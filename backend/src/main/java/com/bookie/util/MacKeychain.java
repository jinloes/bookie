package com.bookie.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Reads and writes macOS Keychain generic passwords through the {@code security} CLI. Secrets are
 * never logged or included in exception messages.
 */
public class MacKeychain implements SecretStore {

  /** Runs one external command; replaceable in tests so no real Keychain is touched. */
  @FunctionalInterface
  public interface CommandRunner {
    CommandResult run(List<String> command);
  }

  public record CommandResult(int exitCode, String stdout) {}

  private final CommandRunner runner;
  private final String osName;

  public MacKeychain() {
    this(MacKeychain::runProcess, System.getProperty("os.name", ""));
  }

  public MacKeychain(CommandRunner runner, String osName) {
    this.runner = runner;
    this.osName = osName;
  }

  @Override
  public boolean isAvailable() {
    return osName.toLowerCase(Locale.ROOT).contains("mac");
  }

  /** Returns the stored secret, or empty when the item is missing or the Keychain is absent. */
  @Override
  public Optional<String> read(String service, String account) {
    if (!isAvailable()) {
      return Optional.empty();
    }
    CommandResult result =
        runner.run(
            List.of("security", "find-generic-password", "-a", account, "-s", service, "-w"));
    if (result.exitCode() != 0 || result.stdout() == null || result.stdout().isBlank()) {
      return Optional.empty();
    }
    return Optional.of(result.stdout().strip());
  }

  /** Creates or updates the item; returns false when the Keychain is absent or the write fails. */
  @Override
  public boolean write(String service, String account, String secret) {
    if (!isAvailable()) {
      return false;
    }
    CommandResult result =
        runner.run(
            List.of(
                "security",
                "add-generic-password",
                "-U",
                "-a",
                account,
                "-s",
                service,
                "-w",
                secret));
    return result.exitCode() == 0;
  }

  @Override
  public String displayName() {
    return "macOS Keychain";
  }

  @Override
  public String storeCommand(String service, String account) {
    return "security add-generic-password -s %s -a %s -w".formatted(service, account);
  }

  static CommandResult runProcess(List<String> command) {
    try {
      Process process = new ProcessBuilder(command).redirectErrorStream(false).start();
      byte[] stdout = process.getInputStream().readAllBytes();
      process.getErrorStream().readAllBytes();
      return new CommandResult(process.waitFor(), new String(stdout, StandardCharsets.UTF_8));
    } catch (IOException e) {
      return new CommandResult(1, "");
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return new CommandResult(1, "");
    }
  }
}
