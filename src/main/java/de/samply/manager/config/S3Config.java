package de.samply.manager.config;

import de.samply.manager.services.storage.SseCustomerKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;
import java.util.Locale;

@Configuration
public class S3Config {

    private static final Logger log = LoggerFactory.getLogger(S3Config.class);

    /**
     * Derived here rather than inside the storage service: the key is computed once at
     * startup instead of per request, the service keeps its all-final constructor injection,
     * and this is the only place that knows the endpoint the warning below needs.
     */
    @Bean
    SseCustomerKey sseCustomerKey(S3Properties props) {
        SseCustomerKey key = SseCustomerKey.of(props.encryption());
        if (key.isPresent() && props.endpoint() != null
                && props.endpoint().toLowerCase(Locale.ROOT).startsWith("http://")) {
            log.warn("storage.s3.encryption.mode=sse-c with a plain-http endpoint ({}): the"
                            + " encryption key is sent unencrypted on every request. Acceptable"
                            + " only for local development - use https in any deployment.",
                    props.endpoint());
        }
        return key;
    }

    @Bean
    public S3Client s3Client(S3Properties props) {
        return S3Client.builder()
                .endpointOverride(URI.create(props.endpoint()))
                .region(Region.of(props.region()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(props.accessKey(), props.secretKey())
                ))
                .forcePathStyle(true)
                .build();
    }

    @Bean
    S3Presigner s3Presigner(S3Properties props) {
        return S3Presigner.builder()
                .endpointOverride(URI.create(props.endpoint()))
                .region(Region.of(props.region()))
                .credentialsProvider(credentials(props))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(true).build())
                .build();
    }

    private static StaticCredentialsProvider credentials(S3Properties props) {
        return StaticCredentialsProvider.create(AwsBasicCredentials.create(props.accessKey(), props.secretKey()));
    }
}
