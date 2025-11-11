package com.spiron.network;

import com.spiron.core.*;
import com.spiron.proto.EddyProto.*;
import com.spiron.proto.EddyRpcGrpc;
import com.spiron.security.BlsSigner;
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
    com.spiron.metrics.RpcMetrics rpcMetrics
  ) {
    this.server = ServerBuilder.forPort(port)
      // intercept to populate peer info into the Context for each call
      .intercept(new PeerInfoInterceptor())
      .addService(new EddyRpcService(engine, rpcMetrics))
      .addService(ProtoReflectionService.newInstance())
      .build();
  }

  public RpcServer(int port, EddyEngine engine) {
    this(port, engine, null);
  }

  public void start() throws Exception {
    server.start();
    log.info("Spiron RPC server listening on {}", server.getPort());
    Runtime.getRuntime().addShutdownHook(new Thread(server::shutdown));
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
    private final com.spiron.metrics.RpcMetrics rpcMetrics;

    EddyRpcService(
      EddyEngine engine,
      com.spiron.metrics.RpcMetrics rpcMetrics
    ) {
      this.engine = engine;
      this.rpcMetrics = rpcMetrics;
    }

    @Override
    public void broadcast(EddyStateMsg req, StreamObserver<Ack> obs) {
      double[] vec = req
        .getVectorList()
        .stream()
        .mapToDouble(Double::doubleValue)
        .toArray();
      var state = new EddyState(req.getId(), vec, req.getEnergy());
      engine.ingest(state);
      String peer = PEER_KEY.get();
      if (peer == null) peer = "unknown";
      if (rpcMetrics != null) rpcMetrics.incBroadcast(peer);
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
        var state = new EddyState(body.getId(), vec, body.getEnergy());

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
          if (rpcMetrics != null) rpcMetrics.incCommit(peer);
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
          if (rpcMetrics != null) rpcMetrics.incCommit(peer);
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
