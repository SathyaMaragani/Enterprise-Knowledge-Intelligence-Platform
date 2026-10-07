package com.eip.backend.config;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The keep-awake request reaches /api/health on the configured public URL. */
class KeepAwakeTest {

    @Test
    void pingsTheHealthEndpointOfTheExternalUrl() throws Exception {
        AtomicInteger hits = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/api/health", exchange -> {
            hits.incrementAndGet();
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.start();
        try {
            // Render's value has no trailing slash, but one must not produce "//api/health".
            new KeepAwake("http://localhost:" + server.getAddress().getPort() + "/").ping();
            assertEquals(1, hits.get());
        } finally {
            server.stop(0);
        }
    }
}
