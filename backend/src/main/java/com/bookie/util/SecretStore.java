package com.bookie.util;

import java.util.Locale;
import java.util.Optional;

/**
 * The operating system's store for small secrets such as the SQL Server password and the backup
 * key. Implementations never log secrets or include them in exception messages.
 */
public interface SecretStore {

  /** Whether this store can be used on the running machine. */
  boolean isAvailable();

  /** Returns the stored secret, or empty when the item is missing or the store is absent. */
  Optional<String> read(String service, String account);

  /** Creates or updates the item; returns false when the store is absent or the write fails. */
  boolean write(String service, String account, String secret);

  /** Name shown to the user in error messages, e.g. {@code macOS Keychain}. */
  String displayName();

  /** A command the user can run to store the item; it prompts for the secret. */
  String storeCommand(String service, String account);

  /** The store for the running operating system. */
  static SecretStore forCurrentOs() {
    return forOs(System.getProperty("os.name", ""));
  }

  static SecretStore forOs(String osName) {
    if (osName.toLowerCase(Locale.ROOT).startsWith("windows")) {
      return new WindowsCredentialStore();
    }
    return new MacKeychain(MacKeychain::runProcess, osName);
  }
}
