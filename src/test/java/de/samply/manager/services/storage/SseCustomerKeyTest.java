package de.samply.manager.services.storage;

import de.samply.manager.config.S3Properties;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the one rule that is silently wrong if you get it backwards: the MD5 header is the
 * Base64 of the digest of the <em>raw decoded</em> key bytes, not of the Base64 text. Both
 * forms are valid Base64 of a 16-byte digest, so only a fixed vector catches the mix-up -
 * otherwise it surfaces as an opaque 400 from Garage at runtime.
 */
class SseCustomerKeyTest {

    /** Bytes 0x00..0x1f. */
    private static final String KEY_B64 = "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=";
    private static final String KEY_MD5 = "tP/LI3N87DFaSk0aoqYgzg==";

    /** 32 zero bytes. */
    private static final String ZERO_KEY_B64 = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";
    private static final String ZERO_KEY_MD5 = "cLyPS3KoaSFGi/joRB3OUQ==";

    private static SseCustomerKey enabled(String keyB64) {
        return SseCustomerKey.of(new S3Properties.Encryption(S3Properties.Mode.SSE_C, keyB64));
    }

    @Test
    void derivesTheMd5FromTheRawKeyBytes() {
        PutObjectRequest request = enabled(KEY_B64)
                .applyTo(PutObjectRequest.builder()).build();

        assertThat(request.sseCustomerAlgorithm()).isEqualTo("AES256");
        assertThat(request.sseCustomerKey()).isEqualTo(KEY_B64);
        assertThat(request.sseCustomerKeyMD5()).isEqualTo(KEY_MD5);
    }

    @Test
    void derivesTheMd5FromTheRawKeyBytesForASecondVector() {
        GetObjectRequest request = enabled(ZERO_KEY_B64)
                .applyTo(GetObjectRequest.builder()).build();

        assertThat(request.sseCustomerKey()).isEqualTo(ZERO_KEY_B64);
        assertThat(request.sseCustomerKeyMD5()).isEqualTo(ZERO_KEY_MD5);
    }

    @Test
    void appliesTheSameThreeHeadersToGetAndPut() {
        SseCustomerKey key = enabled(KEY_B64);

        PutObjectRequest put = key.applyTo(PutObjectRequest.builder()).build();
        GetObjectRequest get = key.applyTo(GetObjectRequest.builder()).build();

        assertThat(get.sseCustomerAlgorithm()).isEqualTo(put.sseCustomerAlgorithm());
        assertThat(get.sseCustomerKey()).isEqualTo(put.sseCustomerKey());
        assertThat(get.sseCustomerKeyMD5()).isEqualTo(put.sseCustomerKeyMD5());
    }

    @Test
    void aDisabledEncryptionYieldsTheNullObject() {
        assertThat(SseCustomerKey.of(S3Properties.Encryption.disabled()).isPresent()).isFalse();
        assertThat(SseCustomerKey.of(null).isPresent()).isFalse();
        assertThat(SseCustomerKey.none().isPresent()).isFalse();
    }

    @Test
    void theNullObjectReturnsTheBuilderUntouched() {
        PutObjectRequest.Builder put = PutObjectRequest.builder();
        GetObjectRequest.Builder get = GetObjectRequest.builder();

        assertThat(SseCustomerKey.none().applyTo(put)).isSameAs(put);
        assertThat(SseCustomerKey.none().applyTo(get)).isSameAs(get);
        assertThat(put.build().sseCustomerKey()).isNull();
        assertThat(get.build().sseCustomerKey()).isNull();
    }

    @Test
    void toStringNeverContainsTheKey() {
        assertThat(enabled(KEY_B64)).hasToString("SseCustomerKey[keyMd5=" + KEY_MD5 + "]");
        assertThat(enabled(KEY_B64).toString()).doesNotContain(KEY_B64);
        assertThat(SseCustomerKey.none()).hasToString("SseCustomerKey[none]");
    }
}
