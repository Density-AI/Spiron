package com.spiron.config;

import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
  String blsSeed
) {
  public static SpironConfig load() {
    try (
      InputStream in =
        SpironConfig.class.getResourceAsStream("/application.properties")
    ) {
      var props = new Properties();
      props.load(in);
      // Read raw values (allow environment overrides where appropriate)
      String nodeId = System.getenv()
        .getOrDefault("spiron_NODE_ID", props.getProperty("spiron.node.id"));
      if (nodeId == null || nodeId.isBlank()) {
        throw new RuntimeException(
          "Missing required configuration: 'spiron.node.id' (or env 'spiron_NODE_ID')."
        );
      }

      String portStr = System.getenv()
        .getOrDefault("spiron_PORT", props.getProperty("spiron.port"));
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

      String peersRaw = props.getProperty("spiron.cluster.peers");
      if (peersRaw == null || peersRaw.isBlank()) {
        throw new RuntimeException(
          "Missing required configuration: 'spiron.cluster.peers'."
        );
      }
      // allow discovery providers to initialize from properties
      com.spiron.discovery.Discovery.initFrom(props);

      List<String> peers = Arrays.stream(peersRaw.split(","))
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .flatMap(SpironConfig::expandPeerToken)
        .collect(Collectors.toList());
      if (peers.isEmpty()) {
        throw new RuntimeException(
          "Property 'spiron.cluster.peers' must contain at least one peer."
        );
      }

      double alpha = parseDoubleProp(
        "spiron.damping.alpha",
        props.getProperty("spiron.damping.alpha")
      );
      double siphon = parseDoubleProp(
        "spiron.siphon.factor",
        props.getProperty("spiron.siphon.factor")
      );
      double angular = parseDoubleProp(
        "spiron.angular.threshold",
        props.getProperty("spiron.angular.threshold")
      );
      double commit = parseDoubleProp(
        "spiron.commit.energy",
        props.getProperty("spiron.commit.energy")
      );

      String dataDir = props.getProperty("spiron.data.dir");
      if (dataDir == null || dataDir.isBlank()) {
        throw new RuntimeException(
          "Missing required configuration: 'spiron.data.dir'."
        );
      }

      // Optional configs with defaults
      int metricsPort = parseIntProp(
        "spiron.metrics.port",
        props.getProperty("spiron.metrics.port", "9090")
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

      return new SpironConfig(
        nodeId,
        port,
        peers,
        alpha,
        siphon,
        angular,
        commit,
        dataDir,
        metricsPort,
        rpcWorkerThreads,
        blsSeed
      );
    } catch (Exception e) {
      throw new RuntimeException(
        "Failed to load spiron configuration: " + e.getMessage(),
        e
      );
    }
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
        var resolved = com.spiron.discovery.Discovery.resolve(host, port);
        return resolved.stream();
      }
    } catch (Exception e) {
      // Fallthrough to default token
    }
    return Stream.of(token);
  }

  /**
   * Load configuration with overrides. The overrides map's entries take precedence
   * over values in application.properties. Environment variables still override these values
   * where applicable (e.g. spiron_NODE_ID, spiron_PORT).
   */
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

      // Read raw values (allow environment overrides where appropriate)
      String nodeId = System.getenv()
        .getOrDefault("spiron_NODE_ID", props.getProperty("spiron.node.id"));
      if (nodeId == null || nodeId.isBlank()) {
        throw new RuntimeException(
          "Missing required configuration: 'spiron.node.id' (or env 'spiron_NODE_ID')."
        );
      }

      String portStr = System.getenv()
        .getOrDefault("spiron_PORT", props.getProperty("spiron.port"));
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

      String peersRaw = props.getProperty("spiron.cluster.peers");
      if (peersRaw == null || peersRaw.isBlank()) {
        throw new RuntimeException(
          "Missing required configuration: 'spiron.cluster.peers'."
        );
      }
      List<String> peers = Arrays.stream(peersRaw.split(","))
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .flatMap(SpironConfig::expandPeerToken)
        .collect(Collectors.toList());
      if (peers.isEmpty()) {
        throw new RuntimeException(
          "Property 'spiron.cluster.peers' must contain at least one peer."
        );
      }

      double alpha = parseDoubleProp(
        "spiron.damping.alpha",
        props.getProperty("spiron.damping.alpha")
      );
      double siphon = parseDoubleProp(
        "spiron.siphon.factor",
        props.getProperty("spiron.siphon.factor")
      );
      double angular = parseDoubleProp(
        "spiron.angular.threshold",
        props.getProperty("spiron.angular.threshold")
      );
      double commit = parseDoubleProp(
        "spiron.commit.energy",
        props.getProperty("spiron.commit.energy")
      );

      String dataDir = props.getProperty("spiron.data.dir");
      if (dataDir == null || dataDir.isBlank()) {
        throw new RuntimeException(
          "Missing required configuration: 'spiron.data.dir'."
        );
      }

      int metricsPort = parseIntProp(
        "spiron.metrics.port",
        props.getProperty("spiron.metrics.port", "9090")
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

      return new SpironConfig(
        nodeId,
        port,
        peers,
        alpha,
        siphon,
        angular,
        commit,
        dataDir,
        metricsPort,
        rpcWorkerThreads,
        blsSeed
      );
    } catch (Exception e) {
      throw new RuntimeException(
        "Failed to load spiron configuration: " + e.getMessage(),
        e
      );
    }
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
}
