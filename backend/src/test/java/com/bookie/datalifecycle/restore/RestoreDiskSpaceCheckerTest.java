package com.bookie.datalifecycle.restore;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RestoreDiskSpaceCheckerTest {

  private final RestoreDiskSpaceChecker checker = new RestoreDiskSpaceChecker();

  @Test
  void acceptsAnAvailableRequirement(@TempDir Path directory) {
    assertThatCode(() -> checker.requireAvailable(directory, 0)).doesNotThrowAnyException();
  }

  @Test
  void rejectsARequirementLargerThanTheFilesystem(@TempDir Path directory) {
    assertThatThrownBy(() -> checker.requireAvailable(directory, Long.MAX_VALUE))
        .isInstanceOf(IOException.class)
        .hasMessageContaining("Insufficient disk space");
  }
}
