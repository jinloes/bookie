package com.bookie.service;

import com.sun.jna.NativeLibrary;
import java.awt.image.BufferedImage;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import net.sourceforge.tess4j.Tesseract;
import org.apache.commons.lang3.StringUtils;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

/** Extracts plain text from PDF bytes using PDFBox, with Tesseract OCR fallback. */
@Slf4j
@Service
public class PdfExtractorService {

  // JNA library paths are registered once per JVM; volatile for safe lazy init across threads.
  private static volatile boolean jnaPathsRegistered = false;

  public String extractText(byte[] pdfBytes) {
    if (pdfBytes == null || pdfBytes.length == 0) {
      return "";
    }
    try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
      String text = new PDFTextStripper().getText(doc).trim();
      if (!text.isBlank()) {
        return text;
      }
      // No text layer — PDF is likely a scanned image; fall back to OCR
      log.info("PDF has no text layer, falling back to OCR");
      return ocrPdf(doc);
    } catch (Exception e) {
      log.warn("Failed to extract text from PDF: {}", e.getMessage());
      return "";
    }
  }

  private String ocrPdf(PDDocument doc) {
    try {
      ensureJnaLibraryPath();
      PDFRenderer renderer = new PDFRenderer(doc);
      Tesseract tesseract = new Tesseract();
      tesseract.setDatapath(resolveTessDataPath());
      tesseract.setLanguage("eng");
      StringBuilder result = new StringBuilder();
      for (int i = 0; i < doc.getNumberOfPages(); i++) {
        BufferedImage image = renderer.renderImageWithDPI(i, 300);
        String pageText = tesseract.doOCR(image).trim();
        if (!pageText.isBlank()) {
          if (!result.isEmpty()) {
            result.append("\n\n");
          }
          result.append(pageText);
        }
      }
      log.info("OCR extracted {} characters from PDF", result.length());
      return result.toString();
    } catch (Exception e) {
      log.warn("OCR failed: {}", e.getMessage());
      return "";
    }
  }

  /**
   * Registers Homebrew lib directories with JNA so it can locate libtesseract.dylib. Uses
   * NativeLibrary.addSearchPath rather than the jna.library.path system property because the latter
   * must be set before the JVM starts when loaded via IntelliJ; addSearchPath works at any point
   * before the first library load.
   */
  private static void ensureJnaLibraryPath() {
    if (jnaPathsRegistered) {
      return;
    }
    String os = System.getProperty("os.name", "").toLowerCase();
    if (!os.contains("mac")) {
      jnaPathsRegistered = true;
      return;
    }
    String arch = System.getProperty("os.arch", "").toLowerCase();
    List<String> candidates =
        arch.contains("aarch64")
            ? List.of("/opt/homebrew/lib", "/opt/homebrew/opt/tesseract/lib")
            : List.of("/usr/local/lib", "/usr/local/opt/tesseract/lib");
    for (String path : candidates) {
      if (new java.io.File(path).isDirectory()) {
        NativeLibrary.addSearchPath("tesseract", path);
        NativeLibrary.addSearchPath("leptonica", path);
        log.info("Registered JNA search path for tesseract/leptonica: {}", path);
      }
    }
    jnaPathsRegistered = true;
  }

  private static String resolveTessDataPath() {
    String envPath = System.getenv("TESSDATA_PREFIX");
    if (StringUtils.isNotBlank(envPath)) {
      return envPath;
    }
    for (String path :
        List.of(
            "/opt/homebrew/share/tessdata", "/usr/local/share/tessdata", "/usr/share/tessdata")) {
      if (new java.io.File(path).isDirectory()) {
        return path;
      }
    }
    return "tessdata"; // relative fallback (tessdata/ in working directory)
  }
}
