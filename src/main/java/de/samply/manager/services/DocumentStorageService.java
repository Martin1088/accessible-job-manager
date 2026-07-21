package de.samply.manager.services;

import de.samply.manager.config.S3Properties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.InputStream;

@Service
@RequiredArgsConstructor
public class DocumentStorageService {
    private final S3Client s3Client;
    private final S3Properties props;

    public String upload(String key, InputStream data, long contentLength, String contentType) {
        s3Client.putObject(
                PutObjectRequest.builder()
                        .bucket(props.bucket())
                        .key(key)
                        .contentType(contentType)
                        .contentLength(contentLength)
                        .build(),
                RequestBody.fromInputStream(data, contentLength)
        );
        return key;
    }

    public InputStream download(String key) {
        return s3Client.getObject(
                GetObjectRequest.builder()
                        .bucket(props.bucket())
                        .key(key)
                        .build()
        );
    }

    public void delete(String key) {
        s3Client.deleteObject(
                DeleteObjectRequest.builder()
                        .bucket(props.bucket())
                        .key(key)
                        .build()
        );
    }
}
