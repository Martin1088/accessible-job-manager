package de.samply.manager.jobimport.extractor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One adapter per ATS. supports(url) decides routing purely by
 * host/query pattern; fetch() calls the respective public API and maps it
 * onto ExtractionResult with ADAPTER confidence.
 *
 * Interface deliberately kept narrow (pragmatic over theoretically optimal) -
 * indirection only because there are genuinely multiple implementations here.
 */
interface AtsAdapter {
    boolean supports(String url);
    Optional<ExtractionResult> fetch(ExtractionContext ctx);
}

// =====================================================================
// Ashby:  ...?ashby_jid={uuid}  OR  jobs.ashbyhq.com/{board}/{uuid}
// Public:  https://api.ashbyhq.com/posting-api/job-board/{board}
// =====================================================================
@Component
@Order(1)
class AshbyAdapter implements AtsAdapter {

    private static final Pattern JID   = Pattern.compile("ashby_jid=([0-9a-fA-F-]{36})");
    private static final Pattern BOARD = Pattern.compile("ashbyhq\\.com/([^/?#]+)");

    private final RestClient http;
    private final ObjectMapper mapper;

    AshbyAdapter(RestClient.Builder builder, ObjectMapper mapper) {
        this.http = builder.build();
        this.mapper = mapper;
    }

    @Override
    public boolean supports(String url) {
        return url.contains("ashby_jid=") || url.contains("ashbyhq.com");
    }

    @Override
    public Optional<ExtractionResult> fetch(ExtractionContext ctx) {
        String url = ctx.sourceUrl();
        String board = firstGroup(BOARD, url).orElse(ctx.boardHint());
        Optional<String> jid = firstGroup(JID, url);
        if (board == null) {
            return Optional.empty();
        }

        String body = http.get()
                .uri("https://api.ashbyhq.com/posting-api/job-board/{b}", board)
                .retrieve()
                .body(String.class);

        JsonNode root = readTree(body);
        if (root == null) return Optional.empty();

        JsonNode jobs = root.path("jobs");
        JsonNode job = jid.isPresent()
                ? findById(jobs, jid.get())
                : (jobs.isArray() && jobs.size() > 0 ? jobs.get(0) : null);
        if (job == null) return Optional.empty();

        var b = ExtractionResult.builder(ConfidenceTier.ADAPTER)
                .title(job.path("title").asText(null))
                .companyName(root.path("name").asText(null))
                .sourceJobId(job.path("id").asText(null))
                .rawDescription(job.path("descriptionHtml").asText(null));

        String location = job.path("location").asText(null);
        if (location != null && !location.isBlank()) {
            b.addLocation(location, null); // Ashby: a single location string, no street
        }
        return Optional.of(b.build());
    }

    private JsonNode findById(JsonNode jobs, String id) {
        if (!jobs.isArray()) return null;
        for (JsonNode j : jobs) {
            if (id.equals(j.path("id").asText())) return j;
        }
        return null;
    }

    private Optional<String> firstGroup(Pattern p, String s) {
        Matcher m = p.matcher(s);
        return m.find() ? Optional.of(m.group(1)) : Optional.empty();
    }

    private JsonNode readTree(String s) {
        try { return mapper.readTree(s); } catch (Exception e) { return null; }
    }
}

// =====================================================================
// Personio:  {subdomain}.jobs.personio.{de|com}/job/{id}
// Public:    https://{subdomain}.jobs.personio.com/xml   (XML feed)
//   -> 'office' is a clean location field, better than JSON-LD
// =====================================================================
@Component
@Order(2)
class PersonioAdapter implements AtsAdapter {

    private static final Pattern SUB = Pattern.compile("https?://([^.]+)\\.jobs\\.personio\\.");
    private static final Pattern JOB = Pattern.compile("/job/(\\d+)");

    private final RestClient http;
    private final PersonioXmlParser xml; // dedicated XML feed parser

    PersonioAdapter(RestClient.Builder builder, PersonioXmlParser xml) {
        this.http = builder.build();
        this.xml = xml;
    }

    @Override
    public boolean supports(String url) {
        return url.contains(".jobs.personio.");
    }

    @Override
    public Optional<ExtractionResult> fetch(ExtractionContext ctx) {
        String url = ctx.sourceUrl();
        Matcher sub = SUB.matcher(url);
        Matcher job = JOB.matcher(url);
        if (!sub.find() || !job.find()) {
            return Optional.empty();
        }
        String subdomain = sub.group(1);
        String jobId = job.group(1);

        String feed = http.get()
                .uri("https://{s}.jobs.personio.com/xml", subdomain)
                .retrieve()
                .body(String.class);

        return xml.findPosting(feed, jobId)
                .map(p -> ExtractionResult.builder(ConfidenceTier.ADAPTER)
                        .title(p.name())
                        .addLocation(p.office(), null) // 'office' = actual location
                        .sourceJobId(jobId)
                        .rawDescription(p.description())
                        .build());
    }
}

// =====================================================================
// Oracle HCM (Cloud Recruiting):
//   .../CandidateExperience/.../sites/{CX}/job/{jobId}
// Public REST:
//   /hcmRestApi/resources/latest/recruitingCEJobRequisitionDetails
//     ?expand=all&finder=ItemsToPersonId;Id="{jobId}"
// Returns ALL requisitionLocation entries, cleanly structured.
// =====================================================================
@Component
@Order(3)
class OracleHcmAdapter implements AtsAdapter {

    private static final Pattern HOST = Pattern.compile("https?://([^/]+)");
    private static final Pattern JOB  = Pattern.compile("/job/(\\d+)");

    private final RestClient http;
    private final ObjectMapper mapper;

    OracleHcmAdapter(RestClient.Builder builder, ObjectMapper mapper) {
        this.http = builder.build();
        this.mapper = mapper;
    }

    @Override
    public boolean supports(String url) {
        return url.contains("CandidateExperience") || url.contains("oraclecloud.com");
    }

    @Override
    public Optional<ExtractionResult> fetch(ExtractionContext ctx) {
        String url = ctx.sourceUrl();
        Matcher host = HOST.matcher(url);
        Matcher job = JOB.matcher(url);
        if (!host.find() || !job.find()) {
            return Optional.empty();
        }
        String base = host.group(1);
        String jobId = job.group(1);

        String body = http.get()
                .uri(URI.create("https://" + base
                        + "/hcmRestApi/resources/latest/recruitingCEJobRequisitionDetails"
                        + "?expand=all&onlyData=true"
                        + "&finder=ItemsToPersonId%3BId%3D%22" + jobId + "%22"))
                .retrieve()
                .body(String.class);

        JsonNode root = readTree(body);
        if (root == null) return Optional.empty();

        JsonNode item = root.path("items").path(0);
        if (item.isMissingNode()) return Optional.empty();

        var b = ExtractionResult.builder(ConfidenceTier.ADAPTER)
                .title(item.path("Title").asText(null))
                .companyName(item.path("CompanyName").asText(null))
                .sourceJobId(jobId)
                .rawDescription(item.path("ExternalDescriptionStr").asText(null));

        JsonNode locs = item.path("requisitionLocation");
        if (locs.isArray()) {
            for (JsonNode l : locs) {
                b.addLocation(l.path("TownOrCity").asText(null), null);
            }
        }
        return Optional.of(b.build());
    }

    private JsonNode readTree(String s) {
        try { return mapper.readTree(s); } catch (Exception e) { return null; }
    }
}
