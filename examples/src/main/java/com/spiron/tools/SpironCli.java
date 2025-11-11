package com.spiron.tools;

import com.spiron.config.SpironConfig;
import com.spiron.core.SpironRaftLog;
import com.spiron.core.SpironSnapshotStore;
import com.spiron.di.DaggerSpironComponent;
import com.spiron.di.SpironComponent;
import com.spiron.network.RpcServer;
import com.spiron.security.BlsSigner;
import java.util.List;

public class SpironCli {

  public static void main(String[] args) throws Exception {
    if (args.length == 0) {
      usage();
      return;
    }
    String cmd = args[0];
    switch (cmd) {
      case "tail-log" -> {
        String dataDir = args.length > 1
          ? args[1]
          : SpironConfig.load().dataDir();
        tailLog(dataDir);
      }
      case "snapshot-info" -> {
        String dataDir = args.length > 1
          ? args[1]
          : SpironConfig.load().dataDir();
        snapshotInfo(dataDir);
      }
      case "start-server" -> startServer(parseOverrides(args));
      default -> usage();
    }
  }

  static void usage() {
    System.out.println(
      """
      Spiron CLI
      Usage:
        spiron-cli tail-log [DATA_DIR]
        spiron-cli snapshot-info [DATA_DIR]
      """
    );
  }

  static void tailLog(String dir) throws Exception {
    var log = new SpironRaftLog(dir);
    List<String> lines = log.readAll();
    int from = Math.max(0, lines.size() - 100);
    lines.subList(from, lines.size()).forEach(System.out::println);
  }

  static void snapshotInfo(String dir) throws Exception {
    var store = new SpironSnapshotStore(dir);
    var s = store.load();
    System.out.println(s.map(Object::toString).orElse("No snapshot"));
  }

  static java.util.Map<String, String> parseOverrides(String[] args) {
    var map = new java.util.HashMap<String, String>();
    for (int i = 1; i < args.length; i++) {
      String a = args[i];
      if (a.startsWith("--")) {
        String[] kv = a.substring(2).split("=", 2);
        if (kv.length == 2) map.put(kv[0], kv[1]);
      }
    }
    return map;
  }

  static void startServer(java.util.Map<String, String> overrides)
    throws Exception {
    var cfg = SpironConfig.loadWithOverrides(overrides);
    byte[] seed = cfg.blsSeed() == null || cfg.blsSeed().isEmpty()
      ? cfg.nodeId().getBytes()
      : cfg.blsSeed().getBytes();
    var signer = new BlsSigner(seed);
    var serverHandle = startServerWithHandle(overrides);
    System.out.println(
      "Spiron server started on port " +
      cfg.port() +
      ", metrics: http://localhost:" +
      cfg.metricsPort() +
      "/metrics"
    );
    serverHandle.server.blockUntilShutdown();
  }

  /**
   * Programmatic API: start the server and return a handle so callers can stop it.
   * This is useful for tests and embedded usage.
   */
  public static ServerHandle startServerWithHandle(
    java.util.Map<String, String> overrides
  ) throws Exception {
    var cfg = SpironConfig.loadWithOverrides(overrides);
    byte[] seed = cfg.blsSeed() == null || cfg.blsSeed().isEmpty()
      ? cfg.nodeId().getBytes()
      : cfg.blsSeed().getBytes();
    var signer = new BlsSigner(seed);
    SpironComponent comp = DaggerSpironComponent.builder()
      .config(cfg)
      .blsSigner(signer)
      .build();
    RpcServer server = comp.rpcServer();
    server.start();
    return new ServerHandle(server);
  }

  public static final class ServerHandle {

    final RpcServer server;

    ServerHandle(RpcServer s) {
      this.server = s;
    }

    public void stop() {
      server.stop();
    }

    public boolean isRunning() {
      return server.isRunning();
    }
  }
}
