package com.spiron.network;

import com.google.protobuf.ByteString;
import com.spiron.core.EddyState;
import com.spiron.metrics.RpcMetrics;
import com.spiron.metrics.ThroughputMetrics;
import com.spiron.proto.EddyProto.CommitBody;
import com.spiron.proto.EddyProto.CommitEnvelope;
import com.spiron.proto.EddyProto.EddyStateMsg;
import com.spiron.proto.EddyRpcGrpc;
import com.spiron.security.BlsSigner;
import io.grpc.ManagedChannel;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** gRPC client that broadcasts eddy states to peers. */
public class RpcClient {

  private static final Logger log = LoggerFactory.getLogger(RpcClient.class);

  // Retry configuration
  private static final int MAX_RETRY_ATTEMPTS = 3;
  private static final long INITIAL_RETRY_DELAY_MS = 10;
  private static final long MAX_RETRY_DELAY_MS = 500;
  private static final double RETRY_BACKOFF_MULTIPLIER = 2.0;
  
  // Circuit breaker configuration
  private static final int CIRCUIT_BREAKER_THRESHOLD = 10; // failures before opening
  private static final long CIRCUIT_BREAKER_TIMEOUT_MS = 5000; // time before retry
  
  // Timeout configuration
  private static final long RPC_TIMEOUT_MS = 2000; // 2 second timeout per RPC
  
  private final List<EddyRpcGrpc.EddyRpcBlockingStub> stubs;
  private final ExecutorService pool;
  private final BlsSigner signer; // may be null if signatures are disabled
  private final RpcMetrics rpcMetrics;
  private final ThroughputMetrics throughputMetrics;
  
  // Circuit breaker state per peer
  private final Map<Integer, CircuitBreaker> circuitBreakers = new ConcurrentHashMap<>();

  /** Constructor without signatures (legacy / testing). */
  public RpcClient(List<String> peers) {
    this(peers, null, 4, null, null);
  }

  /** Constructor with optional BLS signer (recommended). */
  public RpcClient(List<String> peers, BlsSigner signer) {
    this(peers, signer, 4, null, null);
  }

  /** Constructor with configurable worker thread pool size and optional metrics. */
  public RpcClient(
    List<String> peers,
    BlsSigner signer,
    int workerThreads,
    RpcMetrics metrics,
    ThroughputMetrics throughputMetrics
  ) {
    this.signer = signer;
    this.stubs = createStubs(peers);
    this.pool = Executors.newFixedThreadPool(Math.max(1, workerThreads));
    this.rpcMetrics = metrics;
    this.throughputMetrics = throughputMetrics;
  }

  private static List<EddyRpcGrpc.EddyRpcBlockingStub> createStubs(
    List<String> peers
  ) {
    List<EddyRpcGrpc.EddyRpcBlockingStub> stubs = new ArrayList<>();
    
    for (String peer : peers) {
      try {
        var parts = peer.split(":");
        String host = parts[0];
        int port = Integer.parseInt(parts[1]);
        
        log.info("Connecting to peer {} ({}:{})", peer, host, port);
        
        // Create socket address directly to avoid name resolution
        java.net.InetSocketAddress socketAddress = new java.net.InetSocketAddress(host, port);
        
        ManagedChannel channel = io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder
          .forAddress(socketAddress)
          .usePlaintext()
          // Enable keepalive to detect dead connections
          .keepAliveTime(30, TimeUnit.SECONDS)
          .keepAliveTimeout(10, TimeUnit.SECONDS)
          .keepAliveWithoutCalls(true)
          // Set connection and idle timeouts
          .idleTimeout(5, TimeUnit.MINUTES)
          // Enable retry on network failures
          .enableRetry()
          .maxRetryAttempts(3)
          // Connection settings
          .maxInboundMessageSize(64 * 1024 * 1024) // 64MB for large vectors
          .build();
        
        // Create stub WITHOUT deadline (we'll add fresh deadline per call)
        var stub = EddyRpcGrpc.newBlockingStub(channel);
        
        stubs.add(stub);
        log.info("Successfully connected to peer {} with keepalive and timeout configured", peer);
      } catch (Exception e) {
        log.error("Failed to connect to peer {}: {}", peer, e.getMessage(), e);
      }
    }
    if (stubs.isEmpty()) {
      log.error("No RPC stubs created! All peer connections failed.");
    } else {
      log.info("Created {} RPC stubs for {} peers with retry/keepalive enabled", stubs.size(), peers.size());
    }
    return stubs;
  }

