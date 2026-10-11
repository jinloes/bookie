package com.bookie.datalifecycle.backup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import jakarta.servlet.FilterChain;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class WriteFreezeTest {

  @TempDir Path dataDirectory;

  private MockHttpServletResponse filter(
      WriteFreeze freeze, String method, String path, FilterChain chain) throws Exception {
    MockHttpServletResponse response = new MockHttpServletResponse();
    freeze.doFilter(new MockHttpServletRequest(method, path), response, chain);
    return response;
  }

  @Nested
  class Lifecycle {

    @Test
    void startsUnfrozenWithoutAFlagFile() {
      assertThat(new WriteFreeze(dataDirectory).isFrozen()).isFalse();
    }

    @Test
    void survivesARestartUntilLifted() {
      new WriteFreeze(dataDirectory).freeze();

      WriteFreeze restarted = new WriteFreeze(dataDirectory);
      assertThat(restarted.isFrozen()).isTrue();
      assertThat(dataDirectory.resolve(WriteFreeze.FLAG_FILE)).exists();

      restarted.lift();

      assertThat(restarted.isFrozen()).isFalse();
      assertThat(dataDirectory.resolve(WriteFreeze.FLAG_FILE)).doesNotExist();
      assertThat(new WriteFreeze(dataDirectory).isFrozen()).isFalse();
    }

    @Test
    void createsAMissingDataDirectory() {
      Path nested = dataDirectory.resolve("missing");

      new WriteFreeze(nested).freeze();

      assertThat(nested.resolve(WriteFreeze.FLAG_FILE)).exists();
    }

    @Test
    void reportsAFlagThatCannotBeWritten() throws Exception {
      Path blocker = Files.writeString(dataDirectory.resolve("file"), "x");
      WriteFreeze freeze = new WriteFreeze(blocker.resolve("child"));

      assertThatThrownBy(freeze::freeze)
          .isInstanceOf(UncheckedIOException.class)
          .hasMessage("Could not persist the write freeze");
      assertThat(freeze.isFrozen()).isFalse();
    }

    @Test
    void reportsAFlagThatCannotBeRemoved() throws Exception {
      Path flag = Files.createDirectories(dataDirectory.resolve(WriteFreeze.FLAG_FILE));
      Files.writeString(flag.resolve("inside"), "x");
      WriteFreeze freeze = new WriteFreeze(dataDirectory);

      assertThatThrownBy(freeze::lift)
          .isInstanceOf(UncheckedIOException.class)
          .hasMessage("Could not remove the write freeze flag");
      assertThat(freeze.isFrozen()).isTrue();
    }
  }

  @Nested
  class DoFilter {

    @Test
    void passesEverythingThroughWhenNotFrozen() throws Exception {
      FilterChain chain = mock(FilterChain.class);

      MockHttpServletResponse response =
          filter(new WriteFreeze(dataDirectory), "POST", "/api/transactions", chain);

      assertThat(response.getStatus()).isEqualTo(200);
      verify(chain).doFilter(any(), any());
    }

    @Test
    void locksStateChangingCallsWhileFrozen() throws Exception {
      WriteFreeze freeze = new WriteFreeze(dataDirectory);
      freeze.freeze();
      FilterChain chain = mock(FilterChain.class);

      MockHttpServletResponse response = filter(freeze, "DELETE", "/api/transactions/1", chain);

      assertThat(response.getStatus()).isEqualTo(423);
      assertThat(response.getContentType()).isEqualTo("application/json");
      assertThat(response.getContentAsString()).isEqualTo(WriteFreeze.LOCKED_BODY);
      verify(chain, never()).doFilter(any(), any());
    }

    @ParameterizedTest
    @CsvSource({
      "GET, /api/transactions",
      "HEAD, /api/transactions",
      "OPTIONS, /api/transactions",
      "POST, /api/backup",
      "POST, /api/backup/restore",
      "POST, /api/backups",
      "POST, /api/backups/portable/restore"
    })
    void letsReadsAndBackupCallsThroughWhileFrozen(String method, String path) throws Exception {
      WriteFreeze freeze = new WriteFreeze(dataDirectory);
      freeze.freeze();
      FilterChain chain = mock(FilterChain.class);

      MockHttpServletResponse response = filter(freeze, method, path, chain);

      assertThat(response.getStatus()).isEqualTo(200);
      verify(chain).doFilter(any(), any());
    }

    @Test
    void doesNotTreatLookalikePathsAsBackupCalls() throws Exception {
      WriteFreeze freeze = new WriteFreeze(dataDirectory);
      freeze.freeze();

      MockHttpServletResponse response =
          filter(freeze, "POST", "/api/backupsettings", mock(FilterChain.class));

      assertThat(response.getStatus()).isEqualTo(423);
    }
  }
}
