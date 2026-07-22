package de.samply.manager.services;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.endpoints.internal.Value;

import java.io.InputStream;
import java.net.URI;
import java.time.Duration;

@Service
@ConditionalOnProperty(name = "storage.provider", havingValue = "garage", matchIfMissing = true)
public class AzureBlobStorageService implements StorageService{
    @Override
    public String upload(String key, InputStream data, long contentLength, String contentType) {
        return "";
    }

    @Override
    public InputStream download(String key) {
        return null;
    }

    @Override
    public URI presignedGet(String key, Duration ttl) {
        return null;
    }

    @Override
    public void delete(String key) {

    }
}
