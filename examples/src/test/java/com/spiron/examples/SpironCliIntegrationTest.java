package com.spiron.examples;

import static org.junit.jupiter.api.Assertions.*;

import com.spiron.tools.SpironCli;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class SpironCliIntegrationTest {

  @Test
  void startAndStopServerHandle() throws Exception {
    // pick a free port
    ServerSocket s = new ServerSocket(0);
    int port = s.getLocalPort();
    s.close();

    var tmp = Files.createTempDirectory("spiron-cli-test");
    Map<String, String> overrides = new HashMap<>();
    overrides.put("spiron.node.id", "node-test");
    overrides.put("spiron.port", Integer.toString(port));
    overrides.put("spiron.cluster.peers", "localhost:" + port);
    overrides.put("spiron.damping.alpha", "0.98");
    overrides.put("spiron.siphon.factor", "0.2");
    overrides.put("spiron.angular.threshold", "0.6");
    overrides.put("spiron.commit.energy", "2.5");
    overrides.put("spiron.data.dir", tmp.toString());

    var handle = SpironCli.startServerWithHandle(overrides);
    assertTrue(handle.isRunning());
    handle.stop();
    Thread.sleep(200);
    assertFalse(handle.isRunning());
  }
}
