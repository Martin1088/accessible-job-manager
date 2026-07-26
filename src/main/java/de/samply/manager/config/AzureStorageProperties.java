package de.samply.manager.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "storage.azure")
public record AzureStorageProperties(String connectionString, String container) {}
