package de.samply.manager.jobimport.extractor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Tier 1 (highest priority, before ATS-API/microdata/LLM):
 * reads schema.org/JobPosting from <script type="application/ld+json">.
 *
 * Covers the 80% case broadly (title, company, location, dates), without
 * having to maintain an adapter per ATS. Deliberately yields NO contact
 * person: schema.org/JobPosting has no field that's actually populated for
 * that. Contact comes from the HTML regex tier further down.
 *
 * Three pitfalls handled here:
 *   1. @graph / top-level array  -> iterate flat instead of taking the first object
 *   2. @type as a String OR an array
 *   3. hiringOrganization / jobLocation as an object, string, or array
 */
@Component
@Order(1)
public class JsonLdJobPostingExtractor implements FieldExtractor {

    private static final Logger log = LoggerFactory.getLogger(JsonLdJobPostingExtractor.class);
    private final ObjectMapper mapper;

    public JsonLdJobPostingExtractor(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public ConfidenceTier tier() {
        return ConfidenceTier.JSON_LD;
    }

    @Override
    public ExtractionResult extract(ExtractionContext ctx) {
        Document doc = ctx.document();
        if (doc == null) {
            return ExtractionResult.empty();
        }
        return doc.select("script[type=application/ld+json]").stream()
                .map(Element::data)
                .flatMap(this::parseRoots)
                .flatMap(this::flattenGraph)
                .filter(this::isJobPosting)
                .findFirst()
                .map(this::toResult)
                .orElseGet(ExtractionResult::empty);
    }

    private Stream<JsonNode> parseRoots(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            return Stream.empty();
        }
        try {
            JsonNode root = mapper.readTree(rawJson);
            if (root.isArray()) {
                return streamOf(root);
            }
            return Stream.of(root);
        } catch (Exception ex) {
            log.debug("JSON-LD parse skipped: {}", ex.getMessage());
            return Stream.empty();
        }
    }

    private Stream<JsonNode> flattenGraph(JsonNode node) {
        JsonNode graph = node.get("@graph");
        if (graph != null && graph.isArray()) {
            return streamOf(graph);
        }
        return Stream.of(node);
    }

    private boolean isJobPosting(JsonNode node) {
        JsonNode type = node.get("@type");
        if (type == null) {
            return false;
        }
        if (type.isTextual()) {
            return "JobPosting".equals(type.asText());
        }
        if (type.isArray()) {
            return streamOf(type).anyMatch(t -> "JobPosting".equals(t.asText()));
        }
        return false;
    }

    private ExtractionResult toResult(JsonNode job) {
        var b = ExtractionResult.builder(ConfidenceTier.JSON_LD);
        text(job, "title").ifPresent(b::title);
        orgName(job.get("hiringOrganization")).ifPresent(b::companyName);
        for (Location loc : locations(job.get("jobLocation"))) {
            b.addLocation(loc.city(), loc.street());
        }
        text(job, "datePosted").ifPresent(b::postedAt);
        text(job, "validThrough").ifPresent(b::deadline);
        text(job, "employmentType").ifPresent(b::employmentType);
        identifier(job.get("identifier")).ifPresent(b::sourceJobId);
        text(job, "description").ifPresent(b::rawDescription);

        return b.build();
    }

    private Optional<String> orgName(JsonNode org) {
        if (org == null || org.isNull()) return Optional.empty();
        if (org.isTextual()) return Optional.of(org.asText());
        if (org.isArray() && org.size() > 0) return orgName(org.get(0));
        return text(org, "name");
    }

    private List<Location> locations(JsonNode jobLocation) {
        List<Location> out = new ArrayList<>();
        if (jobLocation == null || jobLocation.isNull()) {
            return out;
        }
        Iterable<JsonNode> nodes = jobLocation.isArray()
                ? jobLocation
                : List.of(jobLocation);
        for (JsonNode loc : nodes) {
            JsonNode addr = loc.get("address");
            if (addr == null) continue;
            if (addr.isArray() && addr.size() > 0) addr = addr.get(0);
            String city = text(addr, "addressLocality").orElse(null);
            String street = text(addr, "streetAddress").orElse(null);
            if (city != null || street != null) {
                out.add(new Location(city, street));
            }
        }
        return out;
    }

    private Optional<String> identifier(JsonNode id) {
        if (id == null || id.isNull()) return Optional.empty();
        if (id.isTextual()) return Optional.of(id.asText());
        return text(id, "value");
    }

    private Optional<String> text(JsonNode node, String field) {
        if (node == null) return Optional.empty();
        JsonNode v = node.get(field);
        if (v == null || v.isNull() || !v.isValueNode()) return Optional.empty();
        String s = v.asText().trim();
        return s.isEmpty() ? Optional.empty() : Optional.of(s);
    }

    private static Stream<JsonNode> streamOf(JsonNode array) {
        Spliterator<JsonNode> sp = Spliterators.spliteratorUnknownSize(
                array.elements(), Spliterator.ORDERED);
        return StreamSupport.stream(sp, false);
    }

    private record Location(String city, String street) {}
}
