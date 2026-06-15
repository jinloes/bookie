package com.bookie.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class DocumentTextExtractorServiceTest {

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private LlmGateway llmGateway;

  private DocumentTextExtractorService service;

  @BeforeEach
  void setUp() {
    service = new DocumentTextExtractorService(llmGateway);
    ReflectionTestUtils.setField(service, "visionModel", "test-vision-model");
  }

  @Nested
  class ExtractText {

    @Test
    void returnsEmptyForNullInput() {
      assertThat(service.extractText(null)).isEmpty();
      verifyNoInteractions(llmGateway);
    }

    @Test
    void returnsEmptyForEmptyInput() {
      assertThat(service.extractText(new byte[0])).isEmpty();
      verifyNoInteractions(llmGateway);
    }

    @Test
    void returnsTextLayerContentWithoutCallingOcr() throws Exception {
      assertThat(service.extractText(pdfWithText("Hello World"))).contains("Hello World");
      verifyNoInteractions(llmGateway);
    }

    @Test
    void callsOcrWhenPdfHasNoTextLayer() throws Exception {
      when(llmGateway.completeVision(any(LlmVisionRequest.class)))
          .thenReturn("Extracted receipt text");
      assertThat(service.extractText(pdfWithNoText())).isEqualTo("Extracted receipt text");
    }

    @Test
    void returnsEmptyWhenOcrReturnsBlank() throws Exception {
      when(llmGateway.completeVision(any(LlmVisionRequest.class))).thenReturn("   ");
      assertThat(service.extractText(pdfWithNoText())).isEmpty();
    }

    @Test
    void returnsEmptyWhenOcrThrows() throws Exception {
      when(llmGateway.completeVision(any(LlmVisionRequest.class)))
          .thenThrow(new RuntimeException("model unavailable"));
      assertThat(service.extractText(pdfWithNoText())).isEmpty();
    }

    @Test
    void ocrsImageFilesDirectly() throws Exception {
      when(llmGateway.completeVision(any(LlmVisionRequest.class))).thenReturn("Receipt text");

      assertThat(service.extractText(jpegBytes(), "Scan from 2026-06-14 09_32_33 PM.jpg"))
          .isEqualTo("Receipt text");

      ArgumentCaptor<LlmVisionRequest> captor = ArgumentCaptor.forClass(LlmVisionRequest.class);
      verify(llmGateway).completeVision(captor.capture());
      assertThat(captor.getValue().mimeType()).isEqualTo("image/jpeg");
      assertThat(captor.getValue().displayName()).isEqualTo("Scan from 2026-06-14 09_32_33 PM.jpg");
    }

    @Test
    void concatenatesMultiPageOcrWithBlankLineSeparator() throws Exception {
      when(llmGateway.completeVision(any(LlmVisionRequest.class)))
          .thenReturn("Page one text")
          .thenReturn("Page two text");
      assertThat(service.extractText(pdfWithNoTextPages(2)))
          .isEqualTo("Page one text\n\nPage two text");
    }

    @Test
    void returnsEmptyForInvalidPdfBytes() {
      assertThat(service.extractText(new byte[] {0x00, 0x01, 0x02})).isEmpty();
      verifyNoInteractions(llmGateway);
    }
  }

  private byte[] pdfWithText(String text) throws Exception {
    try (PDDocument doc = new PDDocument()) {
      PDPage page = new PDPage();
      doc.addPage(page);
      try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
        cs.beginText();
        cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
        cs.newLineAtOffset(100, 700);
        cs.showText(text);
        cs.endText();
      }
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      doc.save(out);
      return out.toByteArray();
    }
  }

  private byte[] pdfWithNoText() throws Exception {
    return pdfWithNoTextPages(1);
  }

  private byte[] pdfWithNoTextPages(int pageCount) throws Exception {
    try (PDDocument doc = new PDDocument()) {
      for (int i = 0; i < pageCount; i++) {
        doc.addPage(new PDPage());
      }
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      doc.save(out);
      return out.toByteArray();
    }
  }

  private byte[] jpegBytes() throws Exception {
    BufferedImage image = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
    Graphics2D graphics = image.createGraphics();
    try {
      graphics.setColor(Color.WHITE);
      graphics.fillRect(0, 0, 10, 10);
    } finally {
      graphics.dispose();
    }
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ImageIO.write(image, "jpg", out);
    return out.toByteArray();
  }
}
