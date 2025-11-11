package com.spiron.core;

import static org.assertj.core.api.Assertions.*;

import java.nio.file.*;
import org.junit.jupiter.api.*;

public class SpironSnapshotStoreTest {

  private Path tmpDir;

  @BeforeEach
  void setup() throws Exception {
    tmpDir = Files.createTempDirectory("snapshotstore");
  }

  @AfterEach
  void cleanup() throws Exception {
    Files.walk(tmpDir)
      .sorted((a, b) -> b.compareTo(a))
      .forEach(p -> p.toFile().delete());
  }

  @Test
  void testSaveAndLoad() throws Exception {
    var store = new SpironSnapshotStore(tmpDir.toString());
    var state = new EddyState("test", new double[] { 1, 2 }, 5.0);

    store.save(state);
    var loaded = store.load();
    assertThat(loaded).isPresent();
    assertThat(loaded.get().energy()).isEqualTo(5.0);
  }
}
