package de.samply.manager.jobsearch;

import com.fasterxml.jackson.databind.JsonNode;
import de.samply.manager.exception.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriBuilder;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Searches the Adzuna aggregator (https://developer.adzuna.com).
 *
 * <p>The bean exists whether or not credentials are configured; without them
 * {@link #available()} is false and {@link JobSearchService} answers 503 rather
 * than calling out with an empty app id. That keeps the key out of the image and
 * makes "not set" mean "source off" instead of "source broken".
 *
 * <p>Nothing fetched here is stored - see {@link JobSearchResults}.
 */
@Service
public class AdzunaJobSearchSource implements JobSearchSource {

    public static final String ID = "adzuna";
    private static final String ATTRIBUTION = "Jobs by Adzuna";

    private static final Logger log = LoggerFactory.getLogger(AdzunaJobSearchSource.class);

    /** Query parameters that carry the operator's credentials and must never be logged. */
    private static final Set<String> CREDENTIAL_PARAMETERS = Set.of("app_id", "app_key");

    /** An upstream error body is echoed back for diagnosis, but only this much of it. */
    private static final int MAX_LOGGED_ERROR_BODY = 512;

    private final RestClient restClient;
    private final AdzunaProperties properties;
    private final MessageSource messageSource;

    public AdzunaJobSearchSource(RestClient.Builder restClientBuilder,
                                 AdzunaProperties properties,
                                 MessageSource messageSource) {
        this.restClient = restClientBuilder.baseUrl(properties.baseUrl()).build();
        this.properties = properties;
        this.messageSource = messageSource;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public boolean available() {
        return properties.configured();
    }

    @Override
    public String attribution() {
        return ATTRIBUTION;
    }

    @Override
    public String defaultCountry() {
        return properties.country();
    }

    @Override
    public JobSearchResults search(JobSearchQuery query) {
        JsonNode root = get(uriBuilder -> {
            uriBuilder.path("/jobs/{country}/search/{page}")
                    .queryParam("app_id", properties.appId())
                    .queryParam("app_key", properties.appKey())
                    .queryParam("content-type", "application/json")
                    .queryParam("results_per_page", query.resultsPerPage())
                    .queryParam("sort_by", query.sortBy().wireValue());
            addIfPresent(uriBuilder, "what", query.what());
            addIfPresent(uriBuilder, "what_exclude", query.whatExclude());
            addIfPresent(uriBuilder, "where", query.where());
            addIfPresent(uriBuilder, "category", query.category());
            addIfPresent(uriBuilder, "distance", query.distanceKm());
            addIfPresent(uriBuilder, "max_days_old", query.maxDaysOld());
            addIfPresent(uriBuilder, "salary_min", query.salaryMin());
            addIfTrue(uriBuilder, "full_time", query.fullTime());
            addIfTrue(uriBuilder, "permanent", query.permanent());
            return uriBuilder.build(query.country(), query.page());
        });

        List<JobSearchHit> hits = new ArrayList<>();
        for (JsonNode result : root.path("results")) {
            hits.add(toHit(result));
        }

        return new JobSearchResults(
                ID,
                root.path("count").asLong(hits.size()),
                query.page(),
                query.resultsPerPage(),
                List.copyOf(hits),
                ATTRIBUTION);
    }

    @Override
    public List<JobSearchCategory> categories(String country) {
        JsonNode root = get(uriBuilder -> uriBuilder.path("/jobs/{country}/categories")
                .queryParam("app_id", properties.appId())
                .queryParam("app_key", properties.appKey())
                .queryParam("content-type", "application/json")
                .build(country));

        List<JobSearchCategory> categories = new ArrayList<>();
        for (JsonNode result : root.path("results")) {
            String tag = text(result, "tag");
            if (tag != null) {
                categories.add(new JobSearchCategory(tag, text(result, "label")));
            }
        }
        return List.copyOf(categories);
    }

    private JsonNode get(Function<UriBuilder, URI> uri) {
        try {
            JsonNode body = restClient.get()
                    .uri(builder -> {
                        URI built = uri.apply(builder);
                        // Debug, not info: the URL carries the advisor's search terms.
                        log.debug("Calling Adzuna: {}", redact(built));
                        return built;
                    })
                    .retrieve()
                    .body(JsonNode.class);
            if (body == null) {
                throw new ApiException.BadGateway(message("error.jobSearch.unparsable"));
            }
            return body;
        } catch (RestClientResponseException e) {
            log.warn("Adzuna answered {}: {}", e.getStatusCode().value(), truncate(e.getResponseBodyAsString()));
            throw translate(e);
        } catch (RestClientException e) {
            log.warn("Adzuna could not be reached", e);
            throw new ApiException.BadGateway(message("error.jobSearch.unavailable"));
        }
    }

    /**
     * The app id and key travel as query parameters, so a URL that reaches a log
     * must not carry them.
     *
     * <p>The <em>raw</em> query is split into parameters rather than
     * string-replaced as a whole: {@link URI#getQuery()} returns the decoded
     * form, which need not occur in {@link URI#toString()} at all. One search
     * term containing a space or an umlaut is enough for the two to diverge, and
     * a replacement that silently misses would log the key in the clear.
     */
    static String redact(URI uri) {
        String rawQuery = uri.getRawQuery();
        if (rawQuery == null) {
            return uri.toString();
        }
        String redacted = Arrays.stream(rawQuery.split("&"))
                .map(AdzunaJobSearchSource::redactParameter)
                .collect(Collectors.joining("&"));
        return uri.toString().replace(rawQuery, redacted);
    }

    /** Keeps an upstream error body from filling the log with an entire error page. */
    private static String truncate(String body) {
        if (body == null || body.length() <= MAX_LOGGED_ERROR_BODY) {
            return body;
        }
        return body.substring(0, MAX_LOGGED_ERROR_BODY) + "… (truncated)";
    }

    private static String redactParameter(String parameter) {
        int separator = parameter.indexOf('=');
        String name = separator < 0 ? parameter : parameter.substring(0, separator);
        return CREDENTIAL_PARAMETERS.contains(name) ? name + "=***" : parameter;
    }

    /**
     * Adzuna answers 401/403 for a bad key and 410 for an exhausted plan; both
     * are an operator problem here, not a caller problem, so neither status is
     * passed through to the advisor unchanged.
     */
    private ApiException translate(RestClientResponseException e) {
        HttpStatus status = HttpStatus.resolve(e.getStatusCode().value());
        if (status == HttpStatus.UNAUTHORIZED || status == HttpStatus.FORBIDDEN || status == HttpStatus.GONE) {
            return new ApiException.BadGateway(message("error.jobSearch.credentialsRejected"));
        }
        if (status == HttpStatus.TOO_MANY_REQUESTS) {
            return new ApiException.TooManyRequests(message("error.jobSearch.rateLimited"));
        }
        return new ApiException.BadGateway(message("error.jobSearch.upstreamError", e.getStatusCode().value()));
    }

    private JobSearchHit toHit(JsonNode result) {
        JsonNode salaryMin = result.path("salary_min");
        JsonNode salaryMax = result.path("salary_max");
        return new JobSearchHit(
                text(result, "id"),
                text(result, "title"),
                text(result.path("company"), "display_name"),
                text(result.path("location"), "display_name"),
                text(result, "redirect_url"),
                text(result, "description"),
                parseCreated(text(result, "created")),
                salaryMin.isNumber() ? salaryMin.asDouble() : null,
                salaryMax.isNumber() ? salaryMax.asDouble() : null,
                result.path("salary_is_predicted").asBoolean(false)
                        || "1".equals(text(result, "salary_is_predicted")),
                text(result, "contract_type"),
                text(result, "contract_time"),
                text(result.path("category"), "label"),
                ID);
    }

    /** Adzuna dates are ISO, with or without a zone offset. */
    private static Instant parseCreated(String value) {
        if (value == null) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException ignored) {
            try {
                return LocalDateTime.parse(value).toInstant(ZoneOffset.UTC);
            } catch (DateTimeParseException stillNotADate) {
                return null;
            }
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        String text = value.asText().trim();
        return text.isEmpty() ? null : text;
    }

    private static void addIfPresent(UriBuilder builder, String name, Object value) {
        if (value != null) {
            builder.queryParam(name, value);
        }
    }

    private static void addIfTrue(UriBuilder builder, String name, Boolean value) {
        if (Boolean.TRUE.equals(value)) {
            builder.queryParam(name, 1);
        }
    }

    private String message(String key, Object... args) {
        return messageSource.getMessage(key, args, Locale.ROOT);
    }
}
