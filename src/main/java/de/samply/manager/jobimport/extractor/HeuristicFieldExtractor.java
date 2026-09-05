package de.samply.manager.jobimport.extractor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Tier 5 (last resort - no LLM tier exists yet): title, company and location
 * guessed from generic page structure rather than a schema the site actually
 * commits to. Exists for pages that carry no JobPosting-typed JSON-LD/microdata
 * and aren't behind a known ATS - a plain HTML career page, or one whose
 * JSON-LD only says {@code "@type": "Article"} (a page-publishing schema some
 * CMSes default to even for a job posting - see ofd-bw.fv-bwl.de, whose shared
 * Baden-Württemberg state-agency portal template does exactly that, and stuffs
 * {@code articleSection} with "employer | Bewerbungsfrist: date | pay grade"
 * instead of using schema.org/JobPosting at all).
 *
 * Every field here is a guess, never a fact the page declares about itself the
 * way schema.org/JobPosting or an ATS's own API does - hence HEURISTIC, the
 * lowest tier above LLM, and why the UI marks these fields "please review"
 * (see {@code ConfidenceMergedPosting}'s provenance) rather than showing them
 * as settled. Never overrides a stronger tier: {@code mergeLowerPriority} only
 * fills fields still absent by the time this runs last in the chain.
 */
@Component
@Order(5)
public class HeuristicFieldExtractor implements FieldExtractor {

    /**
     * The German job-posting convention of a bare location label, seen across
     * many employers' postings independent of any one CMS - not specific to
     * the Baden-Württemberg template above. Bounded to 60 chars so a label
     * with no nearby punctuation can't swallow a paragraph.
     */
    private static final Pattern LOCATION_LABEL = Pattern.compile(
            "(?:Standort|Einsatzort|Dienstort|Arbeitsort)\\s*:\\s*([^,;\\n]{1,60})",
            Pattern.CASE_INSENSITIVE);

    /** "Job Title - Company" / "Job Title | Company", the common <title> tag shape. */
    private static final Pattern TITLE_SEPARATOR = Pattern.compile("\\s+[-|\u2013]\\s+");

    private final ObjectMapper mapper;

    public HeuristicFieldExtractor(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public ConfidenceTier tier() {
        return ConfidenceTier.HEURISTIC;
    }

    @Override
    public ExtractionResult extract(ExtractionContext ctx) {
        Document doc = ctx.document();
        if (doc == null) {
            return ExtractionResult.empty();
        }

        Optional<JsonNode> ld = anyJsonLd(doc);
        var b = ExtractionResult.builder(ConfidenceTier.HEURISTIC);

        title(doc, ld).ifPresent(b::title);
        companyName(doc, ld).ifPresent(b::companyName);
        location(ctx.plainText()).ifPresent(city -> b.addLocation(city, null));

        return b.build();
    }

    private Optional<String> title(Document doc, Optional<JsonNode> ld) {
        Optional<String> h1 = firstNonBlankText(doc, "h1");
        if (h1.isPresent()) {
            return h1;
        }
        Optional<String> headline = ld.flatMap(n -> text(n, "headline").or(() -> text(n, "name")));
        if (headline.isPresent()) {
            return headline;
        }
        return titleTagSegment(doc, true);
    }

    private Optional<String> companyName(Document doc, Optional<JsonNode> ld) {
        // Preferred over publisher/author when present: the fuller name,
        // where publisher/author is often just whatever short form the
        // CMS's own site-wide setting uses (here: "OFD" vs. the section's
        // full "Oberfinanzdirektion Baden-Württemberg").
        Optional<String> fromSection = ld.flatMap(n -> text(n, "articleSection"))
                .map(s -> s.split("\\|", 2)[0].trim())
                .filter(s -> !s.isBlank());
        if (fromSection.isPresent()) {
            return fromSection;
        }

        Optional<String> fromOrg = ld.flatMap(n -> orgName(n.get("publisher")).or(() -> orgName(n.get("author"))));
        if (fromOrg.isPresent()) {
            return fromOrg;
        }

        Optional<String> siteName = doc.select("meta[property=og:site_name]").stream()
                .findFirst()
                .map(e -> e.attr("content"))
                .filter(s -> !s.isBlank());
        if (siteName.isPresent()) {
            return siteName;
        }

        return titleTagSegment(doc, false);
    }

    private Optional<String> location(String plainText) {
        if (plainText == null) {
            return Optional.empty();
        }
        Matcher m = LOCATION_LABEL.matcher(plainText);
        return m.find() ? Optional.of(m.group(1).trim()) : Optional.empty();
    }

    /** The first `<title>` segment is the page's own subject; the last is usually the site/company name. */
    private Optional<String> titleTagSegment(Document doc, boolean first) {
        String raw = doc.title();
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String[] parts = TITLE_SEPARATOR.split(raw);
        if (parts.length < 2) {
            return first ? Optional.of(raw.trim()) : Optional.empty();
        }
        String part = first ? parts[0] : parts[parts.length - 1];
        return part.isBlank() ? Optional.empty() : Optional.of(part.trim());
    }

    private Optional<String> firstNonBlankText(Document doc, String selector) {
        Elements elements = doc.select(selector);
        for (Element el : elements) {
            String text = el.text().trim();
            if (!text.isBlank()) {
                return Optional.of(text);
            }
        }
        return Optional.empty();
    }

    /**
     * Any JSON-LD node on the page, whatever its {@code @type} - unlike
     * {@link JsonLdJobPostingExtractor}, which only reads {@code JobPosting}.
     */
    private Optional<JsonNode> anyJsonLd(Document doc) {
        return doc.select("script[type=application/ld+json]").stream()
                .map(Element::data)
                .flatMap(this::parseRoots)
                .findFirst();
    }

    private Stream<JsonNode> parseRoots(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            return Stream.empty();
        }
        try {
            JsonNode root = mapper.readTree(rawJson);
            return root.isArray() ? Stream.ofNullable(root.get(0)) : Stream.of(root);
        } catch (Exception e) {
            return Stream.empty();
        }
    }

    private Optional<String> orgName(JsonNode org) {
        if (org == null || org.isNull()) return Optional.empty();
        if (org.isTextual()) return Optional.of(org.asText());
        return text(org, "name");
    }

    private Optional<String> text(JsonNode node, String field) {
        if (node == null) return Optional.empty();
        JsonNode v = node.get(field);
        if (v == null || v.isNull() || !v.isValueNode()) return Optional.empty();
        String s = v.asText().trim();
        return s.isEmpty() ? Optional.empty() : Optional.of(s);
    }
}
