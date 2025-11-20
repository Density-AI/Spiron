package com.spiron.config;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Tests for configuration profiles and parameter validation.
 */
class SpironConfigProfileTest {

  @Test
  void testProfile_LowLatency() {
    var overrides = Map.of(
      "spiron.profile", "LOW_LATENCY",
      "spiron.node.id", "test-node",
      "spiron.port", "8080",
      "spiron.data.dir", "/tmp/test"
    );
    
    SpironConfig config = SpironConfig.loadWithOverrides(overrides);
    
    assertEquals("LOW_LATENCY", config.profile());
    assertEquals(0.75, config.dampingAlpha(), 0.001);
    assertEquals(0.3, config.siphonFactor(), 0.001);
    assertEquals(0.5, config.angularThreshold(), 0.001);
    assertEquals(0.7, config.commitEnergy(), 0.001);
    assertEquals(50, config.maxIterations());
    assertEquals(0.005, config.convergenceThreshold(), 0.0001);
  }

  @Test
  void testProfile_MinQuorum() {
    var overrides = Map.of(
      "spiron.profile", "MIN_QUORUM",
      "spiron.node.id", "test-node",
      "spiron.port", "8080",
      "spiron.data.dir", "/tmp/test"
    );
    
    SpironConfig config = SpironConfig.loadWithOverrides(overrides);
    
    assertEquals("MIN_QUORUM", config.profile());
    assertEquals(0.85, config.dampingAlpha(), 0.001);
    assertEquals(0.42, config.siphonFactor(), 0.001);
    assertEquals(0.62, config.angularThreshold(), 0.001);
    assertEquals(0.8, config.commitEnergy(), 0.001);
    assertEquals(100, config.maxIterations());
    assertEquals(0.001, config.convergenceThreshold(), 0.0001);
  }

  @Test
  void testProfile_MaxQuorum() {
    var overrides = Map.of(
      "spiron.profile", "MAX_QUORUM",
      "spiron.node.id", "test-node",
      "spiron.port", "8080",
      "spiron.data.dir", "/tmp/test"
    );
    
    SpironConfig config = SpironConfig.loadWithOverrides(overrides);
    
    assertEquals("MAX_QUORUM", config.profile());
    assertEquals(0.88, config.dampingAlpha(), 0.001);
    assertEquals(0.48, config.siphonFactor(), 0.001);
    assertEquals(0.68, config.angularThreshold(), 0.001);
    assertEquals(0.85, config.commitEnergy(), 0.001);
    assertEquals(150, config.maxIterations());
    assertEquals(0.0005, config.convergenceThreshold(), 0.0001);
  }

  @Test
  void testProfile_Balanced() {
    var overrides = Map.of(
      "spiron.profile", "BALANCED",
      "spiron.node.id", "test-node",
      "spiron.port", "8080",
      "spiron.data.dir", "/tmp/test"
    );
    
    SpironConfig config = SpironConfig.loadWithOverrides(overrides);
    
    assertEquals("BALANCED", config.profile());
    assertEquals(0.82, config.dampingAlpha(), 0.001);
    assertEquals(0.38, config.siphonFactor(), 0.001);
    assertEquals(0.58, config.angularThreshold(), 0.001);
    assertEquals(0.75, config.commitEnergy(), 0.001);
    assertEquals(75, config.maxIterations());
    assertEquals(0.002, config.convergenceThreshold(), 0.0001);
  }

  @Test
  void testProfile_WithExplicitOverrides() {
    var overrides = Map.of(
      "spiron.profile", "LOW_LATENCY",
      "spiron.damping.alpha", "0.95",
      "spiron.node.id", "test-node",
      "spiron.port", "8080",
      "spiron.data.dir", "/tmp/test"
    );
    
    SpironConfig config = SpironConfig.loadWithOverrides(overrides);
    
    // Explicit override should take precedence
    assertEquals(0.95, config.dampingAlpha(), 0.001);
    // Other values should come from profile
    assertEquals(0.3, config.siphonFactor(), 0.001);
  }

  @Test
  void testVectorDimensions_Default() {
    var overrides = Map.of(
      "spiron.node.id", "test-node",
      "spiron.port", "8080",
      "spiron.data.dir", "/tmp/test"
    );
    
    SpironConfig config = SpironConfig.loadWithOverrides(overrides);
    
    assertEquals(128, config.vectorDimensions());
  }

  @Test
  void testVectorDimensions_Custom() {
    var overrides = Map.of(
      "spiron.vector.dimensions", "512",
      "spiron.node.id", "test-node",
      "spiron.port", "8080",
      "spiron.data.dir", "/tmp/test"
    );
    
    SpironConfig config = SpironConfig.loadWithOverrides(overrides);
    
    assertEquals(512, config.vectorDimensions());
  }

