package de.samply.manager.services;

import de.samply.manager.model.CompanyPosition;
import de.samply.manager.model.Document;
import de.samply.manager.model.DocumentType;
import de.samply.manager.types.Language;
import de.samply.manager.repository.CompanyPositionRepository;
import de.samply.manager.repository.DocumentRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.time.LocalDateTime;
import java.util.UUID;

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
    private final CompanyPositionRepository companyPositionRepository;

    public JobPostingSnapshotService(@Value("${gotenberg.url}") String gotenbergUrl,
                                     StorageService storageService,
                                     DocumentRepository documentRepository,
                                     CompanyPositionRepository companyPositionRepository) {
        this.gotenbergUrl = gotenbergUrl;
        this.storageService = storageService;
        this.documentRepository = documentRepository;
        this.companyPositionRepository = companyPositionRepository;
        this.restClient = RestClient.create();
    }

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
        } catch (RestClientException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Job posting snapshot service unavailable");
        }
    }

    public Document save(String rawUrl, Long companyPositionId, String label, Language language, String userId) {
        CompanyPosition position = companyPositionRepository.findById(companyPositionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Position not found"));
        if (!position.getCompany().getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

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
        Document doc = findOwned(documentId, userId);
        try (InputStream in = storageService.download(doc.getStorageKey())) {
            return new SnapshotContent(doc, in.readAllBytes());
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not read stored snapshot");
        }
    }

    public Document update(UUID documentId, String userId, String label, Language language) {
        Document doc = findOwned(documentId, userId);
        if (label != null) doc.setLabel(label);
        if (language != null) doc.setLanguage(language);
        doc.setUpdatedAt(LocalDateTime.now());
        return documentRepository.save(doc);
    }

    private Document findOwned(UUID documentId, String userId) {
        Document doc = documentRepository.findById(documentId)
                .filter(d -> d.getType() == DocumentType.JOB_POSTING_SNAPSHOT)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!doc.getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        return doc;
    }

    public record SnapshotContent(Document document, byte[] content) {}

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
}
