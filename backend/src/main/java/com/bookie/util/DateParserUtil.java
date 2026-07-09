package com.bookie.util;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;

/** Utility for parsing dates from various formats. */
public final class DateParserUtil {

  private static final List<DateTimeFormatter> DATE_FORMATS =
      List.of(
          DateTimeFormatter.ISO_LOCAL_DATE,
          DateTimeFormatter.ofPattern("MM/dd/yyyy", Locale.US),
          DateTimeFormatter.ofPattern("M/d/yyyy", Locale.US),
          DateTimeFormatter.ofPattern("MM-dd-yyyy", Locale.US),
          caseInsensitive("MMMM d, yyyy"),
          caseInsensitive("MMM d, yyyy"));

  private DateParserUtil() {}

  /**
   * Parse a date string using multiple supported formats.
   *
   * @param dateStr the date string to parse
   * @return the parsed date as ISO 8601 string (YYYY-MM-DD), or null if parsing fails
   */
  public static String parseDateAsIsoString(String dateStr) {
    if (StringUtils.isBlank(dateStr)) {
      return null;
    }
    return parseDate(dateStr.trim()).map(LocalDate::toString).orElse(null);
  }

  /**
   * Parse a date string using multiple supported formats.
   *
   * @param dateStr the date string to parse
   * @return an Optional containing the parsed LocalDate, or empty if parsing fails
   */
  public static Optional<LocalDate> parseDate(String dateStr) {
    if (StringUtils.isBlank(dateStr)) {
      return Optional.empty();
    }
    String trimmed = dateStr.trim();
    for (DateTimeFormatter fmt : DATE_FORMATS) {
      try {
        return Optional.of(LocalDate.parse(trimmed, fmt));
      } catch (DateTimeParseException ignored) {
      }
    }
    return Optional.empty();
  }

  private static DateTimeFormatter caseInsensitive(String pattern) {
    return new DateTimeFormatterBuilder()
        .parseCaseInsensitive()
        .appendPattern(pattern)
        .toFormatter(Locale.US);
  }
}
