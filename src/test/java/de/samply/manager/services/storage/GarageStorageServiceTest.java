package de.samply.manager.services.storage;

import de.samply.manager.config.S3Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The SSE-C headers are invisible from every caller's side - {@code StorageService} is
 * unchanged - so this is the only place the wiring is observable without a live Garage.
 */
@ExtendWith(MockitoExtension.class)
class GarageStorageServiceTest {

    /** Bytes 0x00..0x1f, with the Base64 MD5 of those raw bytes. */
    private static final String KEY_B64 = "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=";
    private static final String KEY_MD5 = "tP/LI3N87DFaSk0aoqYgzg==";

    private static final byte[] BODY = "a stored document".getBytes(StandardCharsets.UTF_8);

    @Mock
    private S3Client s3Client;

    @Mock
    private S3Presigner presigner;

    private GarageStorageService service(S3Properties.Encryption encryption) {
        S3Properties props = new S3Properties(
                "http://localhost:3900", "garage", "test-bucket", "k", "s", encryption);
        return new GarageStorageService(
                s3Client, presigner, props, SseCustomerKey.of(props.encryption()));
    }

    private GarageStorageService encrypting() {
        return service(new S3Properties.Encryption(S3Properties.Mode.SSE_C, KEY_B64));
    }

    private GarageStorageService plain() {
        return service(S3Properties.Encryption.disabled());
    }

    private PutObjectRequest capturePut() {
        ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(captor.capture(), any(RequestBody.class));
        return captor.getValue();
    }

    private GetObjectRequest captureGet() {
        ArgumentCaptor<GetObjectRequest> captor = ArgumentCaptor.forClass(GetObjectRequest.class);
        verify(s3Client).getObject(captor.capture());
        return captor.getValue();
    }

    private void stubGetObject() {
        // Match on the typed overload: getObject is also overloaded with a Consumer<Builder>.
        when(s3Client.getObject(any(GetObjectRequest.class))).thenReturn(new ResponseInputStream<>(
                GetObjectResponse.builder().build(), new ByteArrayInputStream(BODY)));
    }

    @Test
    void uploadSendsTheSseCustomerHeadersWhenEncryptionIsOn() {
        encrypting().upload("u1/cv/f.pdf", new ByteArrayInputStream(BODY), BODY.length,
                "application/pdf");

        PutObjectRequest request = capturePut();
        assertThat(request.sseCustomerAlgorithm()).isEqualTo("AES256");
        assertThat(request.sseCustomerKey()).isEqualTo(KEY_B64);
        assertThat(request.sseCustomerKeyMD5()).isEqualTo(KEY_MD5);
        assertThat(request.bucket()).isEqualTo("test-bucket");
        assertThat(request.key()).isEqualTo("u1/cv/f.pdf");
    }

    @Test
    void uploadSendsNoSseCustomerHeadersWhenEncryptionIsOff() {
        plain().upload("u1/cv/f.pdf", new ByteArrayInputStream(BODY), BODY.length,
                "application/pdf");

        PutObjectRequest request = capturePut();
        assertThat(request.sseCustomerAlgorithm()).isNull();
        assertThat(request.sseCustomerKey()).isNull();
        assertThat(request.sseCustomerKeyMD5()).isNull();
    }

    @Test
    void downloadSendsTheSseCustomerHeadersWhenEncryptionIsOn() throws Exception {
        stubGetObject();

        assertThat(encrypting().download("u1/cv/f.pdf").readAllBytes()).isEqualTo(BODY);

        GetObjectRequest request = captureGet();
        assertThat(request.sseCustomerAlgorithm()).isEqualTo("AES256");
        assertThat(request.sseCustomerKey()).isEqualTo(KEY_B64);
        assertThat(request.sseCustomerKeyMD5()).isEqualTo(KEY_MD5);
    }

    @Test
    void downloadSendsNoSseCustomerHeadersWhenEncryptionIsOff() {
        stubGetObject();

        plain().download("u1/cv/f.pdf");

        GetObjectRequest request = captureGet();
        assertThat(request.sseCustomerAlgorithm()).isNull();
        assertThat(request.sseCustomerKey()).isNull();
        assertThat(request.sseCustomerKeyMD5()).isNull();
    }

    /**
     * SSE-C encrypts server-side, so the object is stored and reported at the plaintext size.
     * Callers pass {@code file.getSize()} and {@code pdf.length} straight through; a transform
     * that changed the byte count would break every PUT.
     */
    @Test
    void uploadKeepsTheContentLengthContractUnderEncryption() {
        encrypting().upload("u1/cv/f.pdf", new ByteArrayInputStream(BODY), BODY.length,
                "application/pdf");

        PutObjectRequest request = capturePut();
        assertThat(request.contentLength()).isEqualTo(BODY.length);
        assertThat(request.contentType()).isEqualTo("application/pdf");
    }

    /** DeleteObjectRequest has no SSE-C fields: removing ciphertext never needs the key. */
    @Test
    void deleteIssuesAPlainRequestEvenWhenEncryptionIsOn() {
        encrypting().delete("u1/cv/f.pdf");

        ArgumentCaptor<DeleteObjectRequest> captor =
                ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client).deleteObject(captor.capture());
        assertThat(captor.getValue().bucket()).isEqualTo("test-bucket");
        assertThat(captor.getValue().key()).isEqualTo("u1/cv/f.pdf");
    }

    /**
     * A browser following a presigned URL cannot send the SSE-C headers, so the URL would
     * always 400. Failing here beats handing one out.
     */
    @Test
    void presignedGetRefusesToHandOutAUrlThatCannotCarryTheKey() {
        assertThatThrownBy(() -> encrypting().presignedGet("u1/cv/f.pdf", Duration.ofMinutes(5)))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("SSE-C");
    }
}
