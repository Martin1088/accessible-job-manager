package de.samply.manager.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.samply.manager.dto.JobPostingExtraction;
import org.jsoup.Jsoup;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Fetches a job posting from a user-supplied URL and asks a local LLM (via
 * Ollama's /api/chat, structured-output mode) to extract the key fields.
 */
@Service
public class JobPostingParserService {

    private static final String SYSTEM_PROMPT =
            "Extract job posting fields. Only fill fields present in the text. " +
            "Use null for missing. Extract values in the source language. " +
            "Each field is a short value - a name or phrase, never a sentence or paragraph. " +
            "'company' is only the employer's name, not an address, slogan, or description.";

    private static final int MAX_HTML_BYTES = 3_000_000;
    private static final int MAX_TEXT_CHARS = 8_000;
    private static final int MAX_REDIRECTS = 5;
    private static final int MAX_FIELD_LENGTH = 200;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String ollamaUrl;
    private final String ollamaModel;

    public JobPostingParserService(@Value("${job-posting.parser.ollama-url}") String ollamaUrl,
                                   @Value("${job-posting.parser.model}") String ollamaModel,
                                   ObjectMapper objectMapper) {
        this.ollamaUrl = ollamaUrl;
        this.ollamaModel = ollamaModel;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(120));
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    public JobPostingExtraction overview(String rawUrl) {
        URI uri = validate(rawUrl);
        String text = fetchVisibleText(uri);
        return extract(text);
    }

    private URI validate(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "URL must not be empty");
        }

        URI uri;
        try {
            uri = new URI(rawUrl.trim());
        } catch (URISyntaxException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Malformed URL");
        }

        if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "URL must use http or https");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "URL must include a host");
        }

        rejectIfDisallowedHost(uri.getHost());
        return uri;
    }

    private void rejectIfDisallowedHost(String host) {
        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not resolve host");
        }
        for (InetAddress address : addresses) {
            if (address.isLoopbackAddress() || address.isAnyLocalAddress()
                    || address.isLinkLocalAddress() || address.isSiteLocalAddress()
                    || address.isMulticastAddress() || isUniqueLocalIpv6(address)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "URL points to a disallowed network address");
            }
        }
    }

    private boolean isUniqueLocalIpv6(InetAddress address) {
        byte[] bytes = address.getAddress();
        return bytes.length == 16 && (bytes[0] & 0xfe) == 0xfc;
    }

    private String fetchVisibleText(URI uri) {
        URI target = uri;
        for (int redirects = 0; redirects <= MAX_REDIRECTS; redirects++) {
            HttpRequest request = HttpRequest.newBuilder(target)
                    .timeout(Duration.ofSeconds(10))
                    .header("User-Agent", "accessible-job-manager/1.0 (+job posting import)")
                    .header("Accept", "text/html")
                    .GET()
                    .build();

            HttpResponse<InputStream> response;
            try {
                response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            } catch (IOException | InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Could not reach the given URL");
            }

            int status = response.statusCode();
            if (status >= 300 && status < 400) {
                String location = response.headers().firstValue("Location")
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Redirect without Location header"));
                target = validate(target.resolve(location).toString());
                continue;
            }
            if (status < 200 || status >= 300) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "URL responded with status " + status);
            }

            byte[] html = readBounded(response.body());
            String text = Jsoup.parse(new String(html, StandardCharsets.UTF_8), target.toString()).text();
            return text.length() > MAX_TEXT_CHARS ? text.substring(0, MAX_TEXT_CHARS) : text;
        }
        throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Too many redirects");
    }

    private byte[] readBounded(InputStream in) {
        try (in) {
            return in.readNBytes(MAX_HTML_BYTES);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Could not read response from the given URL");
        }
    }

    private JobPostingExtraction extract(String postingText) {
        Map<String, Object> format = Map.of(
                "type", "object",
                "properties", Map.of(
                        "title", Map.of("type", List.of("string", "null")),
                        "company", Map.of("type", List.of("string", "null")),
                        "location", Map.of("type", List.of("string", "null")),
                        "employmentType", Map.of("type", List.of("string", "null"))
                ),
                "required", List.of("title", "company", "location", "employmentType")
        );

        Map<String, Object> body = Map.of(
                "model", ollamaModel,
                "stream", false,
                "options", Map.of("temperature", 0),
                "messages", List.of(
                        Map.of("role", "system", "content", SYSTEM_PROMPT),
                        Map.of("role", "user", "content", postingText)
                ),
                "format", format
        );

        String response;
        try {
            response = restClient.post()
                    .uri(ollamaUrl + "/api/chat")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Job posting extraction service unavailable");
        }

        try {
            JsonNode root = objectMapper.readTree(response);
            String content = root.path("message").path("content").asText();
            JobPostingExtraction raw = objectMapper.readValue(content, JobPostingExtraction.class);
            return new JobPostingExtraction(
                    truncate(raw.title()), truncate(raw.company()),
                    truncate(raw.location()), truncate(raw.employmentType()));
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Could not parse extraction result");
        }
    }

    /**
     * Small local models occasionally spill unrelated page text into a field
     * instead of the short value it's meant to hold. Cap defensively so a
     * runaway field can't silently violate a downstream varchar(255) column.
     */
    private static String truncate(String value) {
        if (value == null || value.isBlank()) return null;
        return value.length() > MAX_FIELD_LENGTH ? value.substring(0, MAX_FIELD_LENGTH) : value;
    }
}
