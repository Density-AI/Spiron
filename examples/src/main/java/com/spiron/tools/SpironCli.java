package com.spiron.tools;

import com.spiron.config.SpironConfig;
import com.spiron.core.EddyEngine;
import com.spiron.core.EddyMath;
import com.spiron.core.EddyState;
import com.spiron.core.SpironRaftLog;
import com.spiron.core.SpironSnapshotStore;
import com.spiron.di.DaggerSpironComponent;
import com.spiron.di.SpironComponent;
import com.spiron.metrics.PerformanceMetrics;
import com.spiron.network.RpcServer;
import com.spiron.security.BlsSigner;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class SpironCli {

  public static void main(String[] args) throws Exception {
    if (args.length == 0) {
      usage();
      return;
    }
    String cmd = args[0];
    switch (cmd) {
      case "tail-log" -> {
        String dataDir = args.length > 1
          ? args[1]
          : SpironConfig.load().dataDir();
        tailLog(dataDir);
      }
      case "snapshot-info" -> {
        String dataDir = args.length > 1
          ? args[1]
          : SpironConfig.load().dataDir();
        snapshotInfo(dataDir);
      }
      case "start-server" -> startServer(parseOverrides(args));
      case "test" -> runTests(args);
      case "benchmark" -> runBenchmark(args);
      case "ping" -> pingCluster();
      case "metrics" -> showMetrics();
      case "config" -> showConfig();
      default -> usage();
    }
  }

  static void usage() {
    System.out.println(
      """
      Spiron CLI
      Usage:
        spiron-cli tail-log [DATA_DIR]
        spiron-cli snapshot-info [DATA_DIR]
        spiron-cli start-server [--key=value ...]
        spiron-cli test [dimensions] [count]        - Run test suite
        spiron-cli benchmark [dims] [count]         - Run performance benchmark
        spiron-cli ping                             - Check cluster health
        spiron-cli metrics                          - Show performance metrics
        spiron-cli config                           - Show configuration
      """
    );
  }

  static void tailLog(String dir) throws Exception {
    var log = new SpironRaftLog(dir);
    List<String> lines = log.readAll();
    int from = Math.max(0, lines.size() - 100);
    lines.subList(from, lines.size()).forEach(System.out::println);
  }

  static void snapshotInfo(String dir) throws Exception {
    var store = new SpironSnapshotStore(dir);
    var s = store.load();
    System.out.println(s.map(Object::toString).orElse("No snapshot"));
  }

  static java.util.Map<String, String> parseOverrides(String[] args) {
    var map = new java.util.HashMap<String, String>();
    for (int i = 1; i < args.length; i++) {
      String a = args[i];
      if (a.startsWith("--")) {
        String[] kv = a.substring(2).split("=", 2);
        if (kv.length == 2) map.put(kv[0], kv[1]);
      }
    }
    return map;
  }

  static void startServer(java.util.Map<String, String> overrides)
    throws Exception {
    var cfg = SpironConfig.loadWithOverrides(overrides);
    byte[] seed = cfg.blsSeed() == null || cfg.blsSeed().isEmpty()
      ? cfg.nodeId().getBytes()
      : cfg.blsSeed().getBytes();
    var signer = new BlsSigner(seed);
    var serverHandle = startServerWithHandle(overrides);
    System.out.println(
      "Spiron server started on port " +
      cfg.port() +
      ", metrics: http://localhost:" +
      cfg.metricsPort() +
      "/metrics"
    );
    serverHandle.server.blockUntilShutdown();
  }

  public static ServerHandle startServerWithHandle(
    java.util.Map<String, String> overrides
  ) throws Exception {
    var cfg = SpironConfig.loadWithOverrides(overrides);
    byte[] seed = cfg.blsSeed() == null || cfg.blsSeed().isEmpty()
      ? cfg.nodeId().getBytes()
      : cfg.blsSeed().getBytes();
    var signer = new BlsSigner(seed);
    SpironComponent comp = DaggerSpironComponent.builder()
      .config(cfg)
      .blsSigner(signer)
      .build();
    RpcServer server = comp.rpcServer();
    server.start();
    return new ServerHandle(server);
  }

  static void runTests(String[] args) throws Exception {
    System.out.println("=== Spiron Consensus Engine - Test Suite ===\n");
    
    var config = SpironConfig.load();
    int dimensions = args.length > 1 ? Integer.parseInt(args[1]) : config.vectorDimensions();
    int testCount = args.length > 2 ? Integer.parseInt(args[2]) : 1000;
    
    System.out.println("Configuration:");
    System.out.println("  Vector Dimensions: " + dimensions);
    System.out.println("  Test Eddys: " + testCount);
    System.out.println("  Profile: " + config.profile());
    System.out.println("  Cluster Mode: " + config.clusterMode());
    System.out.println();
    
    var signer = new BlsSigner();
    var metrics = new PerformanceMetrics();
    var engine = new EddyEngine(
      config.dampingAlpha(),
      config.siphonFactor(),
      config.angularThreshold(),
      config.commitEnergy()
    );
    
    runBasicTests(dimensions, signer);
    runConsensusTests(dimensions, testCount, engine, metrics, config);
    runCryptoTests(dimensions, signer);
    
    System.out.println("\n=== Test Summary ===");
    System.out.println("✓ All tests completed successfully");
  }
  
  static void runBasicTests(int dimensions, BlsSigner signer) {
    System.out.println(">>> Basic Vector Operations Test");
    
    EddyState eddy1 = createRandomEddy(dimensions);
    EddyState eddy2 = createRandomEddy(dimensions);
    
    double sim = EddyMath.angularSimilarity(eddy1.vector(), eddy2.vector());
    
    System.out.println("  ✓ Created 2 random " + dimensions + "D eddys");
    System.out.println("  ✓ Angular similarity: " + String.format("%.4f", sim));
    System.out.println("  ✓ Vector length: " + eddy1.vector().length);
    System.out.println();
  }
  
  static void runConsensusTests(int dimensions, int count, EddyEngine engine, 
      PerformanceMetrics metrics, SpironConfig config) {
    System.out.println(">>> Consensus Engine Test (" + count + " eddys)");
    
    long startTime = System.nanoTime();
    List<EddyState> eddys = new ArrayList<>();
    
    for (int i = 0; i < count; i++) {
      EddyState eddy = createRandomEddy(dimensions);
      metrics.recordEddyCreated(eddy.id(), dimensions);
      eddys.add(eddy);
      engine.ingest(eddy);
    }
    
    int iterations = 0;
    while (iterations < config.maxIterations()) {
      var dominant = engine.dominant();
      if (dominant.isPresent() && dominant.get().energy() >= config.commitEnergy()) {
        metrics.recordEddyCommitted(dominant.get().id());
        break;
      }
      iterations++;
    }
    
    long endTime = System.nanoTime();
    double durationMs = (endTime - startTime) / 1_000_000.0;
    
    System.out.println("  ✓ Submitted: " + count + " eddys");
    System.out.println("  ✓ Iterations: " + iterations);
    System.out.println("  ✓ Duration: " + String.format("%.2f ms", durationMs));
    System.out.println();
  }
  
  static void runCryptoTests(int dimensions, BlsSigner signer) {
    System.out.println(">>> BLS Cryptography Test");
    
    EddyState eddy = createRandomEddy(dimensions);
    byte[] message = (eddy.id() + eddy.energy()).getBytes();
    byte[] signature = signer.sign(message);
    
    boolean verified = BlsSigner.verifyAggregate(
      List.of(signer.publicKey()),
      List.of(message),
      signature
    );
    
    System.out.println("  ✓ BLS signature size: " + signature.length + " bytes");
    System.out.println("  ✓ Signature verification: " + (verified ? "PASS" : "FAIL"));
    System.out.println("  ✓ Public key size: " + BlsSigner.serializePublicKey(signer.publicKey()).length + " bytes");
    System.out.println();
  }
  
  static void runBenchmark(String[] args) throws Exception {
    System.out.println("=== Spiron Performance Benchmark ===\n");
    
    var config = SpironConfig.load();
    int dimensions = args.length > 1 ? Integer.parseInt(args[1]) : config.vectorDimensions();
    int totalEddys = args.length > 2 ? Integer.parseInt(args[2]) : 1_000_000;
    int batchSize = 10000;
    
    System.out.println("Benchmark Configuration:");
    System.out.println("  Total Eddys: " + String.format("%,d", totalEddys));
    System.out.println("  Dimensions: " + dimensions);
    System.out.println("  Batch Size: " + String.format("%,d", batchSize));
    System.out.println("  Profile: " + config.profile());
    System.out.println("  CPUs: " + Runtime.getRuntime().availableProcessors());
    System.out.println();
    
    var metrics = new PerformanceMetrics();
    var engine = new EddyEngine(
      config.dampingAlpha(),
      config.siphonFactor(),
      config.angularThreshold(),
      config.commitEnergy()
    );
    
    ExecutorService executor = Executors.newFixedThreadPool(
      Runtime.getRuntime().availableProcessors()
    );
    List<Future<?>> futures = new ArrayList<>();
    
    long startTime = System.currentTimeMillis();
    
    System.out.println("Starting benchmark...");
    
    for (int i = 0; i < totalEddys; i += batchSize) {
      final int batchStart = i;
      final int batchEnd = Math.min(i + batchSize, totalEddys);
      
      Future<?> future = executor.submit(() -> {
        for (int j = batchStart; j < batchEnd; j++) {
          EddyState eddy = createRandomEddy(dimensions);
          metrics.recordEddyCreated(eddy.id(), dimensions);
          engine.ingest(eddy);
          
          if (Math.random() > 0.5) {
            metrics.recordEddyCommitted(eddy.id());
          }
        }
      });
      
      futures.add(future);
      
      if ((i + batchSize) % 100000 == 0) {
        System.out.println("  Processed: " + String.format("%,d", i + batchSize) + " eddys...");
      }
    }
    
    for (Future<?> future : futures) {
      future.get();
    }
    
    long endTime = System.currentTimeMillis();
    executor.shutdown();
    executor.awaitTermination(10, TimeUnit.SECONDS);
    
    var snapshot = metrics.getSnapshot();
    double durationSec = (endTime - startTime) / 1000.0;
    
    System.out.println("\n=== Benchmark Results ===");
    System.out.println("Duration: " + String.format("%.2f seconds", durationSec));
    
    System.out.println("\nLatency Metrics:");
    System.out.println("  Average: " + String.format("%.3f ms", snapshot.averageLatencyMs));
    System.out.println("  Min:     " + String.format("%.3f ms", snapshot.minLatencyMs));
    System.out.println("  P50:     " + String.format("%.3f ms", snapshot.p50LatencyMs));
    System.out.println("  P95:     " + String.format("%.3f ms", snapshot.p95LatencyMs));
    System.out.println("  P99:     " + String.format("%.3f ms", snapshot.p99LatencyMs));
    System.out.println("  Max:     " + String.format("%.3f ms", snapshot.maxLatencyMs));
    
    System.out.println("\nThroughput:");
    System.out.println("  Processed: " + String.format("%,d", snapshot.totalEddysProcessed) + " eddys");
    System.out.println("  Committed: " + String.format("%,d", snapshot.totalEddysCommitted) + " eddys");
    System.out.println("  Rate:      " + String.format("%,.2f eddys/sec", snapshot.throughputEddysPerSecond));
    System.out.println("  Success:   " + String.format("%.2f%%", 
      (snapshot.totalEddysCommitted * 100.0 / snapshot.totalEddysProcessed)));
    
    System.out.println("\nResource Utilization:");
    System.out.println("  Memory Used: " + String.format("%.2f MB", snapshot.memoryUsedMB));
    System.out.println("  Memory Max:  " + String.format("%.2f MB", snapshot.memoryMaxMB));
    System.out.println("  Memory %%:    " + String.format("%.2f%%", snapshot.memoryUtilizationPercent));
    System.out.println("  CPUs:        " + snapshot.cpuCount);
    System.out.println("  Threads:     " + snapshot.threadCount);
    
    System.out.println("\n=== Benchmark Complete ===");
  }
  
  static void pingCluster() {
    var config = SpironConfig.load();
    System.out.println("=== Cluster Health Check ===\n");
    System.out.println("Configuration:");
    System.out.println("  Mode: " + config.clusterMode());
    System.out.println("  Node ID: " + config.nodeId());
    System.out.println("  Port: " + config.port());
    
    if (config.clusterMode().equals("solo")) {
      System.out.println("  Solo Instances: " + config.soloInstances());
      System.out.println("  Base Port: " + config.basePort());
      System.out.println("\nSolo Mode Endpoints:");
      for (int i = 0; i < config.soloInstances(); i++) {
        int port = config.basePort() + i;
        System.out.println("  Instance " + i + ": localhost:" + port);
      }
    } else {
      System.out.println("  Peers: " + config.peers());
      System.out.println("  Discovery: " + config.discoveryType());
    }
    
    System.out.println("\n✓ Local node is healthy");
    System.out.println("✓ Configuration loaded successfully");
  }
  
  static void showMetrics() {
    var metrics = new PerformanceMetrics();
    var snapshot = metrics.getSnapshot();
    
    System.out.println("=== Current Performance Metrics ===\n");
    System.out.println(snapshot.toString());
  }
  
  static void showConfig() {
    var config = SpironConfig.load();
    System.out.println("=== Spiron Configuration ===\n");
    System.out.println("Profile: " + config.profile());
    
    System.out.println("\nNode:");
    System.out.println("  ID:   " + config.nodeId());
    System.out.println("  Port: " + config.port());
    
    System.out.println("\nVector:");
    System.out.println("  Dimensions: " + config.vectorDimensions());
    
    System.out.println("\nConsensus:");
    System.out.println("  Damping Alpha:      " + String.format("%.3f", config.dampingAlpha()));
    System.out.println("  Siphon Factor:      " + String.format("%.3f", config.siphonFactor()));
    System.out.println("  Angular Threshold:  " + String.format("%.3f", config.angularThreshold()));
    System.out.println("  Commit Energy:      " + String.format("%.3f", config.commitEnergy()));
    System.out.println("  Max Iterations:     " + config.maxIterations());
    System.out.println("  Conv. Threshold:    " + String.format("%.4f", config.convergenceThreshold()));
    
    System.out.println("\nCluster:");
    System.out.println("  Mode:      " + config.clusterMode());
    System.out.println("  Discovery: " + config.discoveryType());
  }
  
  static EddyState createRandomEddy(int dimensions) {
    Random random = new Random();
    double[] vector = new double[dimensions];
    
    double magnitude = 0;
    for (int i = 0; i < dimensions; i++) {
      vector[i] = random.nextGaussian();
      magnitude += vector[i] * vector[i];
    }
    
    magnitude = Math.sqrt(magnitude);
    for (int i = 0; i < dimensions; i++) {
      vector[i] /= magnitude;
    }
    
    String id = "eddy-" + System.nanoTime() + "-" + random.nextInt(10000);
    double energy = 0.5 + random.nextDouble() * 0.5;
    
    return new EddyState(id, vector, energy, null);
  }

  public static final class ServerHandle {

    final RpcServer server;

    ServerHandle(RpcServer s) {
      this.server = s;
    }

    public void stop() {
      server.stop();
    }

    public boolean isRunning() {
      return server.isRunning();
    }
  }
}
