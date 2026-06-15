package com.bookie.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@Tag("llm")
@EnabledIfEnvironmentVariable(named = "BOOKIE_LLM_TESTS", matches = "true")
@SpringBootTest(
    properties = {
      "ai.provider=copilot",
      "ai.model.vision=gpt-5-mini",
    })
class DocumentTextExtractorLlmTest {

  @Autowired private DocumentTextExtractorService pdfExtractorService;

  @Test
  @Timeout(value = 2, unit = TimeUnit.MINUTES)
  void ocrsGeneratedJpegReceiptWithRealLlm() throws Exception {
    String text =
        pdfExtractorService.extractText(receiptJpegBytes(), "Scan from 2026-06-14 09_32_33 PM.jpg");

    assertThat(text).containsIgnoringCase("home depot");
    assertThat(text).contains("7.35");
  }

  private byte[] receiptJpegBytes() throws Exception {
    BufferedImage image = new BufferedImage(1200, 1800, BufferedImage.TYPE_INT_RGB);
    Graphics2D graphics = image.createGraphics();
    try {
      graphics.setRenderingHint(
          RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
      graphics.setColor(Color.WHITE);
      graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
      graphics.setColor(Color.BLACK);

      graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 72));
      graphics.drawString("HOME DEPOT", 130, 180);

      graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 44));
      graphics.drawString("43900 ICEHOUSE TERRACE FREMONT, CA 94538", 120, 280);
      graphics.drawString("06/14/26 01:05 PM", 120, 380);
      graphics.drawString("14X30X1 HDX FPR 5 FILTR", 120, 480);
      graphics.drawString("SUBTOTAL 6.67", 120, 620);
      graphics.drawString("SALES TAX 0.68", 120, 700);
      graphics.drawString("TOTAL 7.35", 120, 780);
      graphics.drawString("VISA XXXX2108", 120, 880);
    } finally {
      graphics.dispose();
    }

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ImageIO.write(image, "jpg", out);
    return out.toByteArray();
  }
}
