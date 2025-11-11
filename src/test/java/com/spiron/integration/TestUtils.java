package com.spiron.integration;

import com.spiron.proto.EddyProto;
import com.spiron.proto.EddyRpcGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

public class TestUtils {

  public static EddyRpcGrpc.EddyRpcBlockingStub stub(int port) {
    ManagedChannel ch = ManagedChannelBuilder.forAddress("localhost", port)
      .usePlaintext()
      .build();
    return EddyRpcGrpc.newBlockingStub(ch);
  }

  public static EddyProto.EddyStateMsg msg(String id, double e) {
    return EddyProto.EddyStateMsg.newBuilder()
      .setId(id)
      .addVector(0.8)
      .addVector(0.3)
      .setEnergy(e)
      .build();
  }
}
