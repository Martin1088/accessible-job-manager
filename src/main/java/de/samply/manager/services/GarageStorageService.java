package de.samply.manager.services;

import de.samply.manager.config.S3Properties;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.endpoints.internal.Value;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.io.InputStream;
import java.net.URI;
import java.time.Duration;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "storage.provider", havingValue = "s3", matchIfMissing = true)
public class GarageStorageService implements StorageService{
    private final S3Client s3Client;
    private final S3Presigner presigner;
    private final S3Properties props;

    @Override
    public InputStream download(String key) {
        return s3Client.getObject(
                GetObjectRequest.builder()
                        .bucket(props.bucket())
                        .key(key)
                        .build()
        );
    }

    @Override
    public String upload(String key, InputStream data, long contentLength, String contentType) {
        s3Client.putObject(
                PutObjectRequest.builder()
                        .bucket(props.bucket()).key(key)
                        .contentType(contentType).contentLength(contentLength)
                        .build(),
                RequestBody.fromInputStream(data, contentLength));
        return key;
    }

    @Override
    public URI presignedGet(String key, Duration ttl) {
        return presigner.presignGetObject(
                GetObjectPresignRequest.builder()
                        .signatureDuration(ttl)
                        .getObjectRequest(b -> b.bucket(props.bucket()).key(key))
                        .build()
        ).url().toString().transform(URI::create);
    }

    @Override
    public void delete(String key) {
        s3Client.deleteObject(
                DeleteObjectRequest.builder()
                        .bucket(props.bucket())
                        .key(key)
                        .build()
        );
    }
}
