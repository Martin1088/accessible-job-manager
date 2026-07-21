package de.samply.manager.services;

import de.samply.manager.config.S3Properties;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.endpoints.internal.Value;

import java.io.InputStream;
import java.net.URI;
import java.time.Duration;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "storage.provider", havingValue = "azure")
public class GarageStorageService implements StorageService{
    private final S3Client s3Client;
    p
    private final S3Properties props;

    @Override
    public void put(String key, InputStream data, long size, String contentType) {

    }

    @Override
    public InputStream get(Value.Str key) {
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
