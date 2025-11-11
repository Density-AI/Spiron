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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * High-level Spiron client API (primary).
 *
 * Replaces the older `SpironClient` (legacy wrapper available as SpironClientLegacy).
 */
public class SpironClient implements Closeable {

  private static final Logger log = LoggerFactory.getLogger(SpironClient.class);

  private final RpcClient rpcClient;
  private final ExecutorService executor;
  private final Map<String, String> properties;
  private volatile boolean closed = false;

  private SpironClient(
    RpcClient rpcClient,
    ExecutorService executor,
    Map<String, String> properties
  ) {
    this.rpcClient = Objects.requireNonNull(rpcClient);
    this.executor = Objects.requireNonNull(executor);
    this.properties = properties == null ? Map.of() : Map.copyOf(properties);
  }

  public CompletableFuture<Void> proposeAsync(EddyState state) {
    ensureOpen();
    CompletableFuture<Void> fut = new CompletableFuture<>();
    executor.submit(() -> {
      try {
        rpcClient.broadcast(state);
        fut.complete(null);
      } catch (Throwable t) {
        fut.completeExceptionally(t);
      }
    });
    return fut;
  }

  public void propose(EddyState state) throws Exception {
    proposeAsync(state).get();
  }

  public CompletableFuture<Void> commitAsync(EddyState state) {
    ensureOpen();
    CompletableFuture<Void> fut = new CompletableFuture<>();
    executor.submit(() -> {
      try {
        rpcClient.commit(state);
        fut.complete(null);
      } catch (Throwable t) {
        fut.completeExceptionally(t);
      }
    });
    return fut;
  }

  public void commit(EddyState state) throws Exception {
    commitAsync(state).get();
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
    executor.shutdownNow();
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
        : new RpcClient(finalPeers, signer, finalWorkerThreads, null);
      ExecutorService ex = Executors.newFixedThreadPool(finalWorkerThreads);
      return new SpironClient(rpc, ex, properties);
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
    byte[] seed = cfg.blsSeed() == null || cfg.blsSeed().isEmpty()
      ? cfg.nodeId().getBytes()
      : cfg.blsSeed().getBytes();
    BlsSigner signer = new BlsSigner(seed);
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
