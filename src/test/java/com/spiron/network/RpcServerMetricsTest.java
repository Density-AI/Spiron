package com.spiron.network;

import static org.junit.jupiter.api.Assertions.*;

import com.spiron.core.EddyEngine;
import com.spiron.metrics.RpcMetrics;
import com.spiron.proto.EddyProto.Ack;
import com.spiron.proto.EddyProto.CommitBody;
import com.spiron.proto.EddyProto.CommitEnvelope;
import com.spiron.proto.EddyProto.EddyStateMsg;
import io.grpc.Context;
import io.grpc.stub.StreamObserver;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

public class RpcServerMetricsTest {

  @Test
  void serverAttributesPeerTagOnMetrics() throws Exception {
    var registry = new SimpleMeterRegistry();
    var metrics = new RpcMetrics(registry);

    var engine = new EddyEngine(0.98, 0.2, 0.6, 2.5);
    var service = new RpcServer.EddyRpcService(engine, metrics);

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

    // Verify the per-peer broadcast counter exists
    var c = registry
      .find("spiron_rpc_broadcast_total")
      .tag("peer", peer)
      .counter();
    assertNotNull(c);
    assertTrue(c.count() >= 1.0);

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
      .tag("peer", peer)
      .counter();
    assertNotNull(c2);
    assertTrue(c2.count() >= 1.0);
  }
}
