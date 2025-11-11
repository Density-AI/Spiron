package com.spiron.util;

import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;

/** Utility for safe local persistence (logs, snapshots). */
public final class FileUtils {

  private FileUtils() {}

  public static Path ensureDir(String dir) throws IOException {
    Path p = Path.of(dir);
    if (!Files.exists(p)) Files.createDirectories(p);
    return p;
  }

  public static void appendLine(Path file, String line) throws IOException {
    Files.writeString(
      file,
      Instant.now() + " " + line + "\n",
      StandardOpenOption.CREATE,
      StandardOpenOption.APPEND
    );
  }
}
