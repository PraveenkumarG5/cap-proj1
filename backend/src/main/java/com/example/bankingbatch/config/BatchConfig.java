package com.example.bankingbatch.config;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.batch.core.configuration.annotation.DefaultBatchConfiguration;
import org.springframework.context.annotation.Configuration;

// Exposes the shared EntityManagerFactory to Spring Batch infrastructure.
@Configuration
public class BatchConfig extends DefaultBatchConfiguration {

    private final EntityManagerFactory entityManagerFactory;

    public BatchConfig(EntityManagerFactory entityManagerFactory) {
        this.entityManagerFactory = entityManagerFactory;
    }

    @Override
    public EntityManagerFactory getEntityManagerFactory() {
        return this.entityManagerFactory;
    }
}


