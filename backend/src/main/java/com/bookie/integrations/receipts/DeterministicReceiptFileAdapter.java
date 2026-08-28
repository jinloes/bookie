package com.bookie.integrations.receipts;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

@Component
public class DeterministicReceiptFileAdapter implements ReceiptFilePort {

  @Override
  public ReceiptFileWriteResult write(Path root, String relativePath, byte[] content)
      throws IOException {
    if (root == null || content == null) {
      throw new IllegalArgumentException("Receipt file root and content are required");
    }
    Path canonicalRoot = canonicalRoot(root);
    Path relative = validatedRelativePath(relativePath);
    Path target = canonicalRoot.resolve(relative).normalize();
    if (!target.startsWith(canonicalRoot)) {
      throw new IllegalArgumentException("Receipt file target escapes its configured root");
    }
    createSafeDirectories(canonicalRoot, target.getParent());

    String expectedSha256 = sha256(content);
    if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
      return compareExisting(target, expectedSha256);
    }

    Path temporary =
        Files.createTempFile(
            target.getParent(), "." + target.getFileName().toString() + "-", ".pending");
    try {
      Files.write(
          temporary,
          content,
          StandardOpenOption.WRITE,
          StandardOpenOption.TRUNCATE_EXISTING,
          StandardOpenOption.SYNC);
      try {
        moveWithoutReplacement(temporary, target);
      } catch (FileAlreadyExistsException e) {
        return compareExisting(target, expectedSha256);
      }
      return new ReceiptFileWriteResult(
          target, ReceiptFileWriteResult.Status.CREATED, expectedSha256, expectedSha256);
    } finally {
      Files.deleteIfExists(temporary);
    }
  }

  private static Path canonicalRoot(Path root) throws IOException {
    Path absolute = root.toAbsolutePath().normalize();
    if (Files.isSymbolicLink(absolute) || !Files.isDirectory(absolute, LinkOption.NOFOLLOW_LINKS)) {
      throw new IllegalArgumentException(
          "Receipt file root must be an existing non-symbolic-link directory");
    }
    return absolute.toRealPath(LinkOption.NOFOLLOW_LINKS);
  }

  private static Path validatedRelativePath(String relativePath) {
    if (StringUtils.isBlank(relativePath)) {
      throw new IllegalArgumentException("Receipt file path must not be blank");
    }
    Path relative = Path.of(relativePath).normalize();
    if (relative.isAbsolute()
        || relative.getNameCount() == 0
        || relative.startsWith("..")
        || relative.toString().equals(".")) {
      throw new IllegalArgumentException("Receipt file path must stay under its configured root");
    }
    return relative;
  }

  private static void createSafeDirectories(Path root, Path parent) throws IOException {
    Path current = root;
    Path relativeParent = root.relativize(parent);
    for (Path segment : relativeParent) {
      current = current.resolve(segment);
      if (Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
        if (Files.isSymbolicLink(current)
            || !Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS)) {
          throw new IllegalArgumentException(
              "Receipt file target contains a non-directory or symbolic-link ancestor");
        }
      } else {
        Files.createDirectory(current);
      }
    }
  }

  private static ReceiptFileWriteResult compareExisting(Path target, String expectedSha256)
      throws IOException {
    if (Files.isSymbolicLink(target) || !Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
      throw new IllegalArgumentException(
          "Receipt file target exists but is not a regular non-symbolic-link file");
    }
    String actualSha256;
    try (InputStream input = Files.newInputStream(target)) {
      actualSha256 = sha256(input);
    }
    ReceiptFileWriteResult.Status status =
        expectedSha256.equals(actualSha256)
            ? ReceiptFileWriteResult.Status.ALREADY_PRESENT
            : ReceiptFileWriteResult.Status.CONFLICT;
    return new ReceiptFileWriteResult(target, status, expectedSha256, actualSha256);
  }

  private static void moveWithoutReplacement(Path source, Path target) throws IOException {
    // Do not request ATOMIC_MOVE here: providers may replace an existing target when that option
    // is used because the target-exists behavior is implementation-specific. A plain move is
    // explicitly non-replacing unless REPLACE_EXISTING is requested.
    Files.move(source, target);
  }

  private static String sha256(byte[] content) {
    return HexFormat.of().formatHex(messageDigest().digest(content));
  }

  private static String sha256(InputStream input) throws IOException {
    MessageDigest digest = messageDigest();
    byte[] buffer = new byte[8192];
    int read;
    while ((read = input.read(buffer)) != -1) {
      digest.update(buffer, 0, read);
    }
    return HexFormat.of().formatHex(digest.digest());
  }

  private static MessageDigest messageDigest() {
    try {
      return MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }
}
