package io.telicent.deletion;

import io.telicent.deletion.config.DeletionWorkerProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
@EnableConfigurationProperties(DeletionWorkerProperties.class)
public class DeletionWorkerApplication {
    public static void main(String[] args) {
        SpringApplication.run(DeletionWorkerApplication.class, args);
    }
}
