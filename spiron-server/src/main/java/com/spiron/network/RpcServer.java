package com.spiron.network;

import com.spiron.config.BroadcastValidationConfig;
import com.spiron.core.*;
import com.spiron.metrics.MetricsUpdater;
import com.spiron.metrics.RpcMetrics;
import com.spiron.metrics.StorageMetrics;
import com.spiron.proto.EddyProto.*;
import com.spiron.proto.EddyRpcGrpc;
import com.spiron.security.BlsSigner;
import com.spiron.serialization.CRDTJsonCodec;
import com.spiron.storage.CRDTStore;
import com.spiron.storage.EtcdCRDTStore;
import com.spiron.storage.RocksDbCRDTStore;
import com.spiron.validation.*;
import io.grpc.Contexts;
import io.grpc.Metadata;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.protobuf.services.ProtoReflectionService;
import io.grpc.stub.StreamObserver;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** gRPC server for incoming eddy broadcasts and commits. */
public class RpcServer {

  private static final Logger log = LoggerFactory.getLogger(RpcServer.class);
  private final Server server;
  private final MetricsUpdater metricsUpdater;

  /**
   * Expose a Context.Key that holds the peer string (host:port) so services
   * can read the remote peer attribution.
   */
  public static final io.grpc.Context.Key<String> PEER_KEY =
    io.grpc.Context.key("spiron.peer");

