package com.bookie.integrations.documents;

import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;

/**
 * Extracts plain text from HTML email bodies, stripping non-visible markup and decoding entities.
 */
public final class HtmlTextExtractor {

  private HtmlTextExtractor() {}

  public static String extractPlainText(String html) {
    if (StringUtils.isBlank(html)) {
      return "";
    }
    return Jsoup.parse(html).text();
  }
}
