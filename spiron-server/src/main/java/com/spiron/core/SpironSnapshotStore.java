package com.spiron.core;

import com.spiron.util.FileUtils;
import java.io.*;
import java.nio.file.*;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Simple snapshot store for durability and fast recovery.
 * Snapshots capture latest committed Eddy state.
 */
public class SpironSnapshotStore {

  private static final Logger log = LoggerFactory.getLogger(
    SpironSnapshotStore.class
  );
  private final Path snapshotDir;
  private final Path latestSnapshot;

  public SpironSnapshotStore(String baseDir) throws IOException {
    this.snapshotDir = FileUtils.ensureDir(baseDir + "/snapshots");
    this.latestSnapshot = snapshotDir.resolve("latest.snapshot");
    log.info("Snapshot store initialized at {}", latestSnapshot);
  }

  public synchronized void save(EddyState dominant) {
    try (
      var out = new ObjectOutputStream(Files.newOutputStream(latestSnapshot))
    ) {
      out.writeObject(dominant);
      log.info("Snapshot saved for eddy {}", dominant.id());
    } catch (Exception e) {
      log.error("Error saving snapshot", e);
    }
  }

  public synchronized Optional<EddyState> load() {
    if (!Files.exists(latestSnapshot)) return Optional.empty();
    try (var in = new ObjectInputStream(Files.newInputStream(latestSnapshot))) {
      EddyState state = (EddyState) in.readObject();
      log.info("Snapshot loaded for eddy {}", state.id());
      return Optional.of(state);
    } catch (Exception e) {
      log.error("Error loading snapshot", e);
      return Optional.empty();
    }
  }
}