  /**
   * Simple server interceptor that extracts the remote transport address and
   * places a host:port string into the gRPC Context for handlers to read.
   */
  static class PeerInfoInterceptor implements ServerInterceptor {

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
      ServerCall<ReqT, RespT> call,
      Metadata headers,
      ServerCallHandler<ReqT, RespT> next
    ) {
      SocketAddress sa = call
        .getAttributes()
        .get(io.grpc.Grpc.TRANSPORT_ATTR_REMOTE_ADDR);
      String peer = "unknown";
      if (sa instanceof InetSocketAddress isa) {
        var addr = isa.getAddress();
        if (addr != null) peer = addr.getHostAddress() + ":" + isa.getPort();
        else peer = isa.getHostString() + ":" + isa.getPort();
      } else if (sa != null) {
        peer = sa.toString();
      }
      var ctx = io.grpc.Context.current().withValue(PEER_KEY, peer);
      return Contexts.interceptCall(ctx, call, headers, next);
    }
  }

  public RpcServer(
    int port,
    EddyEngine engine,
    CRDTStore crdtStore,
    CRDTJsonCodec codec,
    RpcMetrics rpcMetrics,
    MetricsUpdater metricsUpdater,
    StorageMetrics storageMetrics,
    BroadcastValidationConfig validationConfig,
    long finalityThreshold
  ) {
    this.metricsUpdater = metricsUpdater;
    this.server = ServerBuilder.forPort(port)
      // intercept to populate peer info into the Context for each call
      .intercept(new PeerInfoInterceptor())
      .addService(new EddyRpcService(engine, crdtStore, codec, rpcMetrics, storageMetrics, validationConfig))
      .addService(new EddyGossipService(crdtStore, codec, finalityThreshold))
      .addService(ProtoReflectionService.newInstance())
      // Server-side keepalive settings to match client
      .keepAliveTime(30, java.util.concurrent.TimeUnit.SECONDS)
      .keepAliveTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
      .permitKeepAliveTime(10, java.util.concurrent.TimeUnit.SECONDS)
      .permitKeepAliveWithoutCalls(true)
      // Connection limits and timeouts
      .maxConnectionIdle(5, java.util.concurrent.TimeUnit.MINUTES)
      .maxConnectionAge(30, java.util.concurrent.TimeUnit.MINUTES)
      .maxConnectionAgeGrace(5, java.util.concurrent.TimeUnit.SECONDS)
      // Message size limits for large vectors\n      .maxInboundMessageSize(64 * 1024 * 1024) // 64MB\n      .maxInboundMetadataSize(8 * 1024) // 8KB
      .build();
  }

  public void start() throws Exception {
    server.start();
    log.info("Spiron RPC server listening on {}", server.getPort());
    if (metricsUpdater != null) {
      metricsUpdater.start();
    }
    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      server.shutdown();
      if (metricsUpdater != null) {
        metricsUpdater.stop();
      }
    }));
  }

  /** Return the port this server is bound to. */
  public int getPort() {
    return server.getPort();
  }

  public void blockUntilShutdown() throws InterruptedException {
    server.awaitTermination();
  }

  public void stop() {
    server.shutdownNow();
  }

  public boolean isRunning() {
    return server.isShutdown() != true && server.isTerminated() != true;
  }

  /** Service implementation mapping gRPC calls to EddyEngine methods. */
  static class EddyRpcService extends EddyRpcGrpc.EddyRpcImplBase {

    private final EddyEngine engine;
    private final CRDTStore crdtStore;
    private final CRDTJsonCodec codec;
    private final RpcMetrics rpcMetrics;
    private final StorageMetrics storageMetrics;
    private final BroadcastValidator validator;
    private final DuplicateDetector duplicateDetector;
    private final RateLimiter rateLimiter;
    private final PeerAllowlist peerAllowlist;

    EddyRpcService(
      EddyEngine engine,
      CRDTStore crdtStore,
      CRDTJsonCodec codec,
      RpcMetrics rpcMetrics,
      StorageMetrics storageMetrics,
      BroadcastValidationConfig validationConfig
    ) {
      this.engine = engine;
      this.crdtStore = crdtStore;
      this.codec = codec;
      this.rpcMetrics = rpcMetrics;
      this.storageMetrics = storageMetrics;
      this.validator = new BroadcastValidator(validationConfig);
      this.duplicateDetector = new DuplicateDetector(validationConfig.duplicateExpiryMs());
      
      // Create appropriate RateLimitStateStore based on storage mode
      RateLimitStateStore rateLimitStore = null;
      if (crdtStore instanceof RocksDbCRDTStore) {
        rateLimitStore = new RocksDbRateLimitStore(
          ((RocksDbCRDTStore) crdtStore).getDb()
        );
      } else if (crdtStore instanceof EtcdCRDTStore) {
        rateLimitStore = new EtcdRateLimitStore(
          ((EtcdCRDTStore) crdtStore).getClient()
        );
      }
      this.rateLimiter = new RateLimiter(validationConfig.rateLimitPerSecond(), rateLimitStore);
      this.peerAllowlist = new PeerAllowlist(validationConfig.peerAllowlistRegex());
    }

    @Override
    public void broadcast(EddyStateMsg req, StreamObserver<Ack> obs) {
      String peer = PEER_KEY.get();
      if (peer == null) peer = "unknown";
      
      // 1. Check peer allowlist
      if (!peerAllowlist.isAllowed(peer)) {
        if (rpcMetrics != null) {
          rpcMetrics.incBroadcastRejectedPeerAllowlist();
        }
        log.warn("Rejected broadcast from non-allowlisted peer: {}", peer);
        obs.onNext(Ack.newBuilder().setStatus("rejected_allowlist").build());
        obs.onCompleted();
        return;
      }
      
      // 2. Check rate limit
      if (!rateLimiter.allowRequest(peer)) {
        if (rpcMetrics != null) {
          rpcMetrics.incBroadcastRejectedRateLimit();
        }
        log.warn("Rate limit exceeded for peer: {}, eddy: {}", peer, req.getId());
        obs.onNext(Ack.newBuilder().setStatus("rejected_ratelimit").build());
        obs.onCompleted();
        return;
      }
      
      // 3. Convert to EddyState
      double[] vec = req
        .getVectorList()
        .stream()
        .mapToDouble(Double::doubleValue)
        .toArray();
      var state = new EddyState(req.getId(), vec, req.getEnergy(), null);
      
      // 4. Validate input
      var validationResult = validator.validate(state);
      if (!validationResult.isValid()) {
        if (rpcMetrics != null) {
          rpcMetrics.incBroadcastRejectedValidation();
        }
        log.warn("Validation failed for broadcast from peer: {}, eddy: {}, reason: {} - {}", 
          peer, req.getId(), validationResult.errorCode(), validationResult.errorMessage());
        obs.onNext(Ack.newBuilder()
          .setStatus("rejected_validation")
          .build());
        obs.onCompleted();
        return;
      }
      
      // 5. Check for duplicates
      if (duplicateDetector.isDuplicate(req.getId())) {
        if (rpcMetrics != null) {
          rpcMetrics.incBroadcastRejectedDuplicate();
        }
        log.debug("Duplicate broadcast rejected from peer: {}, eddy: {}", peer, req.getId());
        obs.onNext(Ack.newBuilder().setStatus("rejected_duplicate").build());
        obs.onCompleted();
        return;
      }
      
      // 6. Ingest into engine (in-memory)
      engine.ingest(state);
      
      // 7. Persist to CRDT store for crash recovery (with metrics)
      try {
        // Convert EddyState to CRDTEddy proto for persistence
        // Create CRDTVector with current timestamp
        var crdtVector = CRDTVector.newBuilder()
          .setTimestamp(System.currentTimeMillis())
          .setReplicaId(state.id()); // Use eddy ID as replica ID for broadcast
        for (double v : state.vector()) {
          crdtVector.addVector(v);
        }
        
        // Build CRDTEddy
        var crdtEddy = CRDTEddy.newBuilder()
          .setId(state.id())
          .setState(crdtVector.build())
          .setLastUpdated(System.currentTimeMillis())
          .build();
        
        final String json = codec.serializeEddy(crdtEddy);
        final long jsonBytes = json.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        final String eddyId = req.getId();
        
        log.info("About to persist eddy: {}, storageMetrics: {}", eddyId, (storageMetrics != null ? "PRESENT" : "NULL"));
        
        // Record storage metrics SYNCHRONOUSLY
        if (storageMetrics != null) {
          storageMetrics.recordWrite(() -> crdtStore.put(eddyId, json));
          storageMetrics.incWriteOps();
          storageMetrics.recordBytesWritten(jsonBytes);
          log.info("Persisted broadcast to CRDT store: {} ({} bytes) - metrics recorded", eddyId, jsonBytes);
        } else {
          crdtStore.put(eddyId, json);
          log.warn("Persisted broadcast to CRDT store (no metrics): {}", eddyId);
        }
      } catch (Exception e) {
        log.error("Failed to persist broadcast state to CRDT store: {}", req.getId(), e);
        // Continue - in-memory state is already ingested
      }
      
      // 8. Record success metrics
      if (rpcMetrics != null) {
        rpcMetrics.incBroadcast();
        log.info("Recorded broadcast from peer: {} for eddy: {}", peer, req.getId());
      } else {
        log.warn("RpcMetrics is NULL - cannot record broadcast for eddy: {}", req.getId());
      }
      
      obs.onNext(Ack.newBuilder().setStatus("ok").build());
      obs.onCompleted();
    }

    @Override
    public void commit(CommitEnvelope req, StreamObserver<Ack> obs) {
      try {
        var body = req.getBody();
        double[] vec = body
          .getVectorList()
          .stream()
          .mapToDouble(Double::doubleValue)
          .toArray();
        var state = new EddyState(body.getId(), vec, body.getEnergy(), null);

        byte[] msg = body.toByteArray();
        byte[] pubBytes = req.getBlsPubkey().toByteArray();
        byte[] sigBytes = req.getBlsSignature().toByteArray();
        String sigScheme = req.getSigScheme(); // proto3-safe accessor

        // 1️⃣ Normal sender-signed path
        if (
          !sigScheme.equals("receiver-signs") &&
          pubBytes.length > 0 &&
          sigBytes.length > 0
        ) {
          var pk = BlsSigner.parsePublicKey(pubBytes);
          boolean ok = BlsSigner.verifyAggregate(
            List.of(pk),
            List.of(msg),
            sigBytes
          );
          if (!ok) {
            log.warn("Invalid BLS signature for {}", body.getId());
            obs.onNext(Ack.newBuilder().setStatus("invalid-signature").build());
            obs.onCompleted();
            return;
          }
          engine.persistState(state);
          String peer = PEER_KEY.get();
          if (peer == null) peer = "unknown";
          if (rpcMetrics != null) rpcMetrics.incCommit();
          obs.onNext(Ack.newBuilder().setStatus("committed").build());
          obs.onCompleted();
          return;
        }

        // 2️⃣ Receiver-signs fallback (test/dev)
        if (
          sigScheme.equals("receiver-signs") ||
          (pubBytes.length == 0 && sigBytes.length == 0)
        ) {
          engine.persistState(state);
          String peer = PEER_KEY.get();
          if (peer == null) peer = "unknown";
          if (rpcMetrics != null) rpcMetrics.incCommit();
          obs.onNext(Ack.newBuilder().setStatus("committed").build());
          obs.onCompleted();
          return;
        }

        log.warn("Unknown signature scheme for {}", body.getId());
        obs.onNext(Ack.newBuilder().setStatus("invalid-signature").build());
        obs.onCompleted();
      } catch (Exception e) {
        log.error("Commit failed", e);
        obs.onNext(Ack.newBuilder().setStatus("error").build());
        obs.onCompleted();
      }
    }
  }
}
