package com.bookie.service;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeTypeUtils;

/** Extracts plain text from PDF bytes using PDFBox, with vision-model OCR fallback. */
@Slf4j
@Service
public class PdfExtractorService {

  private final ChatClient chatClient;

  public PdfExtractorService(@Qualifier("ocrChatClient") ChatClient chatClient) {
    this.chatClient = chatClient;
  }

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
      PDFRenderer renderer = new PDFRenderer(doc);
      List<String> pageTexts = new ArrayList<>();
      for (int i = 0; i < doc.getNumberOfPages(); i++) {
        BufferedImage image = renderer.renderImageWithDPI(i, 300);
        try {
          ByteArrayOutputStream baos = new ByteArrayOutputStream();
          ImageIO.write(image, "PNG", baos);
          byte[] imageBytes = baos.toByteArray();
          long ocrStart = System.currentTimeMillis();
          String pageText =
              chatClient
                  .prompt()
                  .system(
                      "You are an OCR engine. Output only the extracted text. "
                          + "Do not describe the image, add commentary, or include any text not present in the image.")
                  .user(
                      u ->
                          u.text(
                                  """
                                  Extract all text from this image exactly as it appears.
                                  For tables and line items, keep each row on one line with values separated by spaces.
                                  If text is unclear or partially legible, output your best reading followed by [?].
                                  /no_think
                                  """)
                              .media(MimeTypeUtils.IMAGE_PNG, new ByteArrayResource(imageBytes)))
                  .messages(List.of(new AssistantMessage("<think>\n\n</think>")))
                  .call()
                  .content();
          log.info("LLM [ocr page {}]: {}ms", i + 1, System.currentTimeMillis() - ocrStart);
          if (StringUtils.isNotBlank(pageText)) {
            pageTexts.add(pageText.trim());
          }
        } finally {
          image.flush();
        }
      }
      String result = String.join("\n\n", pageTexts);
      log.info("OCR extracted {} characters from PDF", result.length());
      return result;
    } catch (Exception e) {
      log.warn("OCR failed: {}", e.getMessage());
      return "";
    }
  }
}
