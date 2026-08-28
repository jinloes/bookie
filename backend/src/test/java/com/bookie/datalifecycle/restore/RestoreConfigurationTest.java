package com.bookie.datalifecycle.restore;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RestoreConfigurationTest {

  private final RestoreConfiguration configuration = new RestoreConfiguration();

  @Test
  void createsPathAndJournalBeansForTheConfiguredDirectory(@TempDir Path directory)
      throws Exception {
    RestorePaths paths = configuration.restorePaths(directory.toString());
    RestoreJournalStore store = configuration.restoreJournalStore(paths);

    assertThat(paths.dataDirectory()).isEqualTo(directory.toRealPath());
    assertThat(store).isNotNull();
  }
}
