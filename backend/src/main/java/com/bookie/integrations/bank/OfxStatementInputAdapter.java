package com.bookie.integrations.bank;

import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

/**
 * Reads OFX 1.x (SGML), OFX 2.x (XML) and QFX bank statements with a lexical tokenizer rather than
 * an XML parser, so DTDs and entities are never processed (no XXE or entity expansion).
 */
@Component
public class OfxStatementInputAdapter implements BankStatementInputPort {

  private static final Pattern OFX_ROOT = Pattern.compile("(?i)<OFX[\\s>]");
  private static final Pattern CHARSET_1252 =
      Pattern.compile("(?im)^\\s*CHARSET\\s*:\\s*1252\\s*$");
  private static final Pattern ESCAPE = Pattern.compile("&(amp|lt|gt);");
  private static final Pattern POSTED_DATE = Pattern.compile("^(\\d{8})");
  private static final Charset WINDOWS_1252 = Charset.forName("windows-1252");
  private static final DateTimeFormatter OFX_DATE = DateTimeFormatter.BASIC_ISO_DATE;
  private static final String SOURCE_ID_PREFIX = "ofx:";

  @Override
  public BankStatement parse(byte[] content) {
    if (content == null) {
      throw new BankInputException("Bank statement is required.");
    }
    String text = decode(content);
    Matcher root = OFX_ROOT.matcher(text);
    if (!root.find()) {
      throw new BankInputException("Not an OFX bank statement.");
    }
    return readStatements(tokenize(text.substring(root.start())));
  }

  private static String decode(byte[] content) {
    String utf8 = new String(content, StandardCharsets.UTF_8);
    Matcher root = OFX_ROOT.matcher(utf8);
    String header = root.find() ? utf8.substring(0, root.start()) : utf8;
    return CHARSET_1252.matcher(header).find() ? new String(content, WINDOWS_1252) : utf8;
  }

  private static List<Token> tokenize(String text) {
    List<Token> tokens = new ArrayList<>();
    int position = text.indexOf('<');
    while (position >= 0) {
      int tagEnd = text.indexOf('>', position);
      if (tagEnd < 0) {
        break;
      }
      int next = text.indexOf('<', tagEnd);
      String rawTag = text.substring(position + 1, tagEnd).trim();
      String value = text.substring(tagEnd + 1, next < 0 ? text.length() : next).trim();
      // Declarations (<!DOCTYPE>, <!ENTITY>, comments) and processing instructions are ignored.
      if (!rawTag.isEmpty() && rawTag.charAt(0) != '!' && rawTag.charAt(0) != '?') {
        boolean closing = rawTag.charAt(0) == '/';
        String name = StringUtils.substringBefore(closing ? rawTag.substring(1) : rawTag, " ");
        tokens.add(new Token(name.trim().toUpperCase(Locale.ROOT), closing, value));
      }
      position = next;
    }
    return tokens;
  }

  private static BankStatement readStatements(List<Token> tokens) {
    boolean foundStatement = false;
    boolean inStatement = false;
    boolean inAccount = false;
    String bankId = "";
    String accountId = "";
    Map<String, String> transaction = null;
    int totalRows = 0;
    int invalidRows = 0;
    List<BankTransaction> transactions = new ArrayList<>();

    for (Token token : tokens) {
      switch (token.name()) {
        case "STMTRS" -> {
          if (token.closing()) {
            inStatement = false;
          } else {
            foundStatement = true;
            inStatement = true;
            bankId = "";
            accountId = "";
          }
          transaction = null;
        }
        case "BANKACCTFROM" -> inAccount = inStatement && !token.closing();
        case "STMTTRN" -> {
          if (inStatement && !token.closing()) {
            transaction = new HashMap<>();
          } else if (inStatement && transaction != null) {
            totalRows++;
            BankTransaction parsed = toTransaction(bankId, accountId, transaction);
            if (parsed == null) {
              invalidRows++;
            } else {
              transactions.add(parsed);
            }
            transaction = null;
          }
        }
        default -> {
          if (token.closing()) {
            continue;
          }
          if (inAccount && "BANKID".equals(token.name())) {
            bankId = token.value();
          } else if (inAccount && "ACCTID".equals(token.name())) {
            accountId = token.value();
          } else if (transaction != null) {
            transaction.putIfAbsent(token.name(), unescape(token.value()));
          }
        }
      }
    }
    if (!foundStatement) {
      throw new BankInputException("No bank account statement found.");
    }
    return BankStatement.builder()
        .totalRows(totalRows)
        .invalidRows(invalidRows)
        .transactions(transactions)
        .build();
  }

  private static BankTransaction toTransaction(
      String bankId, String accountId, Map<String, String> fields) {
    String fitId = StringUtils.trimToNull(fields.get("FITID"));
    LocalDate date = parseDate(fields.get("DTPOSTED"));
    BigDecimal amount = parseAmount(fields.get("TRNAMT"));
    if (fitId == null || date == null || amount == null) {
      return null;
    }
    return BankTransaction.builder()
        .sourceId(SOURCE_ID_PREFIX + sha256Hex(bankId + '\u0000' + accountId + '\u0000' + fitId))
        .amount(amount)
        .date(date)
        .name(StringUtils.trimToNull(fields.get("NAME")))
        .memo(StringUtils.trimToNull(fields.get("MEMO")))
        .build();
  }

  private static LocalDate parseDate(String value) {
    Matcher matcher = POSTED_DATE.matcher(StringUtils.defaultString(value));
    if (!matcher.find()) {
      return null;
    }
    try {
      return LocalDate.parse(matcher.group(1), OFX_DATE);
    } catch (RuntimeException e) {
      return null;
    }
  }

  private static BigDecimal parseAmount(String value) {
    if (StringUtils.isBlank(value)) {
      return null;
    }
    try {
      return new BigDecimal(value.trim().replace(',', '.'));
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private static String unescape(String value) {
    return ESCAPE
        .matcher(value)
        .replaceAll(
            match ->
                switch (match.group(1)) {
                  case "amp" -> "&";
                  case "lt" -> "<";
                  default -> ">";
                });
  }

  private static String sha256Hex(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is unavailable", e);
    }
  }

  private record Token(String name, boolean closing, String value) {}
}
