package de.ajm.manager.legal;

import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Reads the documents from an object-storage bucket.
 *
 * <p>Plain {@code GetObject}, without the SSE-C headers {@code SseCustomerKey} adds: this
 * bucket is not the documents bucket, and a legal notice is public by definition.
 * Only direct children of the prefix count, so a nested folder cannot shadow a document.
 */
final class S3Source implements LegalDocumentSource {

    private final S3Client s3;
    private final String bucket;
    private final String prefix;

    S3Source(S3Client s3, String bucket, String prefix) {
        this.s3 = s3;
        this.bucket = bucket;
        this.prefix = prefix;
    }

    @Override
    public String property() {
        return "job-manager.legal.s3-bucket";
    }

    @Override
    public String location() {
        return bucket + "/" + prefix;
    }

    @Override
    public List<Entry> list() throws IOException {
        List<Entry> entries = new ArrayList<>();
        for (S3Object object : objects()) {
            String key = object.key();
            entries.add(new Entry(key.substring(prefix.length()), () -> new ByteArrayInputStream(
                    s3.getObject(GetObjectRequest.builder().bucket(bucket).key(key).build(),
                            ResponseTransformer.toBytes()).asByteArray())));
        }
        return entries;
    }

    /** One listing, no downloads: key, ETag and size change with any edit. */
    @Override
    public String fingerprint() {
        try {
            StringBuilder out = new StringBuilder();
            for (S3Object object : objects()) {
                out.append(object.key()).append(':').append(object.eTag()).append(':')
                        .append(object.size()).append('\n');
            }
            return out.toString();
        } catch (RuntimeException e) {
            return "unreadable";
        }
    }

    private List<S3Object> objects() {
        List<S3Object> found = new ArrayList<>();
        String token = null;
        do {
            ListObjectsV2Response page = s3.listObjectsV2(ListObjectsV2Request.builder()
                    .bucket(bucket).prefix(prefix).delimiter("/").continuationToken(token).build());
            for (S3Object object : page.contents()) {
                if (!object.key().equals(prefix)) {
                    found.add(object);
                }
            }
            token = Boolean.TRUE.equals(page.isTruncated()) ? page.nextContinuationToken() : null;
        } while (token != null);
        found.sort(Comparator.comparing(S3Object::key));
        return found;
    }
}
