package com.bookie;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bookie.intake.application.BackgroundJobStore;
import com.bookie.intake.extraction.EmailParserService;
import com.bookie.intake.extraction.EmailSuggestion;
import com.bookie.intake.extraction.EmailType;
import com.bookie.integrations.documents.DocumentTextExtractor;
import com.bookie.integrations.llm.LlmGateway;
import com.bookie.integrations.onedrive.OneDriveItem;
import com.bookie.integrations.onedrive.OneDrivePort;
import com.bookie.integrations.outlook.OutlookAttachment;
import com.bookie.integrations.outlook.OutlookAuthorization;
import com.bookie.integrations.outlook.OutlookMailPort;
import com.bookie.integrations.outlook.OutlookMessage;
import com.bookie.integrations.outlook.OutlookMessageIdentity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Pins the unified rows each write producer leaves behind, driven only through HTTP and the real
 * durable intake pipeline with external providers replaced. The assertions deliberately read the
 * unified ledger, inbox and classification-history tables so the same file keeps passing when the
 * write path underneath changes.
 */
@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:write_path_characterization_${random.uuid};DB_CLOSE_DELAY=-1",
      "spring.jpa.hibernate.ddl-auto=validate",
      "bookie.auto-import.enabled=false",
      "bookie.intake.worker.enabled=true",
      "jobrunr.background-job-server.enabled=true",
      "bookie.intake.worker.initial-delay-ms=0",
      "bookie.intake.worker.poll-interval-ms=1000"
    })
@AutoConfigureMockMvc
@Tag("slow")
class WritePathCharacterizationIntegrationTest {

  private static final Duration INTAKE_TIMEOUT = Duration.ofSeconds(60);
  private static final String ATTACHMENT_SOURCE_ID =
      "outlook-attachment:3d6f7bfb6d25c2014ec0d3b71f22934c280369cda5dce8a5b207bd876ab1e12c";

  @Autowired private MockMvc mockMvc;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private ObjectMapper objectMapper;

  @MockitoBean private OutlookMailPort outlookMail;
  @MockitoBean private OneDrivePort oneDrive;
  @MockitoBean private OutlookAuthorization outlookAuthorization;
  @MockitoBean private EmailParserService emailParserService;
  @MockitoBean private DocumentTextExtractor documentTextExtractor;
  @MockitoBean private LlmGateway llmGateway;
  @MockitoSpyBean private BackgroundJobStore backgroundJobStore;

  @Nested
  class ManualTransactions {

    @Test
    void createUpdateAndDeleteWriteOneLedgerRowWithHistory() throws Exception {
      Fixture fixture = fixture("manual");

      JsonNode created =
          json(
              mockMvc.perform(
                  post("/api/v2/transactions")
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(
                          """
                          {"amount":40.10,"direction":"EXPENSE","date":"2026-05-02",
                           "description":"Characterization manual repair",
                           "activityId":%d,"neutralCategoryId":%d,"counterpartyId":%d}
                          """
                              .formatted(
                                  fixture.activityId(),
                                  neutralCategory("REPAIRS_AND_MAINTENANCE"),
                                  fixture.counterpartyId()))));
      long id = created.get("id").asLong();

      assertThat(describe(id))
          .isEqualTo(
              "EXPENSE 40.10 2026-05-02 'Characterization manual repair' activity=Characterization"
                  + " manual house category=REPAIRS_AND_MAINTENANCE counterparty=Characterization manual vendor"
                  + " active refs=[] attachments=[]");
      assertThat(history("manual"))
          .containsExactly(
              "payer-category manual vendor/REPAIRS x1",
              "payer-property manual vendor/manual house x1");

      mockMvc
          .perform(
              put("/api/v2/transactions/{id}", id)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      """
                      {"version":%d,"amount":41.20,"direction":"EXPENSE","date":"2026-05-03",
                       "description":"Characterization manual repair updated",
                       "activityId":%d,"neutralCategoryId":%d}
                      """
                          .formatted(
                              created.get("version").asLong(),
                              fixture.activityId(),
                              neutralCategory("REPAIRS_AND_MAINTENANCE"))))
          .andExpect(status().isOk());

