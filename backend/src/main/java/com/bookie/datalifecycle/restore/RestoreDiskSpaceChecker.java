package com.bookie.datalifecycle.restore;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.stereotype.Component;

@Component
public class RestoreDiskSpaceChecker {

  public void requireAvailable(Path dataDirectory, long requiredBytes) throws IOException {
    long available = Files.getFileStore(dataDirectory).getUsableSpace();
    if (available < requiredBytes) {
      throw new IOException(
          "Insufficient disk space for shadow restore: required "
              + requiredBytes
              + " bytes, available "
              + available
              + " bytes");
    }
  }
}
