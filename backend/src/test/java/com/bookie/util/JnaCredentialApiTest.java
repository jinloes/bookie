package com.bookie.util;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.jna.Native;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

// The advapi32 calls only exist on Windows, so only the struct layout is checked elsewhere.
class JnaCredentialApiTest {

  @Test
  void credentialStructMatchesTheNativeCredentialwLayout() {
    int expected = Native.POINTER_SIZE == 8 ? 80 : 52;

    assertThat(new JnaCredentialApi.Credential().size()).isEqualTo(expected);
  }

  @Test
  @EnabledOnOs(OS.WINDOWS)
  void roundTripsAGenericCredential() {
    JnaCredentialApi api = new JnaCredentialApi();
    // A fixed target is overwritten on each run instead of accumulating test items.
    String target = "com.bookie.test:round-trip";
    byte[] secret = "pw".getBytes(StandardCharsets.UTF_16LE);

    assertThat(api.write(target, "test", secret)).isTrue();
    assertThat(api.read(target)).hasValueSatisfying(value -> assertThat(value).isEqualTo(secret));
  }

  @Test
  @EnabledOnOs(OS.WINDOWS)
  void returnsEmptyForAMissingCredential() {
    assertThat(new JnaCredentialApi().read("com.bookie.test:missing:" + System.nanoTime()))
        .isEmpty();
  }
}