      assertThat(describe(id))
          .isEqualTo(
              "EXPENSE 41.20 2026-05-03 'Characterization manual repair updated'"
                  + " activity=Characterization manual house category=REPAIRS_AND_MAINTENANCE counterparty=null"
                  + " active refs=[] attachments=[]");
      assertThat(history("manual"))
          .containsExactly(
              "payer-category manual vendor/REPAIRS x1",
              "payer-property manual vendor/manual house x1");
      long version =
          jdbcTemplate.queryForObject(
              "SELECT version FROM financial_transactions WHERE id = ?", Long.class, id);

      mockMvc
          .perform(delete("/api/v2/transactions/{id}", id).param("version", Long.toString(version)))
          .andExpect(status().isNoContent());

      assertThat(describe(id)).contains(" deleted refs=[] attachments=[]");
      assertThat(activeTransactionsNamed("Characterization manual repair%")).isZero();
    }

    @Test
    void agentProposalWritesNothingUntilTheReviewedProposalIsSaved() throws Exception {
      Fixture fixture = fixture("agent");
      when(llmGateway.completeText(any()))
          .thenReturn(
              """
              {"direction":"EXPENSE","amount":18.75,"description":"Characterization agent paint",
               "date":"2026-06-01","counterpartyName":"Characterization agent vendor",
               "needsMoreInfo":false,"followUpQuestion":""}
              """);
      long before = count("financial_transactions");

      JsonNode proposal =
          json(mockMvc.perform(
                  post("/api/agent/transaction")
                      .contentType(MediaType.APPLICATION_JSON)
                      .content("{\"message\":\"Paint for Characterization agent house $18.75\"}")))
              .get("proposedTransaction");

      assertThat(count("financial_transactions")).isEqualTo(before);
      assertThat(proposal.get("activityId").asLong()).isEqualTo(fixture.activityId());
      assertThat(proposal.get("counterpartyId").asLong()).isEqualTo(fixture.counterpartyId());

      JsonNode saved =
          json(
              mockMvc.perform(
                  post("/api/v2/transactions")
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(
                          """
                          {"amount":%s,"direction":"%s","date":"%s","description":"%s",
                           "activityId":%d,"neutralCategoryId":%d,"counterpartyId":%d}
                          """
                              .formatted(
                                  proposal.get("amount").asText(),
                                  proposal.get("direction").asText(),
                                  proposal.get("date").asText(),
                                  proposal.get("description").asText(),
                                  proposal.get("activityId").asLong(),
                                  proposal.get("neutralCategoryId").asLong(),
                                  proposal.get("counterpartyId").asLong()))));

      assertThat(describe(saved.get("id").asLong()))
          .isEqualTo(
              "EXPENSE 18.75 2026-06-01 'Characterization agent paint' activity=Characterization"
                  + " agent house category=OTHER_EXPENSE counterparty=Characterization agent vendor"
                  + " active refs=[] attachments=[]");
    }
  }

  @Nested
  class DurableIntake {

    @Test
    void outlookEmailSaveWritesItsImportIdentityAndTranslationKeepsTheLegacyId() throws Exception {
      Fixture fixture = fixture("email");
      when(outlookMail.getMessage(eq("char-email-1"), anyBoolean()))
          .thenReturn(Optional.of(message("char-email-1", "Characterization email", List.of())));
      when(outlookMail.translateLegacyIds(anyList()))
          .thenAnswer(
              invocation ->
                  invocation.<List<String>>getArgument(0).stream()
                      .map(legacy -> new OutlookMessageIdentity(legacy, "immutable-" + legacy))
                      .toList());
      when(emailParserService.suggestFromEmail(
              eq("Characterization email"), anyString(), anyString(), nullable(Long.class)))
          .thenReturn(suggestion(fixture, 72.5, "Characterization email repair", "charemail"));

      long inboxId = parseEmail("char-email-1");
      awaitReady(inboxId);
      await()
          .atMost(INTAKE_TIMEOUT)
          .untilAsserted(
              () ->
                  assertThat(inbox(inboxId).get("immutableSourceId").asText())
                      .isEqualTo("immutable-char-email-1"));

      JsonNode saved = save(inboxId, "EXPENSE", "72.50", fixture, "REPAIRS_AND_MAINTENANCE", false);

      assertThat(saved.get("state").asText()).isEqualTo("SAVED");
      assertThat(describe(saved.get("financialTransactionId").asLong()))
          .isEqualTo(
              "EXPENSE 72.50 2026-07-04 'Characterization email repair' activity=Characterization"
                  + " email house category=REPAIRS_AND_MAINTENANCE counterparty=Characterization email vendor"
                  + " active refs=[OUTLOOK_EMAIL|char-email-1|char-email-1|-] attachments=[]");
      assertThat(history("email"))
          .containsExactly(
              "keyword-category charemail/REPAIRS x1",
              "keyword-classification charemail/Characterization email house/REPAIRS x1",
              "keyword-payer charemail/email vendor x1",
              "keyword-property charemail/email house x1",
              "payer-category email vendor/REPAIRS x1",
              "payer-property email vendor/email house x1");
      assertThat(jobTypes(inboxId))
          .containsExactlyInAnyOrder("MOVE_OUTLOOK", "PARSE_OUTLOOK", "TRANSLATE_OUTLOOK_ID");
    }

    @Test
    void outlookAttachmentSaveKeepsTheMessageAsItsParentIdentity() throws Exception {
      Fixture fixture = fixture("attachment");
      byte[] pdf = "attachment-bytes".getBytes(StandardCharsets.UTF_8);
      when(outlookMail.getMessage(eq("char-attach-1"), anyBoolean()))
          .thenReturn(
              Optional.of(
                  message(
                      "char-attach-1",
                      "Characterization attachment",
                      List.of(
                          new OutlookAttachment(
                              "att-1", "invoice.pdf", "application/pdf", false, pdf)))));
      when(outlookMail.translateLegacyIds(anyList())).thenReturn(List.of());
      when(documentTextExtractor.extractText(any(byte[].class), eq("invoice.pdf")))
          .thenReturn("Invoice total 33.00");
      when(emailParserService.suggestFromEmail(
              anyString(), anyString(), anyString(), nullable(Long.class)))
          .thenReturn(suggestion(fixture, 33.0, "Characterization attachment supplies", "charatt"));

      long inboxId = parseEmail("char-attach-1");
      awaitReady(inboxId);
      JsonNode saved = save(inboxId, "EXPENSE", "33.00", fixture, "SUPPLIES", false);

      assertThat(describe(saved.get("financialTransactionId").asLong()))
          .isEqualTo(
              "EXPENSE 33.00 2026-07-04 'Characterization attachment supplies'"
                  + " activity=Characterization attachment house category=SUPPLIES"
                  + " counterparty=Characterization attachment vendor active"
                  + " refs=[OUTLOOK_EMAIL|"
                  + ATTACHMENT_SOURCE_ID
                  + "|char-attach-1|-] attachments=[]");
      assertThat(inbox(inboxId).get("sourceId").asText()).isEqualTo(ATTACHMENT_SOURCE_ID);
    }

    @Test
    void receiptSaveAttachesTheUploadedFileWithItsChecksum() throws Exception {
      Fixture fixture = fixture("receipt");
      byte[] content = "receipt-bytes".getBytes(StandardCharsets.UTF_8);
      when(outlookAuthorization.isConnected()).thenReturn(true);
      when(oneDrive.listChildren(anyString())).thenReturn(List.of());
      when(oneDrive.upload(anyString(), any()))
          .thenReturn(receiptItem("char-receipt-1", "char-receipt.pdf"));
      when(oneDrive.getItem("char-receipt-1"))
          .thenReturn(Optional.of(receiptItem("char-receipt-1", "char-receipt.pdf")));
      when(oneDrive.download("char-receipt-1"))
          .thenAnswer(invocation -> new ByteArrayInputStream(content));
      when(oneDrive.ensureFolder(anyString(), anyString())).thenReturn("year-folder");
      when(documentTextExtractor.extractText(any(byte[].class), eq("char-receipt.pdf")))
          .thenReturn("Receipt total 21.00");
      when(emailParserService.suggestFromReceipt(
              eq("char-receipt.pdf"), eq("Receipt total 21.00"), isNull()))
          .thenReturn(suggestion(fixture, 21.0, "Characterization receipt cleaning", "charrcpt"));

      mockMvc
          .perform(
              multipart("/api/receipts/upload")
                  .file(
                      new MockMultipartFile(
                          "file", "char-receipt.pdf", "application/pdf", content)))
          .andExpect(status().isOk());
      long inboxId =
          json(mockMvc.perform(post("/api/receipts/char-receipt-1/parse"))).get("id").asLong();
      awaitReady(inboxId);
      JsonNode saved =
          save(inboxId, "EXPENSE", "21.00", fixture, "CLEANING_AND_MAINTENANCE", false);

      assertThat(describe(saved.get("financialTransactionId").asLong()))
          .isEqualTo(
              "EXPENSE 21.00 2026-07-04 'Characterization receipt cleaning'"
                  + " activity=Characterization receipt house category=CLEANING_AND_MAINTENANCE"
                  + " counterparty=Characterization receipt vendor active"
                  + " refs=[RECEIPT|char-receipt-1|-|-]"
                  + " attachments=[ONEDRIVE|char-receipt-1|char-receipt.pdf|-]");
      assertThat(jobTypes(inboxId)).containsExactlyInAnyOrder("MOVE_RECEIPT", "PARSE_RECEIPT");
    }

    @Test
    void venmoImportAcceptWritesAnIncomeWithItsSourceLabel() throws Exception {
      Fixture fixture = fixture("venmo");
      String csv =
          """
          ,ID,Datetime,Type,Status,Note,From,To,Amount (total)
          ,char-venmo-1,2026-04-05T09:15:00,Payment,Complete,April rent,@charvenmo,Demo User,"+ $950.00"
          """;

      JsonNode imported =
          json(
              mockMvc.perform(
                  multipart("/api/v2/inbox/imports/venmo")
                      .file(
                          new MockMultipartFile(
                              "file",
                              "venmo.csv",
                              "text/csv",
                              csv.getBytes(StandardCharsets.UTF_8)))
                      .param("activityId", Long.toString(fixture.activityId()))));
      assertThat(imported.get("importedRows").asInt()).isEqualTo(1);
      JsonNode item = activeInboxItem("VENMO", "char-venmo-1");
      assertThat(item.get("state").asText()).isEqualTo("READY");

      JsonNode saved =
          save(item.get("id").asLong(), "INCOME", "950.00", fixture, "RENTAL_INCOME", true);

      assertThat(describe(saved.get("financialTransactionId").asLong()))
          .isEqualTo(
              "INCOME 950.00 2026-04-05 'Venmo - April rent' activity=Characterization venmo house"
                  + " category=RENTAL_INCOME counterparty=Characterization venmo vendor active"
                  + " refs=[VENMO|char-venmo-1|-|Characterization venmo label] attachments=[]");
      assertThat(history("venmo")).containsExactly("payer-property venmo vendor/venmo house x1");
    }

    @Test
    void bankImportAcceptWritesAnIncomeWithAHashedReferenceAndNoAttachment() throws Exception {
      Fixture fixture = fixture("bank");
      String ofx =
          """
          OFXHEADER:100
          DATA:OFXSGML

          <OFX><BANKMSGSRSV1><STMTTRNRS><STMTRS>
          <BANKACCTFROM><BANKID>121000248<ACCTID>987654321</BANKACCTFROM>
          <BANKTRANLIST>
          <STMTTRN><DTPOSTED>20260406<TRNAMT>1200.00<FITID>char-bank-1<NAME>CHARBANK TENANT
          <MEMO>April rent</STMTTRN>
          <STMTTRN><DTPOSTED>20260407<TRNAMT>-80.00<FITID>char-bank-2<NAME>HARDWARE</STMTTRN>
          </BANKTRANLIST></STMTRS></STMTTRNRS></BANKMSGSRSV1></OFX>
          """;
      MockMultipartFile file =
          new MockMultipartFile(
              "file", "statement.ofx", "application/x-ofx", ofx.getBytes(StandardCharsets.UTF_8));

      JsonNode imported =
          json(
              mockMvc.perform(
                  multipart("/api/v2/inbox/imports/bank")
                      .file(file)
                      .param("activityId", Long.toString(fixture.activityId()))));
      assertThat(imported.get("importedRows").asInt()).isEqualTo(1);
      assertThat(imported.get("skippedDebitRows").asInt()).isEqualTo(1);
      JsonNode item = activeInboxItemFrom("BANK");
      String sourceId = item.get("sourceId").asText();
      assertThat(sourceId).matches("ofx:[0-9a-f]{64}");
      assertThat(item.toString()).doesNotContain("987654321");

      JsonNode saved =
          save(item.get("id").asLong(), "INCOME", "1200.00", fixture, "RENTAL_INCOME", true);

      assertThat(describe(saved.get("financialTransactionId").asLong()))
          .isEqualTo(
              "INCOME 1200.00 2026-04-06 'CHARBANK TENANT - April rent' activity=Characterization"
                  + " bank house category=RENTAL_INCOME counterparty=Characterization bank vendor"
                  + " active refs=[BANK|"
                  + sourceId
                  + "|-|Characterization bank label] attachments=[]");
      assertThat(jobTypes(item.get("id").asLong())).isEmpty();

      JsonNode reimported =
          json(
              mockMvc.perform(
                  multipart("/api/v2/inbox/imports/bank")
                      .file(file)
                      .param("activityId", Long.toString(fixture.activityId()))));
      assertThat(reimported.get("importedRows").asInt()).isZero();
      assertThat(reimported.get("skippedDuplicateRows").asInt()).isEqualTo(1);
    }
  }

  @Nested
  class InboxSaveAtomicity {

    @Test
    void saveCommitsOneTransactionSavedStateAndOneMoveJobTogether() throws Exception {
      Fixture fixture = fixture("atomic");
      long inboxId = readyEmail(fixture, "char-atomic-1", "charatomic");

      JsonNode saved = save(inboxId, "EXPENSE", "10.00", fixture, "REPAIRS_AND_MAINTENANCE", false);

      assertThat(activeTransactionsNamed("Characterization atomic%")).isOne();
      assertThat(saved.get("state").asText()).isEqualTo("SAVED");
      assertThat(jobTypes(inboxId).stream().filter(type -> type.startsWith("MOVE_"))).hasSize(1);
    }

    @Test
    void aFailedJobWriteRollsBackTheTransactionAndInboxState() throws Exception {
      Fixture fixture = fixture("rollback");
      long inboxId = readyEmail(fixture, "char-rollback-1", "charrollback");
      doThrow(new IllegalStateException("job store unavailable"))
          .when(backgroundJobStore)
          .save(argThat(job -> job != null && job.getType().isExternalSync()));

      mockMvc
          .perform(
              post("/api/v2/inbox/{id}/save", inboxId)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      saveRequest(
                          inboxId, "EXPENSE", "10.00", fixture, "REPAIRS_AND_MAINTENANCE", false)))
          .andExpect(status().is5xxServerError());

      assertThat(activeTransactionsNamed("Characterization rollback%")).isZero();
      assertThat(inbox(inboxId).get("state").asText()).isEqualTo("READY");
      assertThat(jobTypes(inboxId).stream().filter(type -> type.startsWith("MOVE_"))).isEmpty();
    }

    @Test
    void reparseConflictsWhileTheItemIsProcessing() throws Exception {
      Fixture fixture = fixture("processing");
      CountDownLatch parsing = new CountDownLatch(1);
      CountDownLatch release = new CountDownLatch(1);
      when(outlookMail.getMessage(eq("char-processing-1"), anyBoolean()))
          .thenReturn(
              Optional.of(message("char-processing-1", "Characterization processing", List.of())));
      when(outlookMail.translateLegacyIds(anyList())).thenReturn(List.of());
      when(emailParserService.suggestFromEmail(
              eq("Characterization processing"), anyString(), anyString(), nullable(Long.class)))
          .thenAnswer(
              invocation -> {
                parsing.countDown();
                release.await(INTAKE_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
                return suggestion(fixture, 5.0, "Characterization processing", "charproc");
              });

      long inboxId = parseEmail("char-processing-1");
      assertThat(parsing.await(INTAKE_TIMEOUT.toSeconds(), TimeUnit.SECONDS)).isTrue();
      try {
        assertThat(inbox(inboxId).get("state").asText()).isEqualTo("PROCESSING");
        mockMvc
            .perform(post("/api/v2/inbox/{id}/reparse", inboxId))
            .andExpect(status().isConflict());
      } finally {
        release.countDown();
      }
      awaitReady(inboxId);
    }
  }

  private record Fixture(String token, long activityId, long counterpartyId) {}

  private Fixture fixture(String token) throws Exception {
    String house = "Characterization " + token + " house";
    mockMvc
        .perform(
            post("/api/properties")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name":"%s","address":"1 %s Way","type":"SINGLE_FAMILY","accounts":[]}
                    """
                        .formatted(house, token)))
        .andExpect(status().isOk());
    long activityId =
        jdbcTemplate.queryForObject(
            """
            SELECT activity.id
            FROM financial_activities activity
            JOIN properties property ON property.id = activity.property_id
            WHERE property.name = ?
            """,
            Long.class,
            house);
    // New activities are profiled from their creation date; backdate so the fixed 2026 dates
    // below resolve regardless of when the suite runs.
    jdbcTemplate.update(
        "UPDATE activity_reporting_profile_assignments SET effective_from = DATE '2000-01-01'"
            + " WHERE activity_id = ?",
        activityId);
    long counterpartyId =
        json(mockMvc.perform(
                post("/api/counterparties")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                            {"name":"Characterization %s vendor","type":"COMPANY",
                             "aliases":["char%s"],"accounts":[]}
                            """
                            .formatted(token, token))))
            .get("id")
            .asLong();
    return new Fixture(token, activityId, counterpartyId);
  }

  private long readyEmail(Fixture fixture, String messageId, String keyword) throws Exception {
    String subject = "Characterization " + fixture.token();
    when(outlookMail.getMessage(eq(messageId), anyBoolean()))
        .thenReturn(Optional.of(message(messageId, subject, List.of())));
    when(outlookMail.translateLegacyIds(anyList())).thenReturn(List.of());
    when(emailParserService.suggestFromEmail(
            eq(subject), anyString(), anyString(), nullable(Long.class)))
        .thenReturn(suggestion(fixture, 10.0, subject + " item", keyword));
    long inboxId = parseEmail(messageId);
    awaitReady(inboxId);
    return inboxId;
  }

  private long parseEmail(String messageId) throws Exception {
    return json(mockMvc.perform(
            post("/api/outlook/emails/{id}/parse", messageId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")))
        .get("id")
        .asLong();
  }

  private void awaitReady(long inboxId) {
    await()
        .atMost(INTAKE_TIMEOUT)
        .untilAsserted(() -> assertThat(inbox(inboxId).get("state").asText()).isEqualTo("READY"));
  }

  private JsonNode save(
      long inboxId,
      String direction,
      String amount,
      Fixture fixture,
      String neutralCategoryKey,
      boolean withSourceLabel)
      throws Exception {
    return json(
        mockMvc.perform(
            post("/api/v2/inbox/{id}/save", inboxId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    saveRequest(
                        inboxId,
                        direction,
                        amount,
                        fixture,
                        neutralCategoryKey,
                        withSourceLabel))));
  }

  private String saveRequest(
      long inboxId,
      String direction,
      String amount,
      Fixture fixture,
      String neutralCategoryKey,
      boolean withSourceLabel)
      throws Exception {
    JsonNode proposal = inbox(inboxId);
    Map<String, Object> request = new java.util.LinkedHashMap<>();
    request.put("direction", direction);
    request.put("amount", new java.math.BigDecimal(amount));
    request.put("date", proposal.get("date").asText());
    request.put("description", proposal.get("description").asText());
    request.put("activityId", fixture.activityId());
    request.put("neutralCategoryId", neutralCategory(neutralCategoryKey));
    request.put("counterpartyId", fixture.counterpartyId());
    if (withSourceLabel) {
      request.put("sourceLabel", "Characterization " + fixture.token() + " label");
    }
    return objectMapper.writeValueAsString(request);
  }

  private JsonNode activeInboxItem(String origin, String sourceId) throws Exception {
    for (JsonNode item : json(mockMvc.perform(get("/api/v2/inbox")))) {
      if (origin.equals(item.get("origin").asText())
          && sourceId.equals(item.get("sourceId").asText())) {
        return item;
      }
    }
    throw new AssertionError("No active inbox item " + origin + "/" + sourceId);
  }

  private JsonNode activeInboxItemFrom(String origin) throws Exception {
    for (JsonNode item : json(mockMvc.perform(get("/api/v2/inbox")))) {
      if (origin.equals(item.get("origin").asText())) {
        return item;
      }
    }
    throw new AssertionError("No active inbox item from " + origin);
  }

  private JsonNode inbox(long inboxId) throws Exception {
    return json(mockMvc.perform(get("/api/v2/inbox/{id}", inboxId)));
  }

  private List<String> jobTypes(long inboxId) throws Exception {
    List<String> types = new java.util.ArrayList<>();
    json(mockMvc.perform(get("/api/v2/inbox/{id}/jobs", inboxId)))
        .forEach(job -> types.add(job.get("type").asText()));
    return types;
  }

  private JsonNode json(ResultActions result) throws Exception {
    return objectMapper.readTree(
        result.andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
  }

  private EmailSuggestion suggestion(
      Fixture fixture, double amount, String description, String keyword) {
    return EmailSuggestion.builder()
        .emailType(EmailType.EXPENSE)
        .amount(amount)
        .description(description)
        .date("2026-07-04")
        .category("REPAIRS")
        .propertyName("Characterization " + fixture.token() + " house")
        .payerName("Characterization " + fixture.token() + " vendor")
        .keywords(List.of(keyword))
        .accountNumbers(List.of())
        .activityId(fixture.activityId())
        .classificationAmbiguous(false)
        .build();
  }

  private OutlookMessage message(String id, String subject, List<OutlookAttachment> attachments) {
    return new OutlookMessage(
        OutlookMessageIdentity.unresolved(id),
        subject,
        "sender@example.test",
        OffsetDateTime.of(2026, 7, 4, 12, 0, 0, 0, ZoneOffset.UTC),
        "preview",
        "<p>Characterization body</p>",
        "folder",
        attachments);
  }

  private OneDriveItem receiptItem(String id, String name) {
    return OneDriveItem.builder().id(id).name(name).parentPath("/drive/root:/pending").build();
  }

  private long neutralCategory(String key) {
    return jdbcTemplate.queryForObject(
        "SELECT id FROM neutral_categories WHERE category_key = ?", Long.class, key);
  }

  private long count(String table) {
    return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
  }

  private long activeTransactionsNamed(String pattern) {
    return jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM financial_transactions WHERE description LIKE ? AND deleted_at IS NULL",
        Long.class,
        pattern);
  }

  private String describe(long transactionId) {
    Map<String, Object> row =
        jdbcTemplate.queryForMap(
            """
            SELECT transaction.direction, transaction.amount, transaction.transaction_date,
                   transaction.description, activity.name AS activity_name,
                   category.category_key, counterparty.name AS counterparty_name,
                   transaction.deleted_at
            FROM financial_transactions transaction
            JOIN financial_activities activity ON activity.id = transaction.activity_id
            JOIN neutral_categories category ON category.id = transaction.neutral_category_id
            LEFT JOIN counterparties counterparty ON counterparty.id = transaction.counterparty_id
            WHERE transaction.id = ?
            """,
            transactionId);
    List<String> references =
        jdbcTemplate.query(
            """
            SELECT origin, external_id, parent_external_id, source_label
            FROM transaction_import_references
            WHERE transaction_id = ?
            ORDER BY id
            """,
            (result, rowNumber) ->
                String.join(
                    "|",
                    result.getString(1),
                    value(result.getString(2), ""),
                    value(result.getString(3), "-"),
                    value(result.getString(4), "-")),
            transactionId);
    List<String> attachments =
        jdbcTemplate.query(
            """
            SELECT storage_provider, external_id, file_name, sha256
            FROM transaction_attachments
            WHERE transaction_id = ?
            ORDER BY id
            """,
            (result, rowNumber) ->
                String.join(
                    "|",
                    result.getString(1),
                    value(result.getString(2), "-"),
                    value(result.getString(3), "-"),
                    value(result.getString(4), "-")),
            transactionId);
    return "%s %s %s '%s' activity=%s category=%s counterparty=%s %s refs=%s attachments=%s"
        .formatted(
            row.get("DIRECTION"),
            row.get("AMOUNT"),
            row.get("TRANSACTION_DATE"),
            row.get("DESCRIPTION"),
            row.get("ACTIVITY_NAME"),
            row.get("CATEGORY_KEY"),
            row.get("COUNTERPARTY_NAME"),
            row.get("DELETED_AT") == null ? "active" : "deleted",
            references,
            attachments)
        .replace(", ", ",");
  }

  private List<String> history(String token) {
    String like = "%" + token + "%";
    String shortName = "Characterization ";
    List<String> rows = new java.util.ArrayList<>();
    rows.addAll(
        jdbcTemplate.query(
            """
            SELECT counterparty.name, property.name, history.occurrences
            FROM payer_property_history history
            JOIN counterparties counterparty ON counterparty.id = history.payer_id
            JOIN properties property ON property.id = history.property_id
            WHERE counterparty.name LIKE ?
            """,
            (result, rowNumber) ->
                "payer-property %s/%s x%d"
                    .formatted(
                        result.getString(1).replace(shortName, ""),
                        result.getString(2).replace(shortName, ""),
                        result.getInt(3)),
            like));
    rows.addAll(
        jdbcTemplate.query(
            """
            SELECT counterparty.name, history.category, history.occurrences
            FROM payer_category_history history
            JOIN counterparties counterparty ON counterparty.id = history.payer_id
            WHERE counterparty.name LIKE ?
            """,
            (result, rowNumber) ->
                "payer-category %s/%s x%d"
                    .formatted(
                        result.getString(1).replace(shortName, ""),
                        result.getString(2),
                        result.getInt(3)),
            like));
    rows.addAll(
        jdbcTemplate.query(
            """
            SELECT history.keyword, history.category, history.occurrences
            FROM email_keyword_category_history history
            WHERE history.keyword LIKE ?
            """,
            (result, rowNumber) ->
                "keyword-category %s/%s x%d"
                    .formatted(result.getString(1), result.getString(2), result.getInt(3)),
            like));
    rows.addAll(
        jdbcTemplate.query(
            """
            SELECT history.keyword, activity.name, category.category_key, history.occurrences
            FROM email_keyword_classification_history history
            JOIN financial_activities activity ON activity.id = history.activity_id
            JOIN financial_categories category ON category.id = history.financial_category_id
            WHERE history.keyword LIKE ?
            """,
            (result, rowNumber) ->
                "keyword-classification %s/%s/%s x%d"
                    .formatted(
                        result.getString(1),
                        result.getString(2),
                        result.getString(3),
                        result.getInt(4)),
            like));
    rows.addAll(
        jdbcTemplate.query(
            """
            SELECT history.keyword, counterparty.name, history.occurrences
            FROM email_keyword_payer_history history
            JOIN counterparties counterparty ON counterparty.id = history.payer_id
            WHERE history.keyword LIKE ?
            """,
            (result, rowNumber) ->
                "keyword-payer %s/%s x%d"
                    .formatted(
                        result.getString(1),
                        result.getString(2).replace(shortName, ""),
                        result.getInt(3)),
            like));
    rows.addAll(
        jdbcTemplate.query(
            """
            SELECT history.keyword, property.name, history.occurrences
            FROM email_keyword_property_history history
            JOIN properties property ON property.id = history.property_id
            WHERE history.keyword LIKE ?
            """,
            (result, rowNumber) ->
                "keyword-property %s/%s x%d"
                    .formatted(
                        result.getString(1),
                        result.getString(2).replace(shortName, ""),
                        result.getInt(3)),
            like));
    return rows.stream().sorted().toList();
  }

  private static String value(String value, String fallback) {
    return value == null ? fallback : value;
  }
}
