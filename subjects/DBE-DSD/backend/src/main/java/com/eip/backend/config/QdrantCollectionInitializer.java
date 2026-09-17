package com.eip.backend.config;

import com.eip.backend.service.QdrantService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Makes sure the vector collection exists at startup, so a fresh deployment can
 * store uploads without a separate setup script. Existing collections are not
 * touched.
 *
 * <p>A Qdrant outage at startup is logged, not fatal: keyword search and document
 * reads work without Qdrant, and the collection is checked again on the next
 * start.
 */
@Component
public class QdrantCollectionInitializer implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(QdrantCollectionInitializer.class);

    private final QdrantService qdrantService;

    @Value("${qdrant.initialize-collection:true}")
    private boolean enabled;

    public QdrantCollectionInitializer(QdrantService qdrantService) {
        this.qdrantService = qdrantService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            return;
        }
        try {
            if (qdrantService.ensureCollection()) {
                logger.info("Created Qdrant collection {}", qdrantService.getCollectionName());
            }
        } catch (RuntimeException e) {
            logger.warn("Could not check Qdrant collection {} at startup: {}",
                        qdrantService.getCollectionName(), e.getMessage());
        }
    }
}
