package com.bookie.util;

import static org.assertj.core.api.Assertions.assertThat;

import com.bookie.util.MacKeychain.CommandResult;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class MacKeychainTest {

  private final List<List<String>> commands = new ArrayList<>();

  private MacKeychain keychain(CommandResult result, String osName) {
    return new MacKeychain(
        command -> {
          commands.add(command);
          return result;
        },
        osName);
  }

  @Nested
  class IsAvailable {

    @Test
    void trueOnMacOs() {
      assertThat(keychain(new CommandResult(0, ""), "Mac OS X").isAvailable()).isTrue();
    }

    @Test
    void falseElsewhere() {
      assertThat(keychain(new CommandResult(0, ""), "Linux").isAvailable()).isFalse();
    }

    @Test
    void defaultConstructorUsesTheRunningOs() {
      assertThat(new MacKeychain().isAvailable())
          .isEqualTo(System.getProperty("os.name", "").toLowerCase().contains("mac"));
    }
  }

  @Nested
  class Read {

    @Test
    void returnsTheStrippedSecret() {
      MacKeychain keychain = keychain(new CommandResult(0, "s3cret\n"), "Mac OS X");

      assertThat(keychain.read("svc", "acct")).contains("s3cret");
      assertThat(commands)
          .containsExactly(
              List.of("security", "find-generic-password", "-a", "acct", "-s", "svc", "-w"));
    }

    @Test
    void returnsEmptyWithoutRunningAnythingWhenNotOnMac() {
      assertThat(keychain(new CommandResult(0, "x"), "Linux").read("svc", "acct")).isEmpty();
      assertThat(commands).isEmpty();
    }

    @Test
    void returnsEmptyWhenTheItemIsMissing() {
      assertThat(keychain(new CommandResult(44, ""), "Mac OS X").read("svc", "acct")).isEmpty();
    }

    @Test
    void returnsEmptyWhenOutputIsNull() {
      assertThat(keychain(new CommandResult(0, null), "Mac OS X").read("svc", "acct")).isEmpty();
    }

    @Test
    void returnsEmptyWhenOutputIsBlank() {
      assertThat(keychain(new CommandResult(0, "  \n"), "Mac OS X").read("svc", "acct")).isEmpty();
    }
  }

  @Nested
  class Write {

    @Test
    void updatesOrCreatesTheItem() {
      MacKeychain keychain = keychain(new CommandResult(0, ""), "Mac OS X");

      assertThat(keychain.write("svc", "acct", "pw")).isTrue();
      assertThat(commands)
          .containsExactly(
              List.of(
                  "security", "add-generic-password", "-U", "-a", "acct", "-s", "svc", "-w", "pw"));
    }

    @Test
    void reportsFailure() {
      assertThat(keychain(new CommandResult(1, ""), "Mac OS X").write("svc", "acct", "pw"))
          .isFalse();
    }

    @Test
    void returnsFalseWithoutRunningAnythingWhenNotOnMac() {
      assertThat(keychain(new CommandResult(0, ""), "Linux").write("svc", "acct", "pw")).isFalse();
      assertThat(commands).isEmpty();
    }
  }

  @Nested
  class RunProcess {

    @Test
    void capturesStdoutAndExitCode() {
      assertThat(MacKeychain.runProcess(List.of("sh", "-c", "echo hi; exit 3")))
          .isEqualTo(new CommandResult(3, "hi\n"));
    }

    @Test
    void failsWhenTheCommandCannotStart() {
      assertThat(MacKeychain.runProcess(List.of("/nonexistent/bookie-command")))
          .isEqualTo(new CommandResult(1, ""));
    }

    @Test
    void failsAndKeepsTheInterruptFlagWhenInterrupted() {
      Thread.currentThread().interrupt();
      try {
        // Closing both streams lets the reads finish so waitFor sees the interrupt.
        assertThat(MacKeychain.runProcess(List.of("sh", "-c", "exec >&- 2>&-; sleep 2")))
            .isEqualTo(new CommandResult(1, ""));
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
      } finally {
        Thread.interrupted();
      }
    }
  }
}
