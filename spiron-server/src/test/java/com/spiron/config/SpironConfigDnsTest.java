package com.spiron.config;

import static org.junit.jupiter.api.Assertions.*;

import com.spiron.discovery.Discovery;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

public class SpironConfigDnsTest {

  @AfterEach
  void cleanup() {
    Discovery.reset();
  }

  @Test
  void dnsExpansionUsesResolver() throws Exception {
    // Arrange: provide a resolver that expands 'svc' to two IPs
    Discovery.setResolver((host, port) ->
      java.util.List.of("10.0.0.1:" + port, "10.0.0.2:" + port)
    );

    Path tmpDir = Files.createTempDirectory("spiron-test");
    Map<String, String> overrides = Map.of(
      "spiron.node.id",
      "node-1",
      "spiron.port",
      "9001",
      "spiron.cluster.peers",
      "dns:svc:9001",
      "spiron.damping.alpha",
      "0.98",
      "spiron.siphon.factor",
      "0.2",
      "spiron.angular.threshold",
      "0.6",
      "spiron.commit.energy",
      "2.5",
      "spiron.data.dir",
      tmpDir.toString()
    );

    // Act
    SpironConfig cfg = SpironConfig.loadWithOverrides(overrides);

    // Assert: peers should be expanded to two resolved addresses
    assertTrue(cfg.peers().contains("10.0.0.1:9001"));
    assertTrue(cfg.peers().contains("10.0.0.2:9001"));
  }
}
