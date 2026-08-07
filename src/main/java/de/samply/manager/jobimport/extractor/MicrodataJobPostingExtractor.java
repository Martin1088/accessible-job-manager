package de.samply.manager.jobimport.extractor;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Tier 3: schema.org/JobPosting as HTML *microdata* (itemscope/itemprop/
 * itemtype) - the older sibling of JSON-LD. Some ATS platforms (e.g. SAP
 * SuccessFactors/Jobs2Web career pages) emit only this, no JSON-LD at all,
 * so this tier only matters once tier 1 has already come up empty.
 *
 * Not ATS-specific: microdata is a W3C/schema.org markup convention, so
 * this applies to any page using it, not just one vendor's career site.
 *
 * Two pitfalls handled here:
 *   1. itemprop lookups must respect nested itemscope boundaries - a naive
 *      descendant search would leak properties from a nested sub-item
 *      (e.g. jobLocation's own itemprops) into the parent JobPosting.
 *   2. the value of an itemprop depends on the tag: <meta content="...">,
 *      <time datetime="...">, or plain element text - no single accessor
 *      works for all of them.
 */
@Component
@Order(3)
public class MicrodataJobPostingExtractor implements FieldExtractor {

    @Override
    public ConfidenceTier tier() {
        return ConfidenceTier.MICRODATA;
    }

    @Override
    public ExtractionResult extract(ExtractionContext ctx) {
        Document doc = ctx.document();
        if (doc == null) {
            return ExtractionResult.empty();
        }
        return doc.select("[itemscope][itemtype*=JobPosting]").stream()
                .findFirst()
                .map(this::toResult)
                .orElseGet(ExtractionResult::empty);
    }

    private ExtractionResult toResult(Element job) {
        var b = ExtractionResult.builder(ConfidenceTier.MICRODATA);

        prop(job, "title").ifPresent(b::title);
        prop(job, "hiringOrganization").ifPresent(b::companyName);
        prop(job, "datePosted").ifPresent(b::postedAt);
        prop(job, "validThrough").ifPresent(b::deadline);
        prop(job, "employmentType").ifPresent(b::employmentType);
        propHtml(job, "description").ifPresent(b::rawDescription);

        Element location = directChild(job, "jobLocation");
        if (location != null) {
            String city = prop(location, "addressLocality").orElse(null);
            String street = prop(location, "streetAddress").orElse(null);
            if (city == null && street != null) {
                city = street;
                street = null;
            }
            b.addLocation(city, street);
        }

        return b.build();
    }


    private Optional<String> prop(Element scope, String name) {
        Element el = directChild(scope, name);
        return el == null ? Optional.empty() : value(el);
    }

    private Optional<String> propHtml(Element scope, String name) {
        Element el = directChild(scope, name);
        if (el == null) {
            return Optional.empty();
        }
        String html = el.html().trim();
        return html.isEmpty() ? Optional.empty() : Optional.of(html);
    }

    private Element directChild(Element scope, String itemprop) {
        for (Element candidate : scope.select("[itemprop=" + itemprop + "]")) {
            if (belongsToScope(candidate, scope)) {
                return candidate;
            }
        }
        return null;
    }

    private boolean belongsToScope(Element candidate, Element scope) {
        Element parent = candidate.parent();
        while (parent != null && parent != scope) {
            if (parent.hasAttr("itemscope")) {
                return false;
            }
            parent = parent.parent();
        }
        return parent == scope;
    }

    private Optional<String> value(Element el) {
        String raw = switch (el.tagName()) {
            case "meta" -> el.attr("content");
            case "time" -> el.hasAttr("datetime") ? el.attr("datetime") : el.text();
            case "a", "link" -> el.hasAttr("href") ? el.attr("href") : el.text();
            case "img" -> el.attr("src");
            default -> el.text();
        };
        String trimmed = raw == null ? "" : raw.trim();
        return trimmed.isEmpty() ? Optional.empty() : Optional.of(trimmed);
    }
}
