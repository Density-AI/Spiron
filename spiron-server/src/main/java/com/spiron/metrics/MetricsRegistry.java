package com.spiron.metrics;

import com.sun.net.httpserver.HttpServer;
import io.micrometer.core.instrument.*;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import java.io.IOException;
import java.net.InetSocketAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Central Micrometer registry with Prometheus scrape endpoint.
 */
public class MetricsRegistry {

  private static final Logger log = LoggerFactory.getLogger(
    MetricsRegistry.class
  );
  private final PrometheusMeterRegistry registry;
  private HttpServer server;

  public MetricsRegistry() {
    this.registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
  }

  public MeterRegistry registry() {
    return registry;
  }

  public void startHttp(int port) {
    try {
      server = HttpServer.create(new InetSocketAddress(port), 0);
      server.createContext("/metrics", exchange -> {
        var response = registry.scrape();
        exchange.sendResponseHeaders(200, response.getBytes().length);
        try (var os = exchange.getResponseBody()) {
          os.write(response.getBytes());
        }
      });
      server.start();
      log.info(
        "Prometheus endpoint started at http://localhost:{}/metrics",
        port
      );
    } catch (IOException e) {
      log.error("Failed to start metrics endpoint", e);
    }
  }

  public void stop() {
    if (server != null) server.stop(0);
  }
}
