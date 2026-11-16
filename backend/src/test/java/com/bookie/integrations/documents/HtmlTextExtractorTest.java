package com.bookie.integrations.documents;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class HtmlTextExtractorTest {

  @Nested
  class ExtractPlainText {

    @Test
    void returnsEmptyStringForNullOrBlankInput() {
      assertThat(HtmlTextExtractor.extractPlainText(null)).isEmpty();
      assertThat(HtmlTextExtractor.extractPlainText("   ")).isEmpty();
    }

    @Test
    void stripsStyleAndScriptBlocksAndDecodesHtmlEntities() {
      String html =
          """
          <html>
            <head><style>body { color: #333; }</style><script>window.x = 1;</script></head>
            <body>
              <p>Total &amp; Fees:&nbsp;&#36;125.00</p>
            </body>
          </html>
          """;

      assertThat(HtmlTextExtractor.extractPlainText(html)).isEqualTo("Total & Fees: $125.00");
    }
  }
}
