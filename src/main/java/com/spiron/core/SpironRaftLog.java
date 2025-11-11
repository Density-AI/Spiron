package com.spiron.core;

import com.spiron.util.FileUtils;
import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Persistent Raft-like log implementation for spiron.
 * Each entry = single line (timestamp, term, data)
 * Located under /var/lib/spiron/log/
 */
public class SpironRaftLog {

  private static final Logger log = LoggerFactory.getLogger(
    SpironRaftLog.class
  );
  private final Path logDir;
  private final Path logFile;
  private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

  public SpironRaftLog(String baseDir) throws IOException {
    this.logDir = FileUtils.ensureDir(baseDir + "/log");
    this.logFile = logDir.resolve("spiron.log");
    log.info("Raft log initialized at {}", logFile);
  }

  public void append(String entry) {
    lock.writeLock().lock();
    try {
      FileUtils.appendLine(logFile, entry);
    } catch (IOException e) {
      log.error("Error writing to Raft log", e);
    } finally {
      lock.writeLock().unlock();
    }
  }

  public List<String> readAll() {
    lock.readLock().lock();
    try {
      if (!Files.exists(logFile)) return List.of();
      return Files.readAllLines(logFile);
    } catch (IOException e) {
      log.error("Error reading Raft log", e);
      return List.of();
    } finally {
      lock.readLock().unlock();
    }
  }

  public long size() {
    try {
      return Files.exists(logFile) ? Files.size(logFile) : 0;
    } catch (IOException e) {
      return 0;
    }
  }
}
