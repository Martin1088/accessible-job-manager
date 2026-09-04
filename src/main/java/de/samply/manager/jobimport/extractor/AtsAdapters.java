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

// =====================================================================
// Comeet (now Spark Hire Recruit):
//   www.comeet.com/jobs/{slug}/{COMPANY_UID}/{position-slug}/{POSITION_UID}
// The page is a client-rendered shell - its only "text" is the unrendered
// template ("{{position.name}}" ...) - so JSON-LD/microdata/LLM all come
// back empty. The job data is fetched by the page's own JavaScript from
// the public Careers API, which needs the company UID and a public token.
// Neither is in the URL, but both sit in the widget's bootstrap config in
// the served HTML, so this adapter reads them from ctx.document().
// Public: https://www.comeet.co/careers-api/2.0/company/{UID}/positions
//           ?token={TOKEN}&details=true   -> array of positions
// =====================================================================
@Component
@Order(4)
class ComeetAdapter implements AtsAdapter {

    /** company_uid and token as the widget bootstrap writes them, adjacent. */
    private static final Pattern CONFIG = Pattern.compile(
            "\"company_uid\"\\s*:\\s*\"([^\"]+)\"\\s*,\\s*\"token\"\\s*:\\s*\"([^\"]+)\"");
    /** Comeet UIDs look like 8A.002 / 1C.176; the position UID is the last one in the path. */
    private static final Pattern POSITION_UID = Pattern.compile("/([0-9A-Za-z]{2}\\.[0-9A-Za-z]{3})(?=[/?#]|$)");

    private final RestClient http;
    private final ObjectMapper mapper;

    ComeetAdapter(RestClient.Builder builder, ObjectMapper mapper) {
        this.http = builder.build();
        this.mapper = mapper;
    }

    @Override
    public boolean supports(String url) {
        return url.contains("comeet.com/jobs/") || url.contains("comeet.co/jobs/");
    }

    @Override
    public Optional<ExtractionResult> fetch(ExtractionContext ctx) {
        String positionUid = lastGroup(POSITION_UID, ctx.sourceUrl());
        if (positionUid == null || ctx.document() == null) {
            return Optional.empty();
        }
        Matcher config = CONFIG.matcher(ctx.document().html());
        if (!config.find()) {
            return Optional.empty();
        }
        String companyUid = config.group(1);
        String token = config.group(2);

        String body = http.get()
                .uri("https://www.comeet.co/careers-api/2.0/company/{uid}/positions?token={t}&details=true",
                        companyUid, token)
                .header("Accept", "application/json")
                .retrieve()
                .body(String.class);

        JsonNode positions = readTree(body);
        if (positions == null || !positions.isArray()) {
            return Optional.empty();
        }
        JsonNode job = null;
        for (JsonNode p : positions) {
            if (positionUid.equalsIgnoreCase(p.path("uid").asText())) {
                job = p;
                break;
            }
        }
        if (job == null) {
            return Optional.empty();
        }

        JsonNode location = job.path("location");
        String city = firstNonBlank(location.path("city").asText(null), location.path("name").asText(null));
        String street = street(location.path("street_name").asText(null), location.path("street_number").asText(null));

        return Optional.of(ExtractionResult.builder(ConfidenceTier.ADAPTER)
                .title(job.path("name").asText(null))
                .companyName(job.path("company_name").asText(null))
                .employmentType(job.path("employment_type").asText(null))
                .contactEmail(job.path("email").asText(null)) // "{slug}.{uid}@comeetapply.com" - a working apply-by-email address
                .sourceJobId(job.path("uid").asText(null))
                .rawDescription(joinDetails(job.path("details")))
                .addLocation(city, street)
                .build());
    }

    /** Comeet's `details` is a list of {name, value} HTML sections; concatenate their bodies. */
    private String joinDetails(JsonNode details) {
        if (!details.isArray()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (JsonNode section : details) {
            String value = section.path("value").asText("");
            if (!value.isBlank()) {
                if (!sb.isEmpty()) sb.append('\n');
                sb.append(value);
            }
        }
        return sb.isEmpty() ? null : sb.toString();
    }

    private String street(String name, String number) {
        String joined = ((name == null ? "" : name) + " " + (number == null ? "" : number)).trim();
        return joined.isBlank() ? null : joined;
    }

    private String firstNonBlank(String a, String b) {
        return (a != null && !a.isBlank()) ? a : b;
    }

    private String lastGroup(Pattern p, String s) {
        if (s == null) return null;
        Matcher m = p.matcher(s);
        String last = null;
        while (m.find()) {
            last = m.group(1);
        }
        return last;
    }

    private JsonNode readTree(String s) {
        try { return mapper.readTree(s); } catch (Exception e) { return null; }
    }
}
