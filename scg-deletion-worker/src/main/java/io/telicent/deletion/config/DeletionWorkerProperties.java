package io.telicent.deletion.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "deletion-worker")
public record DeletionWorkerProperties(Kafka kafka, String topic) {
    public record Kafka(String bootstrapServers, String configFilePath) {}
}