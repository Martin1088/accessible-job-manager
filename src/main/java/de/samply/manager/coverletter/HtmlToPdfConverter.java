package de.samply.manager.coverletter;

import de.samply.manager.exception.ApiException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentCatalog;
import org.apache.pdfbox.pdmodel.common.PDMetadata;
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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

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
        body.add("generateTaggedPdf", "true");
        body.add("marginTop", "0");
        body.add("marginBottom", "0");
        body.add("marginLeft", "0");
        body.add("marginRight", "0");

        byte[] pdf;
        try {
            pdf = restClient.post()
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

        return withPdfUaIdentification(pdf);
    }

    /**
     * Adds the XMP metadata stream that makes the file claim PDF/UA-1.
     * <p>
     * Chromium's tagged export already gets the structural half right - it writes
     * MarkInfo/Marked, a structure tree, ViewerPreferences/DisplayDocTitle and the
     * catalog /Lang taken from {@code <html lang>} - but it emits no XMP at all, and
     * without it the document fails clause 7.1 (no Metadata key) and clause 5 (no
     * PDF/UA identification). Gotenberg's own {@code metadata} form field cannot close
     * that gap: it passes only a fixed set of DocInfo keys to exiftool and silently
     * drops {@code pdfuaid:part}. So the identification is written here instead, with
     * the title read back out of the DocInfo dictionary Chromium filled from the
     * document {@code <title>}, which keeps a single source for it.
     */
    private byte[] withPdfUaIdentification(byte[] pdf) {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            PDDocumentCatalog catalog = document.getDocumentCatalog();
            PDMetadata metadata = new PDMetadata(document);
            metadata.importXMPMetadata(xmp(document.getDocumentInformation().getTitle())
                    .getBytes(StandardCharsets.UTF_8));
            catalog.setMetadata(metadata);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new ApiException.InternalServerError(message("error.coverLetter.renderFailed"));
        }
    }

    private static String xmp(String title) {
        return """
                <?xpacket begin="\ufeff" id="W5M0MpCehiHzreSzNTczkc9d"?>
                <x:xmpmeta xmlns:x="adobe:ns:meta/">
                  <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
                    <rdf:Description rdf:about="" xmlns:pdfuaid="http://www.aiim.org/pdfua/ns/id/">
                      <pdfuaid:part>1</pdfuaid:part>
                    </rdf:Description>
                    <rdf:Description rdf:about="" xmlns:dc="http://purl.org/dc/elements/1.1/">
                      <dc:title>
                        <rdf:Alt>
                          <rdf:li xml:lang="x-default">%s</rdf:li>
                        </rdf:Alt>
                      </dc:title>
                    </rdf:Description>
                  </rdf:RDF>
                </x:xmpmeta>
                <?xpacket end="w"?>
                """.formatted(escapeXml(title));
    }

    /** The title is a user-supplied subject line, so it cannot go into XML verbatim. */
    private static String escapeXml(String text) {
        return text == null ? "" : text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private String message(String key) {
        return messageSource.getMessage(key, null, Locale.ROOT);
    }
}
