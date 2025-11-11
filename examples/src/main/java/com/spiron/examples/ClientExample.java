package com.spiron.examples;

import com.spiron.api.SpironClient;
import com.spiron.core.EddyState;
import com.spiron.security.BlsSigner;
import java.util.List;

/** Tiny example showing how to build and use the SpironClient. */
public class ClientExample {

  public static void main(String[] args) throws Exception {
    var peers = List.of("localhost:8080");
    var signer = new BlsSigner();

    try (
      var client = new SpironClient.Builder()
        .peers(peers)
        .signer(signer)
        .workerThreads(2)
        .build()
    ) {
      var state = new EddyState("eddy-1", new double[] { 1.0, 0.5 }, 3.14);
      client.proposeAsync(state).thenRun(() -> System.out.println("proposed"));
      client
        .commitAsync(state)
        .thenRun(() -> System.out.println("commit sent"));
    }
  }
}
