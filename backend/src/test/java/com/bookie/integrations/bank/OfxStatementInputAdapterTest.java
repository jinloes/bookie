package com.bookie.integrations.bank;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class OfxStatementInputAdapterTest {

  private static final String ACCOUNT = "123456789";
  private static final String BANK = "121000248";

  private final OfxStatementInputAdapter adapter = new OfxStatementInputAdapter();

  @Nested
  class Parse {

    @Test
    void readsAnOfx1SgmlStatement() {
      String ofx =
          """
          OFXHEADER:100
          DATA:OFXSGML
          VERSION:102
          CHARSET:NONE

          <OFX>
          <SIGNONMSGSRSV1><SONRS><STATUS><CODE>0</STATUS><DTSERVER>20260901</SONRS>
          </SIGNONMSGSRSV1>
          <BANKMSGSRSV1><STMTTRNRS><STMTRS><CURDEF>USD
          <BANKACCTFROM><BANKID>121000248<ACCTID>123456789<ACCTTYPE>CHECKING</BANKACCTFROM>
          <BANKTRANLIST><DTSTART>20260801<DTEND>20260831
          <STMTTRN><TRNTYPE>CREDIT<DTPOSTED>20260803120000.000[-7:PDT]<TRNAMT>1500.00
          <FITID>2026080301<NAME>JANE DOE<MEMO>August rent</STMTTRN>
          <STMTTRN><TRNTYPE>DEBIT<DTPOSTED>20260805<TRNAMT>-45.10<FITID>2026080501
          <NAME>ACME &amp; SONS &lt;HW&gt;</STMTTRN>
          </BANKTRANLIST></STMTRS></STMTTRNRS></BANKMSGSRSV1></OFX>
          """;

      BankStatement result = adapter.parse(ofx.getBytes(StandardCharsets.UTF_8));

      assertThat(result.totalRows()).isEqualTo(2);
      assertThat(result.invalidRows()).isZero();
      assertThat(result.transactions())
          .containsExactly(
              new BankTransaction(
                  sourceId(BANK, ACCOUNT, "2026080301"),
                  new BigDecimal("1500.00"),
                  LocalDate.of(2026, 8, 3),
                  "JANE DOE",
                  "August rent"),
              new BankTransaction(
                  sourceId(BANK, ACCOUNT, "2026080501"),
                  new BigDecimal("-45.10"),
                  LocalDate.of(2026, 8, 5),
                  "ACME & SONS <HW>",
                  null));
      assertThat(result.transactions())
          .allSatisfy(
              transaction -> {
                assertThat(transaction.sourceId()).matches("ofx:[0-9a-f]{64}");
                assertThat(transaction.toString()).doesNotContain(ACCOUNT);
              });
    }

    @Test
    void readsAnOfx2XmlStatementWithLowercaseTags() {
      String ofx =
          """
          <?xml version="1.0" encoding="UTF-8"?>
          <?OFX OFXHEADER="200" VERSION="220" SECURITY="NONE"?>
          <ofx>
            <bankmsgsrsv1><stmttrnrs><stmtrs>
              <bankacctfrom><bankid>121000248</bankid><acctid>123456789</acctid></bankacctfrom>
              <banktranlist>
                <stmttrn>
                  <trntype>DEP</trntype><dtposted>20260910</dtposted><trnamt>+200.5</trnamt>
                  <fitid>X-1</fitid><name>Café Tenant</name><memo>Café Tenant</memo>
                </stmttrn>
              </banktranlist>
            </stmtrs></stmttrnrs></bankmsgsrsv1>
          </ofx>
          """;

      BankStatement result = adapter.parse(ofx.getBytes(StandardCharsets.UTF_8));

      assertThat(result.transactions())
          .containsExactly(
              new BankTransaction(
                  sourceId(BANK, ACCOUNT, "X-1"),
                  new BigDecimal("200.5"),
                  LocalDate.of(2026, 9, 10),
                  "Café Tenant",
                  "Café Tenant"));
    }

    @Test
    void readsAQfxStatementDecodedAsWindows1252AndAcceptsACommaDecimal() {
      String qfx =
          """
          OFXHEADER:100
          DATA:OFXSGML
          CHARSET:1252

          <OFX><SIGNONMSGSRSV1><SONRS><INTU.BID>3000</SONRS></SIGNONMSGSRSV1>
          <BANKMSGSRSV1><STMTTRNRS><STMTRS>
          <BANKACCTFROM><ACCTID>123456789</BANKACCTFROM>
          <BANKTRANLIST><STMTTRN><DTPOSTED>20260102<TRNAMT>12,34<FITID>Q1<NAME>Café</STMTTRN>
          </BANKTRANLIST></STMTRS></STMTTRNRS></BANKMSGSRSV1></OFX>
          """;

      BankStatement result = adapter.parse(qfx.getBytes(Charset.forName("windows-1252")));

      assertThat(result.transactions())
          .containsExactly(
              new BankTransaction(
                  sourceId("", ACCOUNT, "Q1"),
                  new BigDecimal("12.34"),
                  LocalDate.of(2026, 1, 2),
                  "Café",
                  null));
    }

    @Test
    void countsTransactionsMissingAnIdentityDateOrAmountAsInvalid() {
      String ofx =
          statement(
              """
              <STMTTRN><DTPOSTED>20260801<TRNAMT>10.00<NAME>No FITID</STMTTRN>
              <STMTTRN><FITID>A<TRNAMT>10.00</STMTTRN>
              <STMTTRN><FITID>B<DTPOSTED>20261399<TRNAMT>10.00</STMTTRN>
              <STMTTRN><FITID>C<DTPOSTED>20260801</STMTTRN>
              <STMTTRN><FITID>D<DTPOSTED>20260801<TRNAMT>ten</STMTTRN>
              <STMTTRN><FITID>E<DTPOSTED>20260801<TRNAMT>10.00</STMTTRN>
              """);

      BankStatement result = adapter.parse(ofx.getBytes(StandardCharsets.UTF_8));

      assertThat(result.totalRows()).isEqualTo(6);
      assertThat(result.invalidRows()).isEqualTo(5);
      assertThat(result.transactions())
          .singleElement()
          .extracting(BankTransaction::sourceId)
          .isEqualTo(sourceId(BANK, ACCOUNT, "E"));
    }

    @Test
    void ignoresCreditCardTransactionsAndTransferTargetAccountsInAMixedFile() {
      String ofx =
          """
          <OFX>
          <CREDITCARDMSGSRSV1><CCSTMTTRNRS><CCSTMTRS>
          <CCACCTFROM><ACCTID>4111111111111111</CCACCTFROM>
          <BANKTRANLIST><STMTTRN><DTPOSTED>20260801<TRNAMT>5.00<FITID>CC1</STMTTRN>
          </BANKTRANLIST></CCSTMTRS></CCSTMTTRNRS></CREDITCARDMSGSRSV1>
          <BANKMSGSRSV1><STMTTRNRS><STMTRS>
          <BANKACCTFROM><BANKID>121000248<ACCTID>123456789</BANKACCTFROM>
          <BANKTRANLIST></STMTTRN>
          <STMTTRN><DTPOSTED>20260802<TRNAMT>7.00<FITID>T1
          <BANKACCTTO><BANKID>999<ACCTID>555</BANKACCTTO></STMTTRN>
          </BANKTRANLIST></STMTRS>
          <STMTRS><BANKACCTFROM><BANKID>1<ACCTID>2</BANKACCTFROM>
          <BANKTRANLIST><STMTTRN><DTPOSTED>20260803<TRNAMT>8.00<FITID>T2</STMTTRN>
          </BANKTRANLIST></STMTRS></STMTTRNRS></BANKMSGSRSV1></OFX>
          """;

      BankStatement result = adapter.parse(ofx.getBytes(StandardCharsets.UTF_8));

      assertThat(result.totalRows()).isEqualTo(2);
      assertThat(result.transactions())
          .extracting(BankTransaction::sourceId)
          .containsExactly(sourceId(BANK, ACCOUNT, "T1"), sourceId("1", "2", "T2"));
    }

    @Test
    void treatsDeclaredEntitiesAsLiteralTextWithoutExpandingThem() {
      String ofx =
          """
          <?xml version="1.0"?>
          <!DOCTYPE OFX [ <!ENTITY x "boom"> ]>
          <OFX><!-- comment --><BANKMSGSRSV1><STMTTRNRS><STMTRS>
          <BANKACCTFROM><BANKID>121000248<ACCTID>123456789</BANKACCTFROM>
          <BANKTRANLIST><STMTTRN><DTPOSTED>20260801<TRNAMT>1.00<FITID>E1<NAME>&x;</STMTTRN>
          </BANKTRANLIST></STMTRS></STMTTRNRS></BANKMSGSRSV1></OFX>
          """;

      BankStatement result = adapter.parse(ofx.getBytes(StandardCharsets.UTF_8));

      assertThat(result.transactions())
          .singleElement()
          .extracting(BankTransaction::name)
          .isEqualTo("&x;");
    }

    @Test
    void stopsAtATruncatedTagAndIgnoresEmptyTags() {
      String ofx =
          """
          <OFX><STMTRS><BANKACCTFROM><BANKID>121000248<ACCTID>123456789</BANKACCTFROM>
          <STMTTRN><DTPOSTED>20260801<TRNAMT>1.00<FITID>E1<>ignored</STMTTRN></STMTRS><NAME
          """;

      BankStatement result = adapter.parse(ofx.getBytes(StandardCharsets.UTF_8));

      assertThat(result.transactions())
          .singleElement()
          .extracting(BankTransaction::sourceId, BankTransaction::name)
          .containsExactly(sourceId(BANK, ACCOUNT, "E1"), null);
    }

    @Test
    void rejectsACreditCardOnlyStatementWithoutLeakingItsContent() {
      String ofx =
          """
          <OFX><CREDITCARDMSGSRSV1><CCSTMTTRNRS><CCSTMTRS>
          <CCACCTFROM><ACCTID>4111111111111111</CCACCTFROM>
          </CCSTMTRS></CCSTMTTRNRS></CREDITCARDMSGSRSV1></OFX>
          """;

      assertThatThrownBy(() -> adapter.parse(ofx.getBytes(StandardCharsets.UTF_8)))
          .isInstanceOf(BankInputException.class)
          .hasMessage("No bank account statement found.")
          .message()
          .doesNotContain("4111111111111111");
    }

    @Test
    void rejectsInputWithoutAnOfxRoot() {
      assertThatThrownBy(
              () -> adapter.parse("ID,Amount\n1,123456789".getBytes(StandardCharsets.UTF_8)))
          .isInstanceOf(BankInputException.class)
          .hasMessage("Not an OFX bank statement.")
          .message()
          .doesNotContain(ACCOUNT);
    }

    @Test
    void rejectsMissingInput() {
      assertThatThrownBy(() -> adapter.parse(null))
          .isInstanceOf(BankInputException.class)
          .hasMessage("Bank statement is required.");
    }
  }

  private static String statement(String transactions) {
    return """
        <OFX><BANKMSGSRSV1><STMTTRNRS><STMTRS>
        <BANKACCTFROM><BANKID>121000248<ACCTID>123456789</BANKACCTFROM>
        <BANKTRANLIST>
        %s</BANKTRANLIST></STMTRS></STMTTRNRS></BANKMSGSRSV1></OFX>
        """
        .formatted(transactions);
  }

  private static String sourceId(String bankId, String accountId, String fitId) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256")
              .digest(
                  (bankId + '\u0000' + accountId + '\u0000' + fitId)
                      .getBytes(StandardCharsets.UTF_8));
      return "ofx:" + HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
