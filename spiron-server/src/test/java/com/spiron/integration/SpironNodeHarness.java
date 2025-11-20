package com.spiron.integration;

import com.spiron.config.SpironConfig;
import com.spiron.core.EddyEngine;
import com.spiron.di.DaggerSpironComponent;
import com.spiron.di.SpironComponent;
import com.spiron.network.RpcClient;
import com.spiron.security.BlsSigner;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Test harness to start a spiron node in-process for integration tests.
 * Provides deterministic key support for consistent cluster signatures.
 */
public class SpironNodeHarness {

  private final int port;
  private final Path dataDir;
  private final byte[] seed;
  private final List<String> peers;

  private SpironComponent component;
  private EddyEngine engine;
  private boolean started;

  /**
   * Creates a spiron test node using a deterministic BLS seed.
   */
  public SpironNodeHarness(int port, List<String> peers, byte[] seed)
    throws Exception {
    this.port = port;
    this.peers = peers;
    this.seed = Objects.requireNonNull(seed, "BLS seed must not be null");
    this.dataDir = Files.createTempDirectory("spiron-node-" + port);

    // Create config (matching spironConfig record)
    var cfg = new SpironConfig(
      "node-" + port,
      port,
      peers,
      0.98, // dampingAlpha
      0.2, // siphonFactor
      0.6, // angularThreshold
      2.5, // commitEnergy
      dataDir.toString(),
      9090, // metricsPort
      4, // rpcWorkerThreads
      "", // blsSeed
      "solo", // storageMode
      "", // etcdEndpoints
      "BALANCED", // profile
      128, // vectorDimensions
      "solo", // clusterMode
      3, // soloInstances
      8080, // basePort
      "static", // discoveryType
      "spiron-cluster", // k8sServiceName
      "default", // k8sNamespace
      5000, // healthCheckIntervalMs
      true, // metricsEnabled
      100, // maxIterations
      0.001, // convergenceThreshold
      0.0, // broadcastMinEnergy
      1000.0, // broadcastMaxEnergy
      "^[a-zA-Z0-9_-]{1,128}$", // broadcastIdPattern
      60000L, // broadcastDuplicateExpiryMs
      100, // broadcastRateLimitPerSecond
      "", // broadcastPeerAllowlistRegex
      3L // finalityThreshold
    );

    // Inject deterministic BlsSigner into Dagger before build
    BlsSigner signer = new BlsSigner(seed);

    this.component = DaggerSpironComponent.builder()
      .config(cfg)
      .blsSigner(signer)
      .build();

    this.engine = component.engine();
  }

  /**
   * Convenience constructor with random seed (non-deterministic).
   * Useful for standalone or local debugging.
   */
  public SpironNodeHarness(int port, List<String> peers) throws Exception {
    this(port, peers, ("spiron-shared-seed").getBytes());
  }

  /** Starts the node (RPC server, Raft manager, etc.). */
  public void start() throws Exception {
    if (started) return;
    component.rpcServer().start();
    started = true;
    System.out.printf(
      "🌀 Node started on port %d with data dir %s%n",
      port,
      dataDir
    );
  }

  /** Stops the node and cleans up resources. */
  public void stop() throws Exception {
    if (!started) return;
    component.rpcServer().stop();
    started = false;
    System.out.printf("🧹 Node stopped on port %d%n", port);
  }

  public RpcClient rpcClient() {
    return component.rpcClient();
  }

  /** @return Node’s local engine instance */
  public EddyEngine engine() {
    return engine;
  }

  /** @return Node port */
  public int port() {
    return port;
  }

  /** Simple readiness check for Awaitility */
  public boolean isReady() {
    try {
      return started && component.rpcServer().isRunning();
    } catch (Throwable t) {
      return false;
    }
  }
}
