package com.spiron.network;

import com.google.protobuf.ByteString;
import com.spiron.core.EddyState;
import com.spiron.proto.EddyProto.CommitBody;
import com.spiron.proto.EddyProto.CommitEnvelope;
import com.spiron.proto.EddyProto.EddyStateMsg;
import com.spiron.proto.EddyRpcGrpc;
import com.spiron.security.BlsSigner;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** gRPC client that broadcasts eddy states to peers. */
public class RpcClient {

  private static final Logger log = LoggerFactory.getLogger(RpcClient.class);

  private final List<EddyRpcGrpc.EddyRpcBlockingStub> stubs;
  private final List<String> peersList;
  private final ExecutorService pool;
  private final BlsSigner signer; // may be null if signatures are disabled

  /** Constructor without signatures (legacy / testing). */
  public RpcClient(List<String> peers) {
    this(peers, null, 4, null);
  }

  /** Constructor with optional BLS signer (recommended). */
  public RpcClient(List<String> peers, BlsSigner signer) {
    this(peers, signer, 4, null);
  }

  /** Constructor with configurable worker thread pool size and optional metrics. */
  public RpcClient(
    List<String> peers,
    BlsSigner signer,
    int workerThreads,
    com.spiron.metrics.RpcMetrics metrics
  ) {
    this.signer = signer;
    this.stubs = createStubs(peers);
    this.peersList = List.copyOf(peers);
    this.pool = Executors.newFixedThreadPool(Math.max(1, workerThreads));
    this.rpcMetrics = metrics;
  }

  private final com.spiron.metrics.RpcMetrics rpcMetrics;

  private static List<EddyRpcGrpc.EddyRpcBlockingStub> createStubs(
    List<String> peers
  ) {
    List<EddyRpcGrpc.EddyRpcBlockingStub> stubs = new ArrayList<>();
    for (String peer : peers) {
      try {
        var parts = peer.split(":");
        String host = parts[0];
        int port = Integer.parseInt(parts[1]);
        ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
          .usePlaintext()
          .build();
        stubs.add(EddyRpcGrpc.newBlockingStub(channel));
      } catch (Exception e) {
        log.warn("Failed to connect to peer {}: {}", peer, e.getMessage());
      }
    }
    return stubs;
  }

  public void broadcast(EddyState state) {
    EddyStateMsg msg = EddyStateMsg.newBuilder()
      .setId(state.id())
      .addAllVector(
        Arrays.stream(state.vector()).boxed().collect(Collectors.toList())
      )
      .setEnergy(state.energy())
      .build();

    for (int i = 0; i < stubs.size(); i++) {
      final var stub = stubs.get(i);
      final var peer = peersList.get(i);
      pool.submit(() -> {
        try {
          stub.broadcast(msg);
          if (rpcMetrics != null) {
            rpcMetrics.incBroadcast();
            rpcMetrics.incBroadcast(peer);
          }
        } catch (Exception e) {
          if (rpcMetrics != null) rpcMetrics.incFailure(peer);
          log.debug("Broadcast failed: {}", e.getMessage());
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
      final var peer = peersList.get(i);
      pool.submit(() -> {
        try {
          stub.commit(env);
          if (rpcMetrics != null) rpcMetrics.incCommit(peer);
        } catch (Exception e) {
          if (rpcMetrics != null) rpcMetrics.incFailure(peer);
          log.debug("Commit notify failed: {}", e.getMessage());
        }
      });
    }
  }

  public void shutdown() {
    pool.shutdownNow();
  }
}
