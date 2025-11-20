package com.spiron.api;

import com.spiron.config.SpironConfig;
import com.spiron.core.EddyState;
import com.spiron.di.DaggerSpironComponent;
import com.spiron.di.SpironComponent;
import com.spiron.network.RpcClient;
import com.spiron.security.BlsSigner;
import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * High-level Spiron client API (primary).
 *
 * All operations are fully synchronous - no fire-and-forget async patterns.
 */
public class SpironClient implements Closeable {

  private static final Logger log = LoggerFactory.getLogger(SpironClient.class);

  private final RpcClient rpcClient;
  private final Map<String, String> properties;
  private volatile boolean closed = false;

  private SpironClient(
    RpcClient rpcClient,
    Map<String, String> properties
  ) {
    this.rpcClient = Objects.requireNonNull(rpcClient);
    this.properties = properties == null ? Map.of() : Map.copyOf(properties);
  }

  /**
   * Propose an eddy state to the cluster (synchronous broadcast).
   * Blocks until broadcast completes to all peers.
   */
  public void propose(EddyState state) {
    ensureOpen();
    rpcClient.broadcast(state);
  }

  /**
   * Commit an eddy state to the cluster (synchronous commit).
   * Blocks until commit completes to all peers.
   */
  public void commit(EddyState state) {
    ensureOpen();
    rpcClient.commit(state);
  }

  private void ensureOpen() {
    if (closed) throw new IllegalStateException("SpironClient is closed");
  }

  @Override
  public void close() {
    closed = true;
    try {
      rpcClient.shutdown();
    } catch (Throwable t) {
      log.debug("Error shutting down rpc client", t);
    }
  }

  /** Builder API */
  public static class Builder {

    private List<String> peers;
    private BlsSigner signer;
    private int workerThreads = 4;
    private Map<String, String> properties = new HashMap<>();

    public Builder properties(Map<String, String> props) {
      if (props != null) this.properties.putAll(props);
      return this;
    }

    public Builder peers(List<String> peers) {
      this.peers = peers;
      return this;
    }

    public Builder signer(BlsSigner signer) {
      this.signer = signer;
      return this;
    }

    public Builder workerThreads(int n) {
      this.workerThreads = Math.max(1, n);
      return this;
    }

    public SpironClient build() {
      SpironConfig cfg = SpironConfig.loadWithOverrides(properties);

      List<String> finalPeers = this.peers;
      if (finalPeers == null || finalPeers.isEmpty()) finalPeers = cfg.peers();
      if (
        finalPeers == null || finalPeers.isEmpty()
      ) throw new IllegalArgumentException("peers must be provided");

      int finalWorkerThreads = this.workerThreads;
      if (properties.containsKey("spiron.rpc.workerThreads")) {
        try {
          finalWorkerThreads = Integer.parseInt(
            properties.get("spiron.rpc.workerThreads")
          );
        } catch (NumberFormatException ignored) {}
      } else if (finalWorkerThreads == 4) {
        finalWorkerThreads = cfg.rpcWorkerThreads();
      }

      RpcClient rpc = signer == null
        ? new RpcClient(finalPeers)
        : new RpcClient(finalPeers, signer, finalWorkerThreads, null, null);
      return new SpironClient(rpc, properties);
    }
  }

  /** Convenience factories */
  public static SpironClient fromProperties(Map<String, String> props) {
    return new Builder().properties(props).build();
  }

  public static SpironClient fromConfig(SpironConfig cfg) {
    return new Builder()
      .peers(cfg.peers())
      .workerThreads(cfg.rpcWorkerThreads())
      .build();
  }

  public static SpironClient fromPropertiesFile(Path path) throws IOException {
    var p = new Properties();
    try (var in = Files.newInputStream(path)) {
      p.load(in);
    }
    Map<String, String> map = new HashMap<>();
    for (String name : p.stringPropertyNames()) map.put(
      name,
      p.getProperty(name)
    );
    return fromProperties(map);
  }

  /** Start an embedded server using the client's overrides. Returns a handle to stop it. */
  public EmbeddedServer startEmbeddedServer() throws Exception {
    SpironConfig cfg = SpironConfig.loadWithOverrides(this.properties);
    
    // Use keystore-based BLS signer for persistence
    Path keystoreDir = BlsSigner.getKeystoreDir(cfg.dataDir());
    BlsSigner signer = BlsSigner.fromKeystore(keystoreDir, cfg.nodeId());
    
    SpironComponent component = DaggerSpironComponent.builder()
      .config(cfg)
      .blsSigner(signer)
      .build();
    var rpcServer = component.rpcServer();
    var engine = component.engine();
    var server = new SpironServer(rpcServer, engine);
    Thread t = new Thread(server, "spiron-embedded-server");
    t.start();
    return new EmbeddedServer(server, t);
  }

  public static final class EmbeddedServer {

    private final SpironServer server;
    private final Thread thread;

    private EmbeddedServer(SpironServer server, Thread thread) {
      this.server = server;
      this.thread = thread;
    }

    public void stop() throws InterruptedException {
      server.shutdown();
      thread.join(2000);
    }
  }
}
