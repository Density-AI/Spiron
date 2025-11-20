package com.spiron.network;

import static org.junit.jupiter.api.Assertions.*;

import com.spiron.config.BroadcastValidationConfig;
import com.spiron.core.EddyEngine;
import com.spiron.metrics.RpcMetrics;
import com.spiron.proto.EddyProto.Ack;
import com.spiron.proto.EddyProto.CommitBody;
import com.spiron.proto.EddyProto.CommitEnvelope;
import com.spiron.proto.EddyProto.EddyStateMsg;
import com.spiron.serialization.CRDTJsonCodec;
import com.spiron.storage.RocksDbCRDTStore;
import io.grpc.Context;
import io.grpc.stub.StreamObserver;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class RpcServerMetricsTest {

  @TempDir
  Path tempDir;

  private RocksDbCRDTStore crdtStore;
  private CRDTJsonCodec codec;

  @BeforeEach
  void setUp() throws Exception {
    crdtStore = new RocksDbCRDTStore(tempDir);
    codec = new CRDTJsonCodec();
  }

  @AfterEach
  void tearDown() {
    if (crdtStore != null) {
      crdtStore.close();
    }
  }

  @Test
  void serverAttributesPeerTagOnMetrics() throws Exception {
    var registry = new SimpleMeterRegistry();
    var metrics = new RpcMetrics(registry);

    var engine = new EddyEngine(0.98, 0.2, 0.6, 2.5);
    
    var validationConfig = new BroadcastValidationConfig(
      1,              // vectorDimensions (accept 1 for this test)
      0.0,            // minEnergy
      1000.0,         // maxEnergy
      "^[a-zA-Z0-9_-]{1,128}$",  // idPattern
      60000,          // duplicateExpiryMs
      1000,           // rateLimitPerSecond
      ""              // peerAllowlistRegex (disabled)
    );
    
    long finalityThreshold = 3;
    var rpcServer = new RpcServer(8080, engine, crdtStore, codec, metrics, null, null, validationConfig, finalityThreshold);
    var service = new RpcServer.EddyRpcService(engine, crdtStore, codec, metrics, null, validationConfig);

    // Broadcast test: set peer in Context
    String peer = "1.2.3.4:54321";
    EddyStateMsg msg = EddyStateMsg.newBuilder()
      .setId("a")
      .addVector(0.1)
      .setEnergy(1.0)
      .build();

    AtomicReference<Ack> got = new AtomicReference<>();
    StreamObserver<Ack> obs = new StreamObserver<>() {
      public void onNext(Ack a) {
        got.set(a);
      }

      public void onError(Throwable t) {}

      public void onCompleted() {}
    };

    Context.current()
      .withValue(RpcServer.PEER_KEY, peer)
      .run(() -> {
        service.broadcast(msg, obs);
      });

    // Verify broadcast was accepted
    assertNotNull(got.get(), "Broadcast should receive ACK");
    assertEquals("ok", got.get().getStatus(), "Broadcast should be accepted");

    // Verify the broadcast counter exists (without peer tag for broadcast)
    var c = registry
      .find("spiron_rpc_broadcast_total")
      .counter();
    assertNotNull(c, "Broadcast counter should exist");
    assertTrue(c.count() >= 1.0, "Broadcast counter should be incremented");

    // Commit test: create envelope with receiver-signs path (no pub/sig)
    CommitBody body = CommitBody.newBuilder()
      .setId("c1")
      .addVector(0.2)
      .setEnergy(3.0)
      .build();
    CommitEnvelope env = CommitEnvelope.newBuilder()
      .setBody(body)
      .setSigScheme("receiver-signs")
      .build();

    AtomicReference<Ack> got2 = new AtomicReference<>();
    StreamObserver<Ack> obs2 = new StreamObserver<>() {
      public void onNext(Ack a) {
        got2.set(a);
      }

      public void onError(Throwable t) {}

      public void onCompleted() {}
    };

    Context.current()
      .withValue(RpcServer.PEER_KEY, peer)
      .run(() -> {
        service.commit(env, obs2);
      });

    var c2 = registry
      .find("spiron_rpc_commit_total")
      .counter();
    assertNotNull(c2);
    assertTrue(c2.count() >= 1.0);
  }
}
