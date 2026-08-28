package com.bookie.integrations.receipts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DeterministicReceiptFileAdapterTest {

  private final DeterministicReceiptFileAdapter adapter = new DeterministicReceiptFileAdapter();

  @TempDir Path root;

  @Test
  void createsANewFileBelowTheConfiguredRoot() throws Exception {
    byte[] content = "receipt".getBytes(StandardCharsets.UTF_8);

    ReceiptFileWriteResult result = adapter.write(root, "2026/receipt.pdf", content);

    assertThat(result.status()).isEqualTo(ReceiptFileWriteResult.Status.CREATED);
    assertThat(result.expectedSha256()).isEqualTo(result.actualSha256());
    assertThat(result.path()).isEqualTo(root.resolve("2026/receipt.pdf"));
    assertThat(Files.readAllBytes(result.path())).isEqualTo(content);
  }

  @Test
  void reportsAlreadyPresentWhenTheExistingContentMatches() throws Exception {
    byte[] content = "receipt".getBytes(StandardCharsets.UTF_8);
    adapter.write(root, "receipt.pdf", content);

    ReceiptFileWriteResult result = adapter.write(root, "receipt.pdf", content);

    assertThat(result.status()).isEqualTo(ReceiptFileWriteResult.Status.ALREADY_PRESENT);
    assertThat(result.expectedSha256()).isEqualTo(result.actualSha256());
  }

  @Test
  void reportsConflictWithoutOverwritingDifferentExistingContent() throws Exception {
    byte[] original = "original financial record".getBytes(StandardCharsets.UTF_8);
    byte[] replacement = "different receipt".getBytes(StandardCharsets.UTF_8);
    Files.write(root.resolve("receipt.pdf"), original);

    ReceiptFileWriteResult result = adapter.write(root, "receipt.pdf", replacement);

    assertThat(result.status()).isEqualTo(ReceiptFileWriteResult.Status.CONFLICT);
    assertThat(result.expectedSha256()).isNotEqualTo(result.actualSha256());
    assertThat(Files.readAllBytes(root.resolve("receipt.pdf"))).isEqualTo(original);
  }

  @Test
  void rejectsPathTraversal() {
    assertThatThrownBy(() -> adapter.write(root, "../receipt.pdf", new byte[] {1}))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("configured root");
  }

  @Test
  void rejectsSymbolicLinkAncestors() throws Exception {
    Path outside = Files.createTempDirectory(root.getParent(), "receipt-outside-");
    try {
      Files.createSymbolicLink(root.resolve("linked"), outside);

      assertThatThrownBy(() -> adapter.write(root, "linked/receipt.pdf", new byte[] {1}))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("symbolic-link ancestor");
      assertThat(Files.exists(outside.resolve("receipt.pdf"))).isFalse();
    } finally {
      Files.deleteIfExists(root.resolve("linked"));
      Files.deleteIfExists(outside);
    }
  }
}
