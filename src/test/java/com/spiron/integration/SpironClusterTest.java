package com.spiron.integration;

import static org.junit.jupiter.api.Assertions.*;

import com.spiron.core.EddyState;
import com.spiron.proto.EddyProto;
import com.spiron.proto.EddyRpcGrpc;
import io.grpc.ManagedChannelBuilder;
import java.time.Duration;
import java.util.List;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class SpironClusterTest {

  static SpironNodeHarness n1, n2;
  static EddyRpcGrpc.EddyRpcBlockingStub stub1, stub2;

  @BeforeAll
  static void startCluster() throws Exception {
    List<String> peers = List.of("localhost:8081", "localhost:8082");

    n1 = new SpironNodeHarness(8081, peers);
    n2 = new SpironNodeHarness(8082, peers);
    n1.start();
    n2.start();

    stub1 = EddyRpcGrpc.newBlockingStub(
      ManagedChannelBuilder.forAddress("localhost", 8081).usePlaintext().build()
    );
    stub2 = EddyRpcGrpc.newBlockingStub(
      ManagedChannelBuilder.forAddress("localhost", 8082).usePlaintext().build()
    );
  }

  @AfterAll
  static void stopCluster() throws Exception {
    n1.stop();
    n2.stop();
  }

  @Test
  @Order(1)
  void testBroadcastAndCommit() {
    // Broadcast several events to node 1 (unchanged)
    for (int i = 0; i < 3; i++) {
      var msg = EddyProto.EddyStateMsg.newBuilder()
        .setId("order-" + i)
        .addVector(0.7 + i * 0.1)
        .addVector(0.2 + i * 0.05)
        .setEnergy(0.9 + i * 0.3)
        .build();
      var ack = stub1.broadcast(msg);
      assertNotNull(ack);
    }

    // Commit via node-2's RpcClient -> replicates to both peers
    EddyState commitState = new EddyState(
      "dominant-order",
      new double[] { 0.9, 0.3 },
      3.0
    );
    n2.rpcClient().commit(commitState);

    // Wait until both nodes converge
    Awaitility.await()
      .atMost(Duration.ofSeconds(20)) // a bit more generous
      .until(
        () ->
          n1.engine().dominant().isPresent() &&
          n2.engine().dominant().isPresent()
      );

    var e1 = n1.engine().dominant().get();
    var e2 = n2.engine().dominant().get();
    assertEquals(
      e1.id(),
      e2.id(),
      "Both nodes should converge to the same dominant eddy"
    );
    System.out.println("✅ Cluster consensus verified successfully!");
  }
}
