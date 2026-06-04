package com.bookie.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PdfExtractorServiceTest {

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private CopilotLlmService copilotLlmService;

  private PdfExtractorService service;

  @BeforeEach
  void setUp() {
    service = new PdfExtractorService(copilotLlmService);
    ReflectionTestUtils.setField(service, "visionModel", "test-vision-model");
  }

  @Nested
  class ExtractText {

    @Test
    void returnsEmptyForNullInput() {
      assertThat(service.extractText(null)).isEmpty();
      verifyNoInteractions(copilotLlmService);
    }

    @Test
    void returnsEmptyForEmptyInput() {
      assertThat(service.extractText(new byte[0])).isEmpty();
      verifyNoInteractions(copilotLlmService);
    }

    @Test
    void returnsTextLayerContentWithoutCallingOcr() throws Exception {
      assertThat(service.extractText(pdfWithText("Hello World"))).contains("Hello World");
      verifyNoInteractions(copilotLlmService);
    }

    @Test
    void callsOcrWhenPdfHasNoTextLayer() throws Exception {
      when(copilotLlmService.completeVision(any(CopilotVisionRequest.class)))
          .thenReturn("Extracted receipt text");
      assertThat(service.extractText(pdfWithNoText())).isEqualTo("Extracted receipt text");
    }

    @Test
    void returnsEmptyWhenOcrReturnsBlank() throws Exception {
      when(copilotLlmService.completeVision(any(CopilotVisionRequest.class))).thenReturn("   ");
      assertThat(service.extractText(pdfWithNoText())).isEmpty();
    }

    @Test
    void returnsEmptyWhenOcrThrows() throws Exception {
      when(copilotLlmService.completeVision(any(CopilotVisionRequest.class)))
          .thenThrow(new RuntimeException("model unavailable"));
      assertThat(service.extractText(pdfWithNoText())).isEmpty();
    }

    @Test
    void concatenatesMultiPageOcrWithBlankLineSeparator() throws Exception {
      when(copilotLlmService.completeVision(any(CopilotVisionRequest.class)))
          .thenReturn("Page one text")
          .thenReturn("Page two text");
      assertThat(service.extractText(pdfWithNoTextPages(2)))
          .isEqualTo("Page one text\n\nPage two text");
    }

    @Test
    void returnsEmptyForInvalidPdfBytes() {
      assertThat(service.extractText(new byte[] {0x00, 0x01, 0x02})).isEmpty();
      verifyNoInteractions(copilotLlmService);
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
}
