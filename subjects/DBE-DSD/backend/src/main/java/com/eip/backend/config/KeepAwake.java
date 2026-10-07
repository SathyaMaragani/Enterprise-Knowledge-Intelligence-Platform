package com.eip.backend.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Keeps the free Render instance running.
 *
 * <p>Render stops a free web service after 15 minutes without inbound traffic,
 * and waking it takes about 100 s. GitHub's scheduled workflows proved too
 * irregular to prevent that (hours apart, not minutes), so the service requests
 * its own public health URL every 10 minutes. The request goes out through
 * Render's edge and back in, so Render counts it as inbound traffic.
 *
 * <p>Active only on Render, which sets {@code RENDER_EXTERNAL_URL} for every web
 * service. Running all month uses at most 744 of the 750 free instance hours.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty("RENDER_EXTERNAL_URL")
public class KeepAwake {

    private static final Logger logger = LoggerFactory.getLogger(KeepAwake.class);

    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();
    private final URI health;

    public KeepAwake(@Value("${RENDER_EXTERNAL_URL}") String externalUrl) {
        this.health = URI.create(externalUrl.replaceAll("/+$", "") + "/api/health");
    }

    @Scheduled(initialDelay = 10, fixedDelay = 10, timeUnit = TimeUnit.MINUTES)
    void ping() {
        try {
            HttpRequest request = HttpRequest.newBuilder(health).timeout(Duration.ofSeconds(30)).GET().build();
            int status = client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
            if (status != 200) {
                logger.warn("Keep-awake request to {} answered {}", health, status);
            }
        } catch (IOException e) {
            logger.warn("Keep-awake request to {} failed: {}", health, e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
