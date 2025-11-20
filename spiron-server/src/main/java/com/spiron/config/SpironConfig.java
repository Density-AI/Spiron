package com.spiron.config;

import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.spiron.discovery.Discovery;

/** Loads spiron configuration from application.properties + environment overrides. */
public record SpironConfig(
  String nodeId,
  int port,
  List<String> peers,
  double dampingAlpha,
  double siphonFactor,
  double angularThreshold,
  double commitEnergy,
  String dataDir,
  int metricsPort,
  int rpcWorkerThreads,
  String blsSeed,
  String storageMode, // "solo" (RocksDB) or "cluster" (etcd)
  String etcdEndpoints, // comma-separated etcd URLs if storageMode="cluster"
  // New configuration fields
  String profile, // LOW_LATENCY, MIN_QUORUM, MAX_QUORUM, BALANCED
  int vectorDimensions, // 128-2000
  String clusterMode, // solo, cluster
  int soloInstances, // number of instances for solo mode
  int basePort, // base port for solo mode instances
  String discoveryType, // static, dns, kubernetes
  String k8sServiceName,
  String k8sNamespace,
  int healthCheckIntervalMs,
  boolean metricsEnabled,
  int maxIterations,
  double convergenceThreshold,
  // Broadcast validation config
  double broadcastMinEnergy,
  double broadcastMaxEnergy,
  String broadcastIdPattern,
  long broadcastDuplicateExpiryMs,
  int broadcastRateLimitPerSecond,
  String broadcastPeerAllowlistRegex,
  // Finality detection
  long finalityThreshold
) {
  public static SpironConfig load() {
    try (
      InputStream in =
        SpironConfig.class.getResourceAsStream("/application.properties")
    ) {
      var props = new Properties();
      props.load(in);
      return loadFromProperties(props);
    } catch (Exception e) {
      throw new RuntimeException(
        "Failed to load spiron configuration: " + e.getMessage(),
        e
      );
    }
  }

  public static SpironConfig loadWithOverrides(
    java.util.Map<String, String> overrides
  ) {
    try (
      InputStream in =
        SpironConfig.class.getResourceAsStream("/application.properties")
    ) {
      var props = new Properties();
      props.load(in);
      if (overrides != null) {
        for (var e : overrides.entrySet()) props.setProperty(
          e.getKey(),
          e.getValue()
        );
      }
      return loadFromProperties(props);
    } catch (Exception e) {
      throw new RuntimeException(
        "Failed to load spiron configuration: " + e.getMessage(),
        e
      );
    }
  }

  private static SpironConfig loadFromProperties(Properties props) {
    // Read raw values (allow system properties or environment overrides)
    String nodeId = System.getProperty("spiron.node.id",
      System.getenv().getOrDefault("spiron_NODE_ID", props.getProperty("spiron.node.id")));
    if (nodeId == null || nodeId.isBlank()) {
      throw new RuntimeException(
        "Missing required configuration: 'spiron.node.id' (or env 'spiron_NODE_ID')."
      );
    }

    String portStr = System.getProperty("spiron.port",
      System.getenv().getOrDefault("spiron_PORT", props.getProperty("spiron.port")));
    if (portStr == null || portStr.isBlank()) {
      throw new RuntimeException(
        "Missing required configuration: 'spiron.port'."
      );
    }
    int port = parseIntProp("spiron.port", portStr);
    if (port < 1 || port > 65535) {
      throw new RuntimeException(
        "Invalid 'spiron.port' value: " + port + ". Must be 1-65535."
      );
    }

    String peersRaw = System.getProperty("spiron.cluster.peers",
      props.getProperty("spiron.cluster.peers", ""));
    
    // allow discovery providers to initialize from properties
    Discovery.initFrom(props);

    List<String> peers = peersRaw.isBlank() ? List.of() : 
      Arrays.stream(peersRaw.split(","))
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .flatMap(SpironConfig::expandPeerToken)
        .collect(Collectors.toList());

    double alpha = 0.85; // default
    double siphon = 0.45; // default
    double angular = 0.6; // default
    double commit = 1.0; // default
    
    // Only override defaults if explicitly set
    String alphaStr = props.getProperty("spiron.damping.alpha");
    if (alphaStr != null && !alphaStr.isEmpty()) {
      alpha = parseDoubleProp("spiron.damping.alpha", alphaStr);
    }
    String siphonStr = props.getProperty("spiron.siphon.factor");
    if (siphonStr != null && !siphonStr.isEmpty()) {
      siphon = parseDoubleProp("spiron.siphon.factor", siphonStr);
    }
    String angularStr = props.getProperty("spiron.angular.threshold");
    if (angularStr != null && !angularStr.isEmpty()) {
      angular = parseDoubleProp("spiron.angular.threshold", angularStr);
    }
    String commitStr = props.getProperty("spiron.commit.energy");
    if (commitStr != null && !commitStr.isEmpty()) {
      commit = parseDoubleProp("spiron.commit.energy", commitStr);
    }

    String dataDir = System.getProperty("spiron.data.dir", 
      props.getProperty("spiron.data.dir", "/tmp/spiron"));

    // Optional configs with defaults (support system properties)
    int metricsPort = parseIntProp(
      "spiron.metrics.port",
      System.getProperty("spiron.metrics.port", props.getProperty("spiron.metrics.port", "9090"))
    );
    if (metricsPort < 1 || metricsPort > 65535) {
      throw new RuntimeException(
        "Invalid 'spiron.metrics.port' value: " +
        metricsPort +
        ". Must be 1-65535."
      );
    }
    int rpcWorkerThreads = parseIntProp(
      "spiron.rpc.workerThreads",
      props.getProperty("spiron.rpc.workerThreads", "4")
    );
    if (rpcWorkerThreads < 1) {
      throw new RuntimeException(
        "Invalid 'spiron.rpc.workerThreads' value: " +
        rpcWorkerThreads +
        ". Must be >= 1."
      );
    }
    String blsSeed = props.getProperty("spiron.bls.seed", "");

    String storageMode = System.getProperty("spiron.storage.mode",
      props.getProperty("spiron.storage.mode", "solo"))
      .toLowerCase()
      .trim();
    if (!storageMode.equals("solo") && !storageMode.equals("cluster")) {
      throw new RuntimeException(
        "Invalid 'spiron.storage.mode' value: " +
        storageMode +
        ". Must be 'solo' or 'cluster'."
      );
    }
    String etcdEndpoints = props.getProperty("spiron.etcd.endpoints", "");
    if (
      storageMode.equals("cluster") &&
      (etcdEndpoints == null || etcdEndpoints.isBlank())
    ) {
      throw new RuntimeException(
        "Missing required configuration for cluster mode: 'spiron.etcd.endpoints'."
      );
    }

    // New configuration fields (support system properties)
    String profile = System.getProperty("spiron.profile",
      props.getProperty("spiron.profile", "BALANCED")).toUpperCase();
    int vectorDimensions = parseIntProp("spiron.vector.dimensions", 
      System.getProperty("spiron.vector.dimensions",
        props.getProperty("spiron.vector.dimensions", "128")));
    if (vectorDimensions < 128 || vectorDimensions > 8192) {
      throw new RuntimeException(
        "Invalid 'spiron.vector.dimensions' value: " + vectorDimensions + 
        ". Must be between 128 and 8192."
      );
    }
    
    String clusterMode = props.getProperty("spiron.cluster.mode", "solo").toLowerCase();
    int soloInstances = parseIntProp("spiron.cluster.solo-instances",
      props.getProperty("spiron.cluster.solo-instances", "3"));
    int basePort = parseIntProp("spiron.cluster.base-port",
      props.getProperty("spiron.cluster.base-port", String.valueOf(port)));
    
    String discoveryType = props.getProperty("spiron.cluster.discovery.type", "static");
    String k8sServiceName = props.getProperty("spiron.cluster.discovery.service-name", "spiron-cluster");
    String k8sNamespace = props.getProperty("spiron.cluster.discovery.namespace", "default");
    int healthCheckIntervalMs = parseIntProp("spiron.cluster.discovery.health-check-interval-ms",
      props.getProperty("spiron.cluster.discovery.health-check-interval-ms", "5000"));
    
    boolean metricsEnabled = Boolean.parseBoolean(
      props.getProperty("spiron.metrics.enabled", "true"));
    
    int maxIterations = 100; // default
    String maxIterStr = props.getProperty("spiron.consensus.max-iterations");
    if (maxIterStr != null && !maxIterStr.isEmpty()) {
      maxIterations = parseIntProp("spiron.consensus.max-iterations", maxIterStr);
    }
    
    double convergenceThreshold = 0.001; // default
    String convThresholdStr = props.getProperty("spiron.consensus.convergence-threshold");
    if (convThresholdStr != null && !convThresholdStr.isEmpty()) {
      convergenceThreshold = parseDoubleProp("spiron.consensus.convergence-threshold", convThresholdStr);
    }

    // Broadcast validation configuration
    double broadcastMinEnergy = parseDoubleProp("spiron.broadcast.validation.min-energy",
      props.getProperty("spiron.broadcast.validation.min-energy", "0.0"));
    double broadcastMaxEnergy = parseDoubleProp("spiron.broadcast.validation.max-energy",
      props.getProperty("spiron.broadcast.validation.max-energy", "1000.0"));
    String broadcastIdPattern = props.getProperty("spiron.broadcast.validation.id-pattern", 
      "^[a-zA-Z0-9_-]{1,128}$");
    long broadcastDuplicateExpiryMs = parseLongProp("spiron.broadcast.duplicate.expiry-ms",
      props.getProperty("spiron.broadcast.duplicate.expiry-ms", "60000"));
    int broadcastRateLimitPerSecond = parseIntProp("spiron.broadcast.rate-limit.requests-per-second",
      props.getProperty("spiron.broadcast.rate-limit.requests-per-second", "100"));
    String broadcastPeerAllowlistRegex = props.getProperty("spiron.broadcast.peer.allowlist-regex", "");
    
    long finalityThreshold = parseLongProp("spiron.approval.finality.threshold",
      props.getProperty("spiron.approval.finality.threshold", "3"));

    // Apply profile overrides if not explicitly set
    var configWithProfile = applyProfile(profile, alpha, siphon, angular, commit, 
      maxIterations, convergenceThreshold, props);

    return new SpironConfig(
      nodeId,
      port,
      peers,
      configWithProfile[0], // alpha
      configWithProfile[1], // siphon
      configWithProfile[2], // angular
      configWithProfile[3], // commit
      dataDir,
      metricsPort,
      rpcWorkerThreads,
      blsSeed,
      storageMode,
      etcdEndpoints,
      profile,
      vectorDimensions,
      clusterMode,
      soloInstances,
      basePort,
      discoveryType,
      k8sServiceName,
      k8sNamespace,
      healthCheckIntervalMs,
      metricsEnabled,
      (int)configWithProfile[4], // maxIterations
      configWithProfile[5], // convergenceThreshold
      broadcastMinEnergy,
      broadcastMaxEnergy,
      broadcastIdPattern,
      broadcastDuplicateExpiryMs,
      broadcastRateLimitPerSecond,
      broadcastPeerAllowlistRegex,
      finalityThreshold
    );
  }

  /**
   * Expand a peer token into one or more host:port entries.
   * Supported forms:
   *  - host:port
   *  - dns:hostname[:port]  -> resolves hostname to all A records and returns ip:port entries
   */
  private static Stream<String> expandPeerToken(String token) {
    try {
      if (token.startsWith("dns:")) {
        String body = token.substring(4);
        String host = body;
        int port = -1;
        if (body.contains(":")) {
          var parts = body.split(":", 2);
          host = parts[0];
          port = Integer.parseInt(parts[1]);
        }
        var resolved = Discovery.resolve(host, port);
        return resolved.stream();
      }
    } catch (Exception e) {
      // Fallthrough to default token
    }
    return Stream.of(token);
  }

  private static int parseIntProp(String name, String value) {
    if (value == null) throw new RuntimeException(
      "Missing required configuration: '" + name + "'."
    );
    try {
      return Integer.parseInt(value.trim());
    } catch (NumberFormatException nfe) {
      throw new RuntimeException(
        "Invalid integer for '" + name + "': '" + value + "'."
      );
    }
  }

  private static double parseDoubleProp(String name, String value) {
    if (value == null) throw new RuntimeException(
      "Missing required configuration: '" + name + "'."
    );
    try {
      return Double.parseDouble(value.trim());
    } catch (NumberFormatException nfe) {
      throw new RuntimeException(
        "Invalid number for '" + name + "': '" + value + "'."
      );
    }
  }

  private static long parseLongProp(String name, String value) {
    if (value == null) throw new RuntimeException(
      "Missing required configuration: '" + name + "'."
    );
    try {
      return Long.parseLong(value.trim());
    } catch (NumberFormatException nfe) {
      throw new RuntimeException(
        "Invalid long for '" + name + "': '" + value + "'."
      );
    }
  }

  /**
   * Apply profile presets to configuration parameters.
   * Returns array: [alpha, siphon, angular, commit, maxIterations, convergenceThreshold]
   */
  private static double[] applyProfile(String profile, double alpha, double siphon, 
      double angular, double commit, int maxIter, double convThreshold, Properties props) {
    
    // Check if values were explicitly set in properties (not defaults)
    boolean hasAlpha = props.containsKey("spiron.damping.alpha");
    boolean hasSiphon = props.containsKey("spiron.siphon.factor");
    boolean hasAngular = props.containsKey("spiron.angular.threshold");
    boolean hasCommit = props.containsKey("spiron.commit.energy");
    boolean hasMaxIter = props.containsKey("spiron.consensus.max-iterations");
    boolean hasConvThreshold = props.containsKey("spiron.consensus.convergence-threshold");
    
    // Profile presets - tuned for real-world production workloads
    return switch (profile) {
      case "LOW_LATENCY" -> new double[]{
        hasAlpha ? alpha : 0.75,
        hasSiphon ? siphon : 0.3,
        hasAngular ? angular : 0.5,
        hasCommit ? commit : 0.7,  // Fast commits - high throughput
        hasMaxIter ? maxIter : 50,
        hasConvThreshold ? convThreshold : 0.005
      };
      case "BALANCED" -> new double[]{
        hasAlpha ? alpha : 0.82,
        hasSiphon ? siphon : 0.38,
        hasAngular ? angular : 0.58,
        hasCommit ? commit : 0.75,  // Balanced commits - good for general use
        hasMaxIter ? maxIter : 75,
        hasConvThreshold ? convThreshold : 0.002
      };
      case "MIN_QUORUM" -> new double[]{
        hasAlpha ? alpha : 0.85,
        hasSiphon ? siphon : 0.42,
        hasAngular ? angular : 0.62,
        hasCommit ? commit : 0.8,  // Moderate selectivity
        hasMaxIter ? maxIter : 100,
        hasConvThreshold ? convThreshold : 0.001
      };
      case "MAX_QUORUM" -> new double[]{
        hasAlpha ? alpha : 0.88,
        hasSiphon ? siphon : 0.48,
        hasAngular ? angular : 0.68,
        hasCommit ? commit : 0.85,  // High quality - strong consensus
        hasMaxIter ? maxIter : 150,
        hasConvThreshold ? convThreshold : 0.0005
      };
      default -> new double[]{ // BALANCED
        hasAlpha ? alpha : 0.82,
        hasSiphon ? siphon : 0.38,
        hasAngular ? angular : 0.58,
        hasCommit ? commit : 0.75,
        hasMaxIter ? maxIter : 75,
        hasConvThreshold ? convThreshold : 0.002
      };
    };
  }
}
