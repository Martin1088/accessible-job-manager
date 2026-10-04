package de.ajm.manager.document.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "storage.azure")
public record AzureStorageProperties(String connectionString, String container) {}
