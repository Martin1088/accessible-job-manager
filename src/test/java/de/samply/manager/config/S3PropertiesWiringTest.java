package de.samply.manager.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves Spring Boot's binder runs the compact constructor during startup, so a bad
 * {@code storage.s3.encryption} value is fatal to boot rather than failing the first upload -
 * the same contract {@code SecurityRolesPropertiesWiringTest} pins for the role mapping.
 */
class S3PropertiesWiringTest {

    /** Bytes 0x00..0x1f. */
    private static final String KEY_B64 = "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=";

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(S3Properties.class)
    static class S3Configuration {}

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(S3Configuration.class)
            .withPropertyValues(
                    "storage.s3.endpoint=http://localhost:3900",
                    "storage.s3.region=garage",
                    "storage.s3.bucket=test-bucket",
                    "storage.s3.access-key=k",
                    "storage.s3.secret-key=s");

    @Test
    void anAbsentEncryptionBlockBindsAsDisabled() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            S3Properties.Encryption encryption =
                    context.getBean(S3Properties.class).encryption();
            assertThat(encryption).isNotNull();
            assertThat(encryption.mode()).isEqualTo(S3Properties.Mode.NONE);
            assertThat(encryption.enabled()).isFalse();
        });
    }

    @Test
    void theEmptyPlaceholderDefaultsBindAsDisabled() {
        // What ${S3_SSE_C_MODE:none} / ${S3_SSE_C_KEY:} resolve to with no env set.
        runner.withPropertyValues(
                "storage.s3.encryption.mode=none",
                "storage.s3.encryption.key="
        ).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(S3Properties.class).encryption().enabled()).isFalse();
        });
    }

    @Test
    void aValidKeyEnablesEncryption() {
        runner.withPropertyValues(
                "storage.s3.encryption.mode=sse-c",
                "storage.s3.encryption.key=" + KEY_B64
        ).run(context -> {
            assertThat(context).hasNotFailed();
            S3Properties.Encryption encryption =
                    context.getBean(S3Properties.class).encryption();
            assertThat(encryption.enabled()).isTrue();
            assertThat(encryption.keyBytes()).hasSize(32);
        });
    }

    @Test
    void bothTheYamlAndEnvSpellingsOfTheModeBind() {
        for (String spelling : new String[]{"sse-c", "SSE_C", "sse_c"}) {
            runner.withPropertyValues(
                    "storage.s3.encryption.mode=" + spelling,
                    "storage.s3.encryption.key=" + KEY_B64
            ).run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context.getBean(S3Properties.class).encryption().mode())
                        .isEqualTo(S3Properties.Mode.SSE_C);
            });
        }
    }

    @Test
    void sseCWithoutAKeyFailsStartup() {
        runner.withPropertyValues("storage.s3.encryption.mode=sse-c").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure())
                    .hasRootCauseInstanceOf(IllegalStateException.class)
                    .rootCause().hasMessageContaining("openssl rand -base64 32");
        });
    }

    @Test
    void sseCWithABlankKeyFailsStartup() {
        runner.withPropertyValues(
                "storage.s3.encryption.mode=sse-c",
                "storage.s3.encryption.key=   "
        ).run(context -> assertThat(context).hasFailed());
    }

    @Test
    void sseCWithANonBase64KeyFailsStartup() {
        runner.withPropertyValues(
                "storage.s3.encryption.mode=sse-c",
                "storage.s3.encryption.key=not base64!!"
        ).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure())
                    .hasRootCauseInstanceOf(IllegalStateException.class)
                    .rootCause().hasMessageContaining("Base64");
        });
    }

    @Test
    void sseCWithAShortKeyFailsStartup() {
        runner.withPropertyValues(
                "storage.s3.encryption.mode=sse-c",
                "storage.s3.encryption.key=AAECAwQFBgcICQoLDA0ODw=="   // 16 bytes
        ).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure())
                    .hasRootCauseInstanceOf(IllegalStateException.class)
                    .rootCause().hasMessageContaining("32 bytes");
        });
    }

    @Test
    void theKeyIsNotPrintedInToString() {
        assertThat(new S3Properties.Encryption(S3Properties.Mode.SSE_C, KEY_B64).toString())
                .doesNotContain(KEY_B64)
                .contains("****");
    }
}
