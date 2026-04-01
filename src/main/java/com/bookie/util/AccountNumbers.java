package com.bookie.util;

import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

public final class AccountNumbers {

  // Leading asterisks and spaces are mask characters added by billers (e.g. "******4191-6")
  private static final Pattern LEADING_MASK = Pattern.compile("^[* ]+");

  private AccountNumbers() {}

  /**
   * Strips leading mask characters, lowercases, and trims each account number. Removes blank
   * entries. Safe to call with a null list.
   */
  public static List<String> normalize(Collection<String> accounts) {
    return CollectionUtils.emptyIfNull(accounts).stream()
        .map(a -> LEADING_MASK.matcher(a.toLowerCase().trim()).replaceFirst(""))
        .filter(StringUtils::isNotBlank)
        .toList();
  }
}
