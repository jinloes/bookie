package com.bookie.integrations.receipts;

import java.io.IOException;
import java.nio.file.Path;

public interface ReceiptFilePort {

  ReceiptFileWriteResult write(Path root, String relativePath, byte[] content) throws IOException;
}
