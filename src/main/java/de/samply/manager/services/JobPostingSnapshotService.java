package de.samply.manager.services;

import de.samply.manager.exception.ApiException;
import de.samply.manager.model.CompanyPosition;
import de.samply.manager.model.Document;
import de.samply.manager.model.DocumentType;
import de.samply.manager.types.Language;
import de.samply.manager.repository.CompanyPositionRepository;
import de.samply.manager.repository.DocumentRepository;
import de.samply.manager.services.storage.StorageService;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Renders a job posting URL to PDF via Gotenberg's Chromium route and stores the
 * result in S3 as a {@link Document} linked to the {@link CompanyPosition} it was
 * taken for, so the posting can be reviewed later even if the original page changes
 * or disappears.
 */
@Service
public class JobPostingSnapshotService {

    private final RestClient restClient;
    private final String gotenbergUrl;
    private final StorageService storageService;
    private final DocumentRepository documentRepository;
    private final DocumentService documentService;
    private final CompanyPositionRepository companyPositionRepository;
    private final MessageSource messageSource;

    public JobPostingSnapshotService(@Value("${gotenberg.url}") String gotenbergUrl,
                                     StorageService storageService,
                                     DocumentRepository documentRepository,
                                     DocumentService documentService,
                                     CompanyPositionRepository companyPositionRepository,
                                     MessageSource messageSource) {
        this.gotenbergUrl = gotenbergUrl;
        this.storageService = storageService;
        this.documentRepository = documentRepository;
        this.documentService = documentService;
        this.companyPositionRepository = companyPositionRepository;
        this.messageSource = messageSource;
        this.restClient = RestClient.create();
    }

    private static final Pattern UPSTREAM_STATUS_PATTERN =
            Pattern.compile("status code[^0-9]*(\\d{3})", Pattern.CASE_INSENSITIVE);

    public byte[] snapshotToPdf(String rawUrl) {
        URI uri = validate(rawUrl);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("url", uri.toString());

        try {
            return restClient.post()
                    .uri(gotenbergUrl + "/forms/chromium/convert/url")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve()
                    .body(byte[].class);
        } catch (RestClientResponseException e) {
            throw new ApiException.BadRequest(describeConversionFailure(e));
        } catch (RestClientException e) {
            throw new ApiException.BadGateway(message("error.snapshot.serviceUnavailable"));
        }
    }

    /**
     * Gotenberg reports a failed page load as "...HTTP status code from the main page: {status}: ..."
     * in its error body (e.g. a job posting that was taken down surfaces as a 404 there).
     * Extracting that status lets us tell the user why their URL failed instead of a generic 502.
     */
    private String describeConversionFailure(RestClientResponseException e) {
        Matcher matcher = UPSTREAM_STATUS_PATTERN.matcher(e.getResponseBodyAsString());
        if (!matcher.find()) {
            return message("error.snapshot.conversionFailed");
        }
        int upstreamStatus = Integer.parseInt(matcher.group(1));
        return switch (upstreamStatus) {
            case 404 -> message("error.snapshot.notFound404");
            case 401, 403 -> message("error.snapshot.deniedAccess", upstreamStatus);
            default -> message("error.snapshot.upstreamError", upstreamStatus);
        };
    }

    private String message(String key, Object... args) {
        return messageSource.getMessage(key, args, Locale.ROOT);
    }

    public List<Document> listForPosition(Long companyPositionId, String userId) {
        findOwnedPosition(companyPositionId, userId);
        return documentRepository.findByCompanyPositionIdAndTypeOrderByCreatedAtDesc(
                companyPositionId, DocumentType.JOB_POSTING_SNAPSHOT);
    }

    public Document save(String rawUrl, Long companyPositionId, String label, Language language, String userId) {
        CompanyPosition position = findOwnedPosition(companyPositionId, userId);

        byte[] pdf = snapshotToPdf(rawUrl);

        String key = userId + "/" + DocumentType.JOB_POSTING_SNAPSHOT.name().toLowerCase()
                + "/" + UUID.randomUUID() + "." + DocumentType.JOB_POSTING_SNAPSHOT.getExtension();
        storageService.upload(key, new ByteArrayInputStream(pdf), pdf.length,
                DocumentType.JOB_POSTING_SNAPSHOT.getAllowedMime());

        Document doc = Document.builder()
                .userId(userId)
                .type(DocumentType.JOB_POSTING_SNAPSHOT)
                .language(language)
                .label(label)
                .filename("job-posting-snapshot.pdf")
                .mimeType(DocumentType.JOB_POSTING_SNAPSHOT.getAllowedMime())
                .storageKey(key)
                .companyPosition(position)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        return documentRepository.save(doc);
    }

    public SnapshotContent download(UUID documentId, String userId) {
        Document doc = documentService.findOwned(documentId, userId, DocumentType.JOB_POSTING_SNAPSHOT);
        try (InputStream in = storageService.download(doc.getStorageKey())) {
            return new SnapshotContent(doc, in.readAllBytes());
        } catch (IOException e) {
            throw new ApiException.InternalServerError(message("error.snapshot.readFailed"));
        }
    }

    public Document update(UUID documentId, String userId, String label, Language language) {
        Document doc = documentService.findOwned(documentId, userId, DocumentType.JOB_POSTING_SNAPSHOT);
        if (label != null) doc.setLabel(label);
        if (language != null) doc.setLanguage(language);
        doc.setUpdatedAt(LocalDateTime.now());
        return documentRepository.save(doc);
    }

    private CompanyPosition findOwnedPosition(Long companyPositionId, String userId) {
        CompanyPosition position = companyPositionRepository.findById(companyPositionId)
                .orElseThrow(() -> new ApiException.NotFound(message("error.snapshot.positionNotFound")));
        if (!position.getCompany().getUserId().equals(userId)) {
            throw new ApiException.Forbidden();
        }
        return position;
    }

    public record SnapshotContent(Document document, byte[] content) {}

    private URI validate(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw new ApiException.BadRequest(message("error.url.empty"));
        }

        URI uri;
        try {
            uri = new URI(rawUrl.trim());
        } catch (URISyntaxException e) {
            throw new ApiException.BadRequest(message("error.url.malformed"));
        }

        if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) {
            throw new ApiException.BadRequest(message("error.url.scheme"));
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new ApiException.BadRequest(message("error.url.host"));
        }

        rejectIfDisallowedHost(uri.getHost());
        return uri;
    }

    private void rejectIfDisallowedHost(String host) {
        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            throw new ApiException.BadRequest(message("error.url.hostUnresolved"));
        }
        for (InetAddress address : addresses) {
            if (address.isLoopbackAddress() || address.isAnyLocalAddress()
                    || address.isLinkLocalAddress() || address.isSiteLocalAddress()
                    || address.isMulticastAddress() || isUniqueLocalIpv6(address)) {
                throw new ApiException.BadRequest(message("error.url.disallowedHost"));
            }
        }
    }

    private boolean isUniqueLocalIpv6(InetAddress address) {
        byte[] bytes = address.getAddress();
        return bytes.length == 16 && (bytes[0] & 0xfe) == 0xfc;
    }
}
