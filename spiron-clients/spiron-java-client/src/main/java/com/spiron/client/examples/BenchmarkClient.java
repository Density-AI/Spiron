package com.spiron.client.examples;

import com.spiron.client.SpironClient;
import com.spiron.core.EddyState;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.time.Duration;
import java.time.Instant;
import java.net.URI;

/**
 * Benchmark client for testing Spiron cluster performance.
 * Measures latency, throughput, and collects metrics from all nodes.
 */
public class BenchmarkClient {
    
    private static final int VECTOR_DIMENSIONS = 4096;
    private static int NUM_REQUESTS = 100_000;  // Default 100K requests (configurable)
    private static int WARMUP_REQUESTS = 5_000;  // 5K warmup (configurable)
    private static final int CONCURRENT_CLIENTS = 20;   // 20 worker threads
    
    // Vector clustering parameters for higher angular similarity
    private static final int NUM_CLUSTERS = 20;  // Create 20 distinct vector templates
    private static final double CLUSTER_SPREAD = 0.0;  // NO fuzzy spread - exact duplicates!
    private static final Random SHARED_RANDOM = new Random(42);  // Seeded for reproducibility
    private static final List<double[]> CLUSTER_CENTERS = new ArrayList<>();
    
    static {
        // Pre-generate cluster centers
        for (int i = 0; i < NUM_CLUSTERS; i++) {
            CLUSTER_CENTERS.add(generateClusterCenter(VECTOR_DIMENSIONS, SHARED_RANDOM));
        }
    }
    
    private static double[] generateClusterCenter(int dimensions, Random random) {
        double[] center = new double[dimensions];
        for (int i = 0; i < dimensions; i++) {
            center[i] = random.nextGaussian();
        }
        // Normalize
        double magnitude = 0;
        for (double v : center) {
            magnitude += v * v;
        }
        magnitude = Math.sqrt(magnitude);
        if (magnitude > 0) {
            for (int i = 0; i < dimensions; i++) {
                center[i] /= magnitude;
            }
        }
        return center;
    }
    
    public static void main(String[] args) throws Exception {
        // Read parameters from args
        int nodeCount = 7;  // Default to 7 nodes
        int numRequests = 100_000;  // Default to 100K
        
        if (args.length > 0) {
            nodeCount = Integer.parseInt(args[0]);
        }
        if (args.length > 1) {
            numRequests = Integer.parseInt(args[1]);
            NUM_REQUESTS = numRequests;
            // Scale warmup proportionally (5% of total requests, min 5K, max 50K)
            WARMUP_REQUESTS = Math.min(50_000, Math.max(5_000, numRequests / 20));
        }
        
        System.out.println("╔══════════════════════════════════════════════════════════╗");
        System.out.println("║     Spiron Benchmark - " + nodeCount + " Node Cluster (" + 
            String.format("%,d", numRequests) + "/4096D)      ║");
        System.out.println("╚══════════════════════════════════════════════════════════╝");
        System.out.println();
        
        List<String> peers = new ArrayList<>();
        int[] metricsPorts = new int[nodeCount];
        for (int i = 0; i < nodeCount; i++) {
            // Use 127.0.0.1 instead of localhost to avoid Unix socket resolution
            peers.add("127.0.0.1:" + (8081 + i));
            metricsPorts[i] = 9091 + i;
        }
        
        // Check node availability
        System.out.println("🔍 Checking node availability...");
        checkNodesAvailable(metricsPorts);
        
        // Create client
        System.out.println("📡 Creating Spiron client...");
        SpironClient client = new SpironClient.Builder()
            .peers(peers)
            .workerThreads(CONCURRENT_CLIENTS)
            .build();
        
        try {
            // Warmup phase
            System.out.println("\n🔥 Warmup phase: " + WARMUP_REQUESTS + " requests...");
            runWarmup(client);
            Thread.sleep(2000); // Let system stabilize
            
            // Benchmark phase
            System.out.println("\n⚡ Benchmark phase: " + NUM_REQUESTS + " requests with " + CONCURRENT_CLIENTS + " concurrent clients...");
            BenchmarkResult result = runBenchmark(client);
            
            // Wait for propagation
            System.out.println("\n⏳ Waiting for cluster propagation (5s)...");
            Thread.sleep(5000);
            
            // Collect metrics from all nodes
            System.out.println("\n📊 Collecting metrics from all nodes...");
            collectMetrics(metricsPorts);
            
            // Print results
            printResults(result);
            
        } finally {
            client.close();
            System.out.println("\n✅ Benchmark complete!");
        }
    }
    
