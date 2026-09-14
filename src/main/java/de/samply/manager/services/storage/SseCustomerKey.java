package de.samply.manager.services.storage;

import de.samply.manager.config.S3Properties;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/**
 * The three SSE-C request headers, derived once from the configured key.
 *
 * <p>S3 requires all three on every request that touches an object's bytes: the algorithm,
 * the Base64 key, and the Base64 MD5 digest of the <em>raw</em> key bytes - not of the Base64
 * text. Hashing the Base64 text instead is the classic SSE-C bug, and it fails only at
 * runtime against a real server, which is why {@code SseCustomerKeyTest} pins the digest
 * against fixed vectors. The AWS SDK does not compute it (there is no SSE-C interceptor in
 * {@code s3-2.25.0.jar}). The MD5 is an integrity check on the header in transit, not a
 * security property, so MD5 is the right and only algorithm here.
 *
 * <p>This is a null object rather than an {@code Optional}: {@link #none()} makes
 * {@code applyTo} return the builder untouched, so the callers in {@link GarageStorageService}
 * carry no conditional and cannot get one path right and the other wrong. The single branch
 * lives in {@link #of} and is evaluated once, at startup.
 *
 * <p>Deliberately not applied to deletes: {@code DeleteObjectRequest} has no SSE-C fields at
 * all, because removing the stored ciphertext never needs the key.
 *
 * <p>The {@code applyTo} overloads are per request type on purpose. Every upload today is a
 * single {@code PutObject} under the 20MB multipart limit, but a later switch to multipart or
 * {@code S3TransferManager} must repeat these headers on {@code CreateMultipartUpload}, on
 * <em>every</em> {@code UploadPart}, and on {@code CompleteMultipartUpload} - missing one
 * fails mid-upload. Adding a request type here forces that thought rather than hiding it
 * behind a generic method.
 */
public final class SseCustomerKey {

    private static final String AES_256 = "AES256";
    private static final SseCustomerKey NONE = new SseCustomerKey(null, null);

    private final String key;
    private final String keyMd5;

    private SseCustomerKey(String key, String keyMd5) {
        this.key = key;
        this.keyMd5 = keyMd5;
    }

    /** The disabled case: objects are stored as uploaded. */
    public static SseCustomerKey none() {
        return NONE;
    }

    public static SseCustomerKey of(S3Properties.Encryption encryption) {
        if (encryption == null || !encryption.enabled()) {
            return NONE;
        }
        byte[] raw = encryption.keyBytes();
        return new SseCustomerKey(Base64.getEncoder().encodeToString(raw), md5Base64(raw));
    }

    public boolean isPresent() {
        return key != null;
    }

    public PutObjectRequest.Builder applyTo(PutObjectRequest.Builder builder) {
        return isPresent()
                ? builder.sseCustomerAlgorithm(AES_256).sseCustomerKey(key).sseCustomerKeyMD5(keyMd5)
                : builder;
    }

    public GetObjectRequest.Builder applyTo(GetObjectRequest.Builder builder) {
        return isPresent()
                ? builder.sseCustomerAlgorithm(AES_256).sseCustomerKey(key).sseCustomerKeyMD5(keyMd5)
                : builder;
    }

    /** The key must never reach a log line; the digest is safe and identifies which key it is. */
    @Override
    public String toString() {
        return isPresent() ? "SseCustomerKey[keyMd5=" + keyMd5 + "]" : "SseCustomerKey[none]";
    }

    private static String md5Base64(byte[] raw) {
        try {
            return Base64.getEncoder().encodeToString(MessageDigest.getInstance("MD5").digest(raw));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Every JRE is required to provide MD5", e);
        }
    }
}
