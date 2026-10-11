package com.bookie.datalifecycle.backup;

import com.bookie.util.WriteFreezeState;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * The cutover write freeze. A flag file in the data directory keeps it on across restarts until a
 * portable restore commits or the user lifts it. While frozen, every state-changing API call except
 * backup and restore is answered with 423 Locked.
 */
@Slf4j
@Component
public class WriteFreeze extends OncePerRequestFilter implements WriteFreezeState {

  static final String FLAG_FILE = "write-freeze";
  static final String LOCKED_BODY =
      """
      {"message":"Bookie is frozen for a database migration. Finish the restore or lift the \
      freeze on the Backup page."}""";

  private static final Set<String> READ_METHODS = Set.of("GET", "HEAD", "OPTIONS");

  private final Path flagFile;
  private volatile boolean frozen;

  public WriteFreeze(@Value("${bookie.data-dir}") Path dataDirectory) {
    this.flagFile = dataDirectory.resolve(FLAG_FILE);
    this.frozen = Files.exists(flagFile);
  }

  @Override
  public boolean isFrozen() {
    return frozen;
  }

  /** Turns the freeze on and persists it. */
  public synchronized void freeze() {
    try {
      Files.createDirectories(flagFile.getParent());
      Files.writeString(flagFile, "frozen");
    } catch (IOException e) {
      throw new UncheckedIOException("Could not persist the write freeze", e);
    }
    frozen = true;
    log.warn("Write freeze enabled");
  }

  /** Turns the freeze off and removes the persisted flag. */
  public synchronized void lift() {
    try {
      Files.deleteIfExists(flagFile);
    } catch (IOException e) {
      throw new UncheckedIOException("Could not remove the write freeze flag", e);
    }
    frozen = false;
    log.info("Write freeze lifted");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    if (!frozen || isAllowedWhileFrozen(request)) {
      filterChain.doFilter(request, response);
      return;
    }
    response.setStatus(HttpStatus.LOCKED.value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.getWriter().write(LOCKED_BODY);
  }

  private static boolean isAllowedWhileFrozen(HttpServletRequest request) {
    String path = request.getRequestURI();
    return READ_METHODS.contains(request.getMethod())
        || path.equals("/api/backup")
        || path.startsWith("/api/backup/")
        || path.equals("/api/backups")
        || path.startsWith("/api/backups/");
  }
}
