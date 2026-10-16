package com.bookie.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class WindowsCredentialStoreTest {

  private final FakeCredentialApi api = new FakeCredentialApi();
  private final WindowsCredentialStore store = new WindowsCredentialStore(api);

  @Nested
  class Read {

    @Test
    void decodesTheUtf16SecretStoredUnderServiceAndAccount() {
      api.items.put("svc:acct", " s3cret\n".getBytes(StandardCharsets.UTF_16LE));

      assertThat(store.read("svc", "acct")).contains("s3cret");
    }

    @Test
    void returnsEmptyWhenTheItemIsMissing() {
      assertThat(store.read("svc", "acct")).isEmpty();
    }

    @Test
    void returnsEmptyWhenTheSecretIsBlank() {
      api.items.put("svc:acct", "  ".getBytes(StandardCharsets.UTF_16LE));

      assertThat(store.read("svc", "acct")).isEmpty();
    }

    @Test
    void returnsEmptyWhenTheNativeCallFails() {
      api.failure = new IllegalStateException("boom");

      assertThat(store.read("svc", "acct")).isEmpty();
    }

    @Test
    void returnsEmptyWhenTheNativeLibraryCannotLoad() {
      api.failure = new UnsatisfiedLinkError("no advapi32");

      assertThat(store.read("svc", "acct")).isEmpty();
    }
  }

  @Nested
  class Write {

    @Test
    void storesTheSecretAsUtf16UnderServiceAndAccount() {
      assertThat(store.write("svc", "acct", "pw")).isTrue();

      assertThat(api.lastUserName).isEqualTo("acct");
      assertThat(api.lastWritten).isEqualTo("pw".getBytes(StandardCharsets.UTF_16LE));
    }

    @Test
    void wipesTheEncodedSecretAfterWriting() {
      store.write("svc", "acct", "pw");

      assertThat(api.items.get("svc:acct")).containsOnly((byte) 0);
    }

    @Test
    void reportsANativeFailure() {
      api.writeSucceeds = false;

      assertThat(store.write("svc", "acct", "pw")).isFalse();
    }

    @Test
    void returnsFalseWhenTheNativeCallThrows() {
      api.failure = new UnsatisfiedLinkError("no advapi32");

      assertThat(store.write("svc", "acct", "pw")).isFalse();
    }

    @Test
    void refusesAnEmptySecret() {
      assertThat(store.write("svc", "acct", "")).isFalse();
      assertThat(api.items).isEmpty();
    }
  }

  @Test
  void describesCredentialManagerAndThePromptingCmdkeyCommand() {
    assertThat(store.isAvailable()).isTrue();
    assertThat(store.displayName()).isEqualTo("Windows Credential Manager");
    assertThat(store.storeCommand("svc", "acct"))
        .isEqualTo("cmdkey /generic:svc:acct /user:acct /pass");
  }

  @Test
  void defaultConstructorDefersLoadingTheNativeLibrary() {
    assertThat(new WindowsCredentialStore().isAvailable()).isTrue();
  }

  private static final class FakeCredentialApi implements WindowsCredentialStore.CredentialApi {
    final Map<String, byte[]> items = new HashMap<>();
    boolean writeSucceeds = true;
    Throwable failure;
    String lastUserName;
    byte[] lastWritten;

    @Override
    public Optional<byte[]> read(String targetName) {
      throwIfFailing();
      return Optional.ofNullable(items.get(targetName));
    }

    @Override
    public boolean write(String targetName, String userName, byte[] secret) {
      throwIfFailing();
      lastUserName = userName;
      lastWritten = secret.clone();
      items.put(targetName, secret);
      return writeSucceeds;
    }

    private void throwIfFailing() {
      if (failure instanceof RuntimeException runtime) {
        throw runtime;
      }
      if (failure instanceof Error error) {
        throw error;
      }
    }
  }
}
