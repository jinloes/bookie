package com.bookie.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SecretStoreTest {

  @Nested
  class ForOs {

    @Test
    void usesCredentialManagerOnWindows() {
      assertThat(SecretStore.forOs("Windows 11")).isInstanceOf(WindowsCredentialStore.class);
    }

    @Test
    void usesTheKeychainOnMacOs() {
      SecretStore store = SecretStore.forOs("Mac OS X");

      assertThat(store).isInstanceOf(MacKeychain.class);
      assertThat(store.isAvailable()).isTrue();
    }

    @Test
    void hasNoUsableStoreElsewhere() {
      assertThat(SecretStore.forOs("Linux").isAvailable()).isFalse();
    }
  }

  @Test
  void forCurrentOsMatchesTheRunningOs() {
    assertThat(SecretStore.forCurrentOs().getClass())
        .isEqualTo(SecretStore.forOs(System.getProperty("os.name", "")).getClass());
  }
}