    private static void checkNodesAvailable(int[] metricsPorts) {
        for (int i = 0; i < metricsPorts.length; i++) {
            try {
                URI uri = URI.create("http://localhost:" + metricsPorts[i] + "/metrics");
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) uri.toURL().openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(2000);
                int responseCode = conn.getResponseCode();
                if (responseCode == 200) {
                    System.out.println("  ✓ Node " + (i + 1) + " (port " + metricsPorts[i] + ") - ONLINE");
                } else {
                    System.out.println("  ✗ Node " + (i + 1) + " - UNEXPECTED RESPONSE: " + responseCode);
                }
                conn.disconnect();
            } catch (Exception e) {
                System.out.println("  ✗ Node " + (i + 1) + " (port " + metricsPorts[i] + ") - OFFLINE: " + e.getMessage());
            }
        }
        System.out.println();
    }
    
    private static void runWarmup(SpironClient client) throws Exception {
        Random random = new Random();
        for (int i = 0; i < WARMUP_REQUESTS; i++) {
            double[] vector = generateClusteredVector(VECTOR_DIMENSIONS, random);
            int clusterId = random.nextInt(NUM_CLUSTERS);
            // Fuzzy energy: base 1.0 + random jitter [0, 1.0]
            double fuzzyEnergy = 1.0 + random.nextDouble();
            // Use cluster ID so vectors merge when they hit the same cluster
            EddyState eddy = new EddyState("cluster-" + clusterId, vector, fuzzyEnergy, null);
            client.propose(eddy);
        }
        System.out.println("  ✓ Warmup complete");
    }
    
    private static BenchmarkResult runBenchmark(SpironClient client) throws Exception {
        List<Long> latencies = new CopyOnWriteArrayList<>();
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        
        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_CLIENTS);
        CountDownLatch latch = new CountDownLatch(NUM_REQUESTS);
        
        Instant startTime = Instant.now();
        
        for (int i = 0; i < NUM_REQUESTS; i++) {
            final int requestId = i;
            executor.submit(() -> {
                try {
                    Random random = new Random();
                    double[] vector = generateClusteredVector(VECTOR_DIMENSIONS, random);
                    int clusterId = random.nextInt(NUM_CLUSTERS);
                    // Fuzzy energy: base 1.5 + random jitter [0, 0.8]
                    double fuzzyEnergy = 1.5 + random.nextDouble() * 0.8;
                    // Use cluster ID so vectors with same ID will merge via Map.merge()
                    EddyState eddy = new EddyState("cluster-" + clusterId, vector, fuzzyEnergy, null);
                    
                    long reqStart = System.nanoTime();
                    client.propose(eddy);
                    long reqEnd = System.nanoTime();
                    
                    latencies.add((reqEnd - reqStart) / 1_000_000); // Convert to ms
                    successCount.incrementAndGet();
                    
                    if ((requestId + 1) % 10000 == 0) {
                        System.out.println("  Progress: " + (requestId + 1) + "/" + NUM_REQUESTS);
                    }
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                    System.err.println("  Request " + requestId + " failed: " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }
        
        latch.await(60, TimeUnit.SECONDS);
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);
        
        Instant endTime = Instant.now();
        long totalDurationMs = Duration.between(startTime, endTime).toMillis();
        
        return new BenchmarkResult(latencies, successCount.get(), failureCount.get(), totalDurationMs, 
            (int)((java.util.stream.IntStream.range(0, 10).filter(i -> {
                try {
                    java.net.URI uri = URI.create("http://localhost:" + (9091 + i) + "/metrics");
                    java.net.HttpURLConnection conn = (java.net.HttpURLConnection) uri.toURL().openConnection();
                    conn.setRequestMethod("GET");
                    conn.setConnectTimeout(500);
                    int code = conn.getResponseCode();
                    conn.disconnect();
                    return code == 200;
                } catch (Exception e) {
                    return false;
                }
            }).count())));
    }
    
    /**
     * Generate a clustered vector with high angular similarity to other vectors in the same cluster.
     * With CLUSTER_SPREAD=0.0, generates exact duplicates (similarity=1.0).
     * Fuzzy energy values ensure eddies still have variation.
     */
    private static double[] generateClusteredVector(int dimensions, Random random) {
        // Pick a random cluster center
        double[] center = CLUSTER_CENTERS.get(random.nextInt(NUM_CLUSTERS));
        
        if (CLUSTER_SPREAD == 0.0) {
            // Return exact copy for perfect angular similarity (1.0)
            return center.clone();
        }
        
        // Generate vector near the cluster center with fuzzy perturbation
        double[] vector = new double[dimensions];
        for (int i = 0; i < dimensions; i++) {
            // Mix cluster center with small random noise for fuzzy similarity
            // Higher weight on center = higher similarity between cluster members
            vector[i] = center[i] + random.nextGaussian() * CLUSTER_SPREAD;
        }
        
        // Normalize to unit vector
        double magnitude = 0;
        for (double v : vector) {
            magnitude += v * v;
        }
        magnitude = Math.sqrt(magnitude);
        if (magnitude > 0) {
            for (int i = 0; i < dimensions; i++) {
                vector[i] /= magnitude;
            }
        }
        
        return vector;
    }
    
    private static void collectMetrics(int[] metricsPorts) throws Exception {
        for (int i = 0; i < metricsPorts.length; i++) {
            System.out.println("\n🔹 Node " + (i + 1) + " Metrics (port " + metricsPorts[i] + "):");
            try {
                URI uri = URI.create("http://localhost:" + metricsPorts[i] + "/metrics");
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) uri.toURL().openConnection();
                conn.setRequestMethod("GET");
                
                try (java.io.BufferedReader reader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(conn.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.startsWith("spiron_") && !line.startsWith("#")) {
                            System.out.println("  " + line);
                        }
                    }
                }
                conn.disconnect();
            } catch (Exception e) {
                System.err.println("  Failed to fetch metrics: " + e.getMessage());
            }
        }
    }
    
    private static void printResults(BenchmarkResult result) {
        System.out.println("\n╔══════════════════════════════════════════════════════════╗");
        System.out.println("║                  BENCHMARK RESULTS                       ║");
        System.out.println("╚══════════════════════════════════════════════════════════╝");
        System.out.println();
        
        System.out.println("📈 THROUGHPUT:");
        System.out.println("  Total Requests:     " + NUM_REQUESTS);
        System.out.println("  Successful:         " + result.successCount + " (" + 
            String.format("%.2f%%", 100.0 * result.successCount / NUM_REQUESTS) + ")");
        System.out.println("  Failed:             " + result.failureCount);
        System.out.println("  Total Duration:     " + result.totalDurationMs + " ms");
        System.out.println("  Throughput:         " + String.format("%.2f", 
            1000.0 * result.successCount / result.totalDurationMs) + " req/sec");
        System.out.println();
        
        if (!result.latencies.isEmpty()) {
            Collections.sort(result.latencies);
            
            long min = result.latencies.get(0);
            long max = result.latencies.get(result.latencies.size() - 1);
            long p50 = result.latencies.get((int)(result.latencies.size() * 0.50));
            long p90 = result.latencies.get((int)(result.latencies.size() * 0.90));
            long p95 = result.latencies.get((int)(result.latencies.size() * 0.95));
            long p99 = result.latencies.get((int)(result.latencies.size() * 0.99));
            double avg = result.latencies.stream().mapToLong(Long::longValue).average().orElse(0);
            
            System.out.println("⏱️  LATENCY (milliseconds):");
            System.out.println("  Min:                " + min + " ms");
            System.out.println("  Max:                " + max + " ms");
            System.out.println("  Average:            " + String.format("%.2f", avg) + " ms");
            System.out.println("  P50 (median):       " + p50 + " ms");
            System.out.println("  P90:                " + p90 + " ms");
            System.out.println("  P95:                " + p95 + " ms");
            System.out.println("  P99:                " + p99 + " ms");
            System.out.println();
        }
        
        System.out.println("🎯 CONFIGURATION:");
        System.out.println("  Vector Dimensions:  " + VECTOR_DIMENSIONS);
        System.out.println("  Concurrent Clients: " + CONCURRENT_CLIENTS);
        System.out.println("  Cluster Nodes:      " + result.nodeCount);
        System.out.println("  Storage Mode:       RocksDB (solo)");
        System.out.println("  Profile:            LOW_LATENCY");
        System.out.println();
        
        // Memory estimation
        long vectorSizeBytes = VECTOR_DIMENSIONS * 8; // 8 bytes per double
        long totalDataBytes = vectorSizeBytes * result.successCount;
        System.out.println("💾 ESTIMATED DATA:");
        System.out.println("  Vector Size:        " + vectorSizeBytes + " bytes");
        System.out.println("  Total Ingested:     " + String.format("%.2f", totalDataBytes / 1024.0 / 1024.0) + " MB");
        System.out.println();
    }
    
    static class BenchmarkResult {
        final List<Long> latencies;
        final int successCount;
        final int failureCount;
        final long totalDurationMs;
        final int nodeCount;
        
        BenchmarkResult(List<Long> latencies, int successCount, int failureCount, long totalDurationMs) {
            this(latencies, successCount, failureCount, totalDurationMs, 0);
        }
        
        BenchmarkResult(List<Long> latencies, int successCount, int failureCount, long totalDurationMs, int nodeCount) {
            this.latencies = latencies;
            this.successCount = successCount;
            this.failureCount = failureCount;
            this.totalDurationMs = totalDurationMs;
            this.nodeCount = nodeCount;
        }
    }
}
