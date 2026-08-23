package de.samply.manager.services.storage;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.models.BlobHttpHeaders;
import com.azure.storage.blob.sas.BlobSasPermission;
import com.azure.storage.blob.sas.BlobServiceSasSignatureValues;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "storage.provider", havingValue = "azure")
public class AzureBlobStorageService implements StorageService{
    private final BlobContainerClient container;

    @Override
    public String upload(String key, InputStream data, long contentLength, String contentType) {
        BlobClient blob = container.getBlobClient(key);
        blob.upload(data, contentLength, true);
        blob.setHttpHeaders(new BlobHttpHeaders().setContentType(contentType));
        return key;

    }

    @Override
    public InputStream download(String key) {
        return container.getBlobClient(key).openInputStream();
    }

    @Override
    public URI presignedGet(String key, Duration ttl) {
        BlobClient blob = container.getBlobClient(key);
        String sas = blob.generateSas(new BlobServiceSasSignatureValues(
                OffsetDateTime.now().plus(ttl),
                new BlobSasPermission().setReadPermission(true)));
        return URI.create(blob.getBlobUrl() + "?" + sas);
    }

    @Override
    public void delete(String key) {
        container.getBlobClient(key).deleteIfExists();
    }
}
