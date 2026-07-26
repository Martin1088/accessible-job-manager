package de.samply.manager.services;

import software.amazon.awssdk.services.s3.endpoints.internal.Value;

import java.io.InputStream;
import java.net.URI;
import java.time.Duration;

public interface StorageService {
    String upload(String  key, InputStream data, long contentLength, String contentType);
    InputStream download(String key);
    URI presignedGet(String key, Duration ttl);
    void delete(String key);
}
