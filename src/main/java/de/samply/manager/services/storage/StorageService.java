package de.samply.manager.services.storage;

import java.io.InputStream;
import java.net.URI;
import java.time.Duration;

public interface StorageService {
    String upload(String  key, InputStream data, long contentLength, String contentType);
    InputStream download(String key);
    URI presignedGet(String key, Duration ttl);
    void delete(String key);
}
