package de.samply.manager.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.Base64;

@ConfigurationProperties(prefix = "storage.s3")
public record S3Properties(
        String endpoint,
        String region,
        String bucket,
        String accessKey,
        String secretKey,
        @DefaultValue Encryption encryption
) {

    /**
     * Belt and braces next to the {@code @DefaultValue}: the binder builds a defaulted
     * {@link Encryption} when no {@code storage.s3.encryption.*} key is present, but a
     * directly constructed instance - a test, or any {@code new S3Properties(...)} - would
     * otherwise carry a null that only surfaces as an NPE inside the storage service.
     */
    public S3Properties {
        encryption = (encryption == null) ? Encryption.disabled() : encryption;
    }

    public enum Mode { NONE, SSE_C }

    /**
     * Server-side encryption with a customer-provided key (SSE-C). Garage stores the object
     * encrypted and keeps no copy of the key, so every read has to present it again.
     *
     * <p>Losing the key loses every document. There is deliberately no key id and no
     * rotation: one key, or none. Rotating means re-uploading every object.
     *
     * <p>Off by default. Switching it on does not convert anything - objects already in the
     * bucket were stored unencrypted and become unreadable, and nothing in the schema records
     * which objects are which.
     */
    public record Encryption(@DefaultValue("none") Mode mode, String key) {

        /** AES-256 is the only algorithm S3 SSE-C defines. */
        private static final int REQUIRED_KEY_BYTES = 32;

        private static final String GENERATE_HINT = "Generate one with `openssl rand -base64 32`.";

        public Encryption {
            mode = (mode == null) ? Mode.NONE : mode;
            key = (key == null || key.isBlank()) ? null : key.trim();
            validate(mode, key);
        }

        public static Encryption disabled() {
            return new Encryption(Mode.NONE, null);
        }

        public boolean enabled() {
            return mode == Mode.SSE_C;
        }

        /** The 32 raw key bytes. Only meaningful when {@link #enabled()}. */
        public byte[] keyBytes() {
            return Base64.getDecoder().decode(key);
        }

        private static void validate(Mode mode, String key) {
            if (mode != Mode.SSE_C) {
                return;
            }
            if (key == null) {
                throw new IllegalStateException(
                        "storage.s3.encryption.mode is sse-c but storage.s3.encryption.key is not"
                                + " set. " + GENERATE_HINT);
            }
            byte[] decoded;
            try {
                decoded = Base64.getDecoder().decode(key);
            } catch (IllegalArgumentException e) {
                throw new IllegalStateException(
                        "storage.s3.encryption.key is not valid Base64. " + GENERATE_HINT);
            }
            if (decoded.length != REQUIRED_KEY_BYTES) {
                throw new IllegalStateException(
                        "storage.s3.encryption.key must decode to exactly " + REQUIRED_KEY_BYTES
                                + " bytes for AES-256, but decoded to " + decoded.length + ". "
                                + GENERATE_HINT);
            }
        }

        /** The generated record toString would print the key into any binding error. */
        @Override
        public String toString() {
            return "Encryption[mode=" + mode + ", key=" + (key == null ? "unset" : "****") + "]";
        }
    }
}
