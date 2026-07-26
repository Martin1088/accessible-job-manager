package de.samply.manager.config;

import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "storage.provider", havingValue = "azure")
public class AzureStorageConfig {

    @Bean
    BlobContainerClient blobContainerClient(AzureStorageProperties props) {
        return new BlobServiceClientBuilder()
                .connectionString(props.connectionString())
                .buildClient()
                .getBlobContainerClient(props.container());
    }
}