  @Test
  void testVectorDimensions_Maximum() {
    var overrides = Map.of(
      "spiron.vector.dimensions", "2000",
      "spiron.node.id", "test-node",
      "spiron.port", "8080",
      "spiron.data.dir", "/tmp/test"
    );
    
    SpironConfig config = SpironConfig.loadWithOverrides(overrides);
    
    assertEquals(2000, config.vectorDimensions());
  }

  @Test
  void testVectorDimensions_TooLow_ShouldFail() {
    var overrides = Map.of(
      "spiron.vector.dimensions", "64",
      "spiron.node.id", "test-node",
      "spiron.port", "8080",
      "spiron.data.dir", "/tmp/test"
    );
    
    assertThrows(RuntimeException.class, () -> {
      SpironConfig.loadWithOverrides(overrides);
    });
  }

  @Test
  void testVectorDimensions_TooHigh_ShouldFail() {
    var overrides = Map.of(
      "spiron.vector.dimensions", "10000", // Exceeds max of 8192
      "spiron.node.id", "test-node",
      "spiron.port", "8080",
      "spiron.data.dir", "/tmp/test"
    );
    
    assertThrows(RuntimeException.class, () -> {
      SpironConfig.loadWithOverrides(overrides);
    });
  }

  @Test
  void testClusterMode_Solo() {
    var overrides = Map.of(
      "spiron.cluster.mode", "solo",
      "spiron.cluster.solo-instances", "3",
      "spiron.node.id", "test-node",
      "spiron.port", "8080",
      "spiron.data.dir", "/tmp/test"
    );
    
    SpironConfig config = SpironConfig.loadWithOverrides(overrides);
    
    assertEquals("solo", config.clusterMode());
    assertEquals(3, config.soloInstances());
  }

  @Test
  void testClusterMode_Cluster() {
    var overrides = Map.of(
      "spiron.cluster.mode", "cluster",
      "spiron.cluster.discovery.type", "kubernetes",
      "spiron.node.id", "test-node",
      "spiron.port", "8080",
      "spiron.data.dir", "/tmp/test"
    );
    
    SpironConfig config = SpironConfig.loadWithOverrides(overrides);
    
    assertEquals("cluster", config.clusterMode());
    assertEquals("kubernetes", config.discoveryType());
  }

  @Test
  void testDiscoveryType_DNS() {
    var overrides = Map.of(
      "spiron.cluster.discovery.type", "dns",
      "spiron.node.id", "test-node",
      "spiron.port", "8080",
      "spiron.data.dir", "/tmp/test"
    );
    
    SpironConfig config = SpironConfig.loadWithOverrides(overrides);
    
    assertEquals("dns", config.discoveryType());
  }

  @Test
  void testDiscoveryType_Kubernetes() {
    var overrides = Map.of(
      "spiron.cluster.discovery.type", "kubernetes",
      "spiron.cluster.discovery.service-name", "my-service",
      "spiron.cluster.discovery.namespace", "production",
      "spiron.node.id", "test-node",
      "spiron.port", "8080",
      "spiron.data.dir", "/tmp/test"
    );
    
    SpironConfig config = SpironConfig.loadWithOverrides(overrides);
    
    assertEquals("kubernetes", config.discoveryType());
    assertEquals("my-service", config.k8sServiceName());
    assertEquals("production", config.k8sNamespace());
  }

  @Test
  void testMetricsEnabled() {
    var overrides = Map.of(
      "spiron.metrics.enabled", "true",
      "spiron.node.id", "test-node",
      "spiron.port", "8080",
      "spiron.data.dir", "/tmp/test"
    );
    
    SpironConfig config = SpironConfig.loadWithOverrides(overrides);
    
    assertTrue(config.metricsEnabled());
  }

  @Test
  void testMetricsDisabled() {
    var overrides = Map.of(
      "spiron.metrics.enabled", "false",
      "spiron.node.id", "test-node",
      "spiron.port", "8080",
      "spiron.data.dir", "/tmp/test"
    );
    
    SpironConfig config = SpironConfig.loadWithOverrides(overrides);
    
    assertFalse(config.metricsEnabled());
  }

  @Test
  void testHealthCheckInterval() {
    var overrides = Map.of(
      "spiron.cluster.discovery.health-check-interval-ms", "10000",
      "spiron.node.id", "test-node",
      "spiron.port", "8080",
      "spiron.data.dir", "/tmp/test"
    );
    
    SpironConfig config = SpironConfig.loadWithOverrides(overrides);
    
    assertEquals(10000, config.healthCheckIntervalMs());
  }
}
