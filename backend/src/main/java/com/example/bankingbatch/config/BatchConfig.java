package com.example.bankingbatch.config;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.context.annotation.Configuration;

// Minimal Batch configuration holder. Spring Boot's auto-configuration
// will provide the necessary Batch infrastructure for this app; keep
// a reference to the application's EntityManagerFactory so it can be
// injected if needed elsewhere.
@Configuration
public class BatchConfig {

    private final EntityManagerFactory entityManagerFactory;

    public BatchConfig(EntityManagerFactory entityManagerFactory) {
        this.entityManagerFactory = entityManagerFactory;
    }

    public EntityManagerFactory entityManagerFactory() {
        return this.entityManagerFactory;
    }
}