  public void broadcast(EddyState state) {
    EddyStateMsg.Builder msgBuilder = EddyStateMsg.newBuilder()
      .setId(state.id())
      .addAllVector(
        Arrays.stream(state.vector()).boxed().collect(Collectors.toList())
      )
      .setEnergy(state.energy());
    // parentId is not in proto, so not set
    EddyStateMsg msg = msgBuilder.build();

    for (int i = 0; i < stubs.size(); i++) {
      final var stub = stubs.get(i);
      final int peerIndex = i;
      pool.submit(() -> {
        if (rpcMetrics != null) rpcMetrics.recordInFlight(1);
        
        // Check circuit breaker
        CircuitBreaker cb = circuitBreakers.computeIfAbsent(peerIndex, k -> new CircuitBreaker());
        if (cb.isOpen()) {
          log.debug("Circuit breaker OPEN for peer {}, skipping broadcast", peerIndex);
          if (rpcMetrics != null) rpcMetrics.incFailure();
          return;
        }
        
        try {
          // Retry with exponential backoff
          executeWithRetry(() -> {
            // Refresh deadline for each call
            var freshStub = stub.withDeadlineAfter(RPC_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (rpcMetrics != null) {
              rpcMetrics.recordLatency(() -> freshStub.broadcast(msg));
              rpcMetrics.incBroadcast();
            } else {
              freshStub.broadcast(msg);
            }
          }, peerIndex);
          
          // Success - reset circuit breaker
          cb.recordSuccess();
          
        } catch (Exception e) {
          cb.recordFailure();
          if (rpcMetrics != null) rpcMetrics.incFailure();
          
          // Log detailed error information
          String errorType = getErrorType(e);
          log.warn("Broadcast to peer {} failed after retries ({}): {}", 
            peerIndex, errorType, e.getMessage());
        } finally {
          if (rpcMetrics != null) rpcMetrics.recordInFlight(0);
        }
      });
    }
  }

  public void commit(EddyState state) {
    CommitBody body = CommitBody.newBuilder()
      .setId(state.id())
      .addAllVector(
        Arrays.stream(state.vector()).boxed().collect(Collectors.toList())
      )
      .setEnergy(state.energy())
      // parentId is not in proto, so not set
      .build();

    CommitEnvelope.Builder envB = CommitEnvelope.newBuilder().setBody(body);

    boolean signed = false;
    if (signer != null) {
      try {
        byte[] msg = body.toByteArray();
        byte[] sig = signer.sign(msg);
        byte[] pk = BlsSigner.serializePublicKey(signer.publicKey());
        envB.setBlsPubkey(ByteString.copyFrom(pk));
        envB.setBlsSignature(ByteString.copyFrom(sig));
        // optional proto flag (recommended)
        envB.setSigScheme(""); // normal
        signed = true;
        log.debug("Signed commit {}", state.id());
      } catch (Throwable t) {
        log.warn(
          "BLS signing unavailable on client, asking receiver to sign {}: {}",
          state.id(),
          t.toString()
        );
      }
    }

    if (!signed) {
      // Receiver-signed fallback: let server sign and persist
      envB.clearBlsPubkey();
      envB.clearBlsSignature();
      // optional proto flag (recommended)
      envB.setSigScheme("receiver-signs");
    }

    CommitEnvelope env = envB.build();

    for (int i = 0; i < stubs.size(); i++) {
      final var stub = stubs.get(i);
      final int peerIndex = i;
      pool.submit(() -> {
        if (rpcMetrics != null) rpcMetrics.recordInFlight(1);
        
        // Check circuit breaker
        CircuitBreaker cb = circuitBreakers.computeIfAbsent(peerIndex, k -> new CircuitBreaker());
        if (cb.isOpen()) {
          log.debug("Circuit breaker OPEN for peer {}, skipping commit", peerIndex);
          if (rpcMetrics != null) rpcMetrics.incFailure();
          return;
        }
        
        try {
          // Retry with exponential backoff
          executeWithRetry(() -> {
            // Refresh deadline for each call
            var freshStub = stub.withDeadlineAfter(RPC_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (rpcMetrics != null) {
              rpcMetrics.recordLatency(() -> freshStub.commit(env));
              rpcMetrics.incCommit();
            } else {
              freshStub.commit(env);
            }
          }, peerIndex);
          
          // Success - reset circuit breaker and record throughput metrics
          cb.recordSuccess();
          if (throughputMetrics != null) {
            throughputMetrics.incEddiesEmitted();
            // Estimate bytes: vector (8 bytes per double) + overhead
            long estimatedBytes = (long) (state.vector().length * 8 + 100);
            throughputMetrics.recordBytesEmitted(estimatedBytes);
          }
          
        } catch (Exception e) {
          cb.recordFailure();
          if (rpcMetrics != null) rpcMetrics.incFailure();
          
          // Log detailed error information
          String errorType = getErrorType(e);
          log.warn("Commit to peer {} failed after retries ({}): {}", 
            peerIndex, errorType, e.getMessage());
        } finally {
          if (rpcMetrics != null) rpcMetrics.recordInFlight(0);
        }
      });
    }
  }

  public void shutdown() {
    pool.shutdownNow();
  }
  
  /**
   * Execute RPC with retry and exponential backoff.
   */
  private void executeWithRetry(Runnable operation, int peerIndex) throws Exception {
    long delay = INITIAL_RETRY_DELAY_MS;
    Exception lastException = null;
    
    for (int attempt = 0; attempt < MAX_RETRY_ATTEMPTS; attempt++) {
      try {
        operation.run();
        return; // Success!
      } catch (Exception e) {
        lastException = e;
        
        // Don't retry on certain errors
        if (!isRetriableError(e)) {
          throw e;
        }
        
        if (attempt < MAX_RETRY_ATTEMPTS - 1) {
          log.debug("RPC to peer {} failed (attempt {}/{}), retrying in {}ms: {}",
            peerIndex, attempt + 1, MAX_RETRY_ATTEMPTS, delay, e.getMessage());
          
          try {
            Thread.sleep(delay);
          } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted during retry", ie);
          }
          
          // Exponential backoff with cap
          delay = Math.min((long)(delay * RETRY_BACKOFF_MULTIPLIER), MAX_RETRY_DELAY_MS);
        }
      }
    }
    
    throw lastException;
  }
  
  /**
   * Determine if an error is retriable.
   */
  private boolean isRetriableError(Exception e) {
    if (e instanceof StatusRuntimeException) {
      StatusRuntimeException sre = (StatusRuntimeException) e;
      Status.Code code = sre.getStatus().getCode();
      
      // Retriable errors
      return code == Status.Code.UNAVAILABLE ||
             code == Status.Code.DEADLINE_EXCEEDED ||
             code == Status.Code.RESOURCE_EXHAUSTED ||
             code == Status.Code.ABORTED ||
             code == Status.Code.INTERNAL;
    }
    
    // Network/connection errors are retriable
    return e instanceof java.io.IOException ||
           e.getMessage() != null && 
           (e.getMessage().contains("Connection refused") ||
            e.getMessage().contains("Connection reset") ||
            e.getMessage().contains("Broken pipe"));
  }
  
  /**
   * Get human-readable error type for logging.
   */
  private String getErrorType(Exception e) {
    if (e instanceof StatusRuntimeException) {
      StatusRuntimeException sre = (StatusRuntimeException) e;
      return "gRPC:" + sre.getStatus().getCode();
    }
    return e.getClass().getSimpleName();
  }
  
  /**
   * Simple circuit breaker implementation to prevent cascading failures.
   */
  private static class CircuitBreaker {
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private volatile long openedAt = 0;
    
    public boolean isOpen() {
      if (consecutiveFailures.get() < CIRCUIT_BREAKER_THRESHOLD) {
        return false;
      }
      
      // Check if timeout has elapsed
      long now = System.currentTimeMillis();
      if (now - openedAt > CIRCUIT_BREAKER_TIMEOUT_MS) {
        // Half-open state - allow one request through
        return false;
      }
      
      return true;
    }
    
    public void recordSuccess() {
      consecutiveFailures.set(0);
      openedAt = 0;
    }
    
    public void recordFailure() {
      int failures = consecutiveFailures.incrementAndGet();
      if (failures >= CIRCUIT_BREAKER_THRESHOLD) {
        openedAt = System.currentTimeMillis();
      }
    }
  }
}
