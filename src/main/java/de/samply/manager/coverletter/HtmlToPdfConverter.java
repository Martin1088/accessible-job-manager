package de.samply.manager.coverletter;

import de.samply.manager.exception.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Prints a self-contained HTML document to PDF through Gotenberg's Chromium route.
 * <p>
 * Gotenberg requires the main file to be named {@code index.html}. Its own margin
 * parameters are set to zero and {@code preferCssPageSize} is enabled so the page
 * box comes from the {@code @page} rule in the template - otherwise Chromium's
 * default half-inch margins would silently shift every DIN 5008 measurement.
 */
@Component
public class HtmlToPdfConverter {

    private final RestClient restClient;
    private final String gotenbergUrl;
    private final MessageSource messageSource;

    public HtmlToPdfConverter(@Value("${gotenberg.url}") String gotenbergUrl, MessageSource messageSource) {
        this.gotenbergUrl = gotenbergUrl;
        this.messageSource = messageSource;
        this.restClient = RestClient.create();
    }

    public byte[] toPdf(String html) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("files", new ByteArrayResource(html.getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getFilename() {
                return "index.html";
            }
        });
        body.add("preferCssPageSize", "true");
        body.add("printBackground", "true");
        body.add("marginTop", "0");
        body.add("marginBottom", "0");
        body.add("marginLeft", "0");
        body.add("marginRight", "0");

        try {
            return restClient.post()
                    .uri(gotenbergUrl + "/forms/chromium/convert/html")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve()
                    .body(byte[].class);
        } catch (RestClientResponseException e) {
            throw new ApiException.InternalServerError(message("error.coverLetter.renderFailed"));
        } catch (RestClientException e) {
            throw new ApiException.BadGateway(message("error.coverLetter.serviceUnavailable"));
        }
    }

    private String message(String key) {
        return messageSource.getMessage(key, null, Locale.ROOT);
    }
}
