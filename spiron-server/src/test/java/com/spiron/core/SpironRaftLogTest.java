package com.spiron.core;

import static org.assertj.core.api.Assertions.*;

import java.nio.file.*;
import org.junit.jupiter.api.*;

public class SpironRaftLogTest {

  private Path tmpDir;

  @BeforeEach
  void setup() throws Exception {
    tmpDir = Files.createTempDirectory("raftlogtest");
  }

  @AfterEach
  void cleanup() throws Exception {
    Files.walk(tmpDir)
      .sorted((a, b) -> b.compareTo(a))
      .forEach(p -> p.toFile().delete());
  }

  @Test
  void testAppendAndRead() throws Exception {
    SpironRaftLog log = new SpironRaftLog(tmpDir.toString());
    log.append("entry1");
    log.append("entry2");

    var entries = log.readAll();
    assertThat(entries).hasSize(2);
    assertThat(entries.get(0)).contains("entry1");
  }
}
