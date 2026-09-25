package com.bookie.intake.sources;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.bookie.intake.domain.InboxItem;
import com.bookie.intake.domain.InboxState;
import com.bookie.intake.sources.InboxItemCreationService.FindOrCreateResult;
import com.bookie.intake.sources.InboxItemCreationService.IntakeSource;
import com.bookie.ledger.domain.ExpenseSource;
import java.io.ByteArrayInputStream;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

@WebMvcTest(ReceiptController.class)
class ReceiptControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private ReceiptService receiptService;
  @MockitoBean private InboxItemCreationService inboxItemCreationService;

  private static final IntakeSource SOURCE =
      IntakeSource.of("item-1", ExpenseSource.RECEIPT, "bill.pdf");

  private ReceiptDto receipt() {
    return new ReceiptDto(
        "item-1", "bill.pdf", 0, "https://1drv.ms/x", "2024-01-15T10:00:00Z", null, null, true);
  }

  @Nested
  class Upload {

    @Test
    void upload_returnsReceiptWhenConnected() throws Exception {
      when(receiptService.isConnected()).thenReturn(true);
      when(receiptService.uploadReceipt(anyString(), any()))
          .thenReturn(new UploadReceiptResponse(receipt(), false));

      MockMultipartFile file =
          new MockMultipartFile("file", "bill.pdf", "application/pdf", "content".getBytes());

      mockMvc
          .perform(multipart("/api/receipts/upload").file(file))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.receipt.id").value("item-1"))
          .andExpect(jsonPath("$.duplicate").value(false));
    }

    @Test
    void upload_returns503WhenNotConnected() throws Exception {
      when(receiptService.isConnected()).thenReturn(false);

      MockMultipartFile file =
          new MockMultipartFile("file", "bill.pdf", "application/pdf", "content".getBytes());

      mockMvc
          .perform(multipart("/api/receipts/upload").file(file))
          .andExpect(status().isServiceUnavailable());
    }

    @Test
    void upload_returnsDuplicateFlagWhenFileExists() throws Exception {
      when(receiptService.isConnected()).thenReturn(true);
      ReceiptDto linked = new ReceiptDto("item-1", "bill.pdf", 0, null, null, 42L, null, true);
      when(receiptService.uploadReceipt(anyString(), any()))
          .thenReturn(new UploadReceiptResponse(linked, true));

      MockMultipartFile file =
          new MockMultipartFile("file", "bill.pdf", "application/pdf", "content".getBytes());

      mockMvc
          .perform(multipart("/api/receipts/upload").file(file))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.duplicate").value(true))
          .andExpect(jsonPath("$.receipt.expenseId").value(42));
    }

    @Test
    void upload_rejectsBlankFilename() throws Exception {
      when(receiptService.isConnected()).thenReturn(true);
      MockMultipartFile file = new MockMultipartFile("file", "", "application/pdf", "x".getBytes());

      mockMvc
          .perform(multipart("/api/receipts/upload").file(file))
          .andExpect(status().isBadRequest());

      verify(receiptService, never()).uploadReceipt(anyString(), any());
    }

    @Test
    void upload_sanitizesPathShapedFilename() throws Exception {
      when(receiptService.isConnected()).thenReturn(true);
      when(receiptService.uploadReceipt(anyString(), any()))
          .thenReturn(new UploadReceiptResponse(receipt(), false));
      MockMultipartFile file =
          new MockMultipartFile("file", "../nested/bill.pdf", "application/pdf", "x".getBytes());

      mockMvc.perform(multipart("/api/receipts/upload").file(file)).andExpect(status().isOk());

      verify(receiptService).uploadReceipt(eq("bill.pdf"), any());
    }
  }

  @Nested
  class ListReceipts {

    @Test
    void list_returnsAllReceiptsWhenConnected() throws Exception {
      when(receiptService.isConnected()).thenReturn(true);
      when(receiptService.listReceipts()).thenReturn(List.of(receipt()));

      mockMvc
          .perform(get("/api/receipts"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$[0].id").value("item-1"))
          .andExpect(jsonPath("$[0].name").value("bill.pdf"))
          .andExpect(jsonPath("$[0].year").value(0))
          .andExpect(jsonPath("$[0].pending").value(true));
    }

    @Test
    void list_returns503WhenNotConnected() throws Exception {
      when(receiptService.isConnected()).thenReturn(false);

      mockMvc.perform(get("/api/receipts")).andExpect(status().isServiceUnavailable());
    }
  }

  @Nested
  class ParseReceipt {

    @Test
    void parse_createsANewInboxItemAndQueuesItsParse() throws Exception {
      stubReceipt();
      when(inboxItemCreationService.findOrCreate(SOURCE))
          .thenReturn(new FindOrCreateResult(item(110L, InboxState.QUEUED), false));

      mockMvc
          .perform(post("/api/receipts/item-1/parse"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.id").value(110))
          .andExpect(jsonPath("$.state").value("QUEUED"))
          .andExpect(jsonPath("$.count").value(1))
          .andExpect(jsonPath("$.ids[0]").value(110));

      // findOrCreate publishes the parse kick (InboxItemQueued) with the item's commit.
      verify(inboxItemCreationService).findOrCreate(SOURCE);
    }

    @Test
    void parse_returnsTheExistingItemWhileItIsStillProcessing() throws Exception {
      stubReceipt();
      when(inboxItemCreationService.findOrCreate(SOURCE))
          .thenReturn(new FindOrCreateResult(item(107L, InboxState.PROCESSING), true));

      mockMvc
          .perform(post("/api/receipts/item-1/parse"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.id").value(107))
          .andExpect(jsonPath("$.state").value("PROCESSING"));
    }

    @Test
    void parse_surfacesAnAlreadySavedConflict() throws Exception {
      stubReceipt();
      when(inboxItemCreationService.findOrCreate(SOURCE))
          .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "already saved"));

      mockMvc.perform(post("/api/receipts/item-1/parse")).andExpect(status().isConflict());
    }

    private void stubReceipt() {
      when(receiptService.isConnected()).thenReturn(true);
      when(receiptService.getReceiptName("item-1")).thenReturn("bill.pdf");
    }

    private InboxItem item(Long id, InboxState state) {
      return InboxItem.builder().id(id).origin(ExpenseSource.RECEIPT).state(state).build();
    }

    @Test
    void parse_returns503WhenNotConnected() throws Exception {
      when(receiptService.isConnected()).thenReturn(false);

      mockMvc
          .perform(post("/api/receipts/item-1/parse"))
          .andExpect(status().isServiceUnavailable());
    }
  }

  @Nested
  class DeleteReceipt {

    @Test
    void delete_returns204WhenConnected() throws Exception {
      when(receiptService.isConnected()).thenReturn(true);
      doNothing().when(receiptService).deleteReceipt("item-1");

      mockMvc.perform(delete("/api/receipts/item-1")).andExpect(status().isNoContent());

      verify(receiptService).deleteReceipt("item-1");
    }

    @Test
    void delete_returns503WhenNotConnected() throws Exception {
      when(receiptService.isConnected()).thenReturn(false);

      mockMvc.perform(delete("/api/receipts/item-1")).andExpect(status().isServiceUnavailable());

      verify(receiptService, never()).deleteReceipt(any());
    }
  }

  @Nested
  class Download {

    @Test
    void download_returnsReceiptStreamWhenConnected() throws Exception {
      when(receiptService.isConnected()).thenReturn(true);
      when(receiptService.getReceiptName("item-1")).thenReturn("bill.pdf");
      when(receiptService.getReceiptContent("item-1"))
          .thenReturn(new ByteArrayInputStream("pdf-content".getBytes()));

      mockMvc
          .perform(get("/api/receipts/item-1/download"))
          .andExpect(status().isOk())
          .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("bill.pdf")))
          .andExpect(content().contentType("application/pdf"));
    }

    @Test
    void download_usesImageContentTypeForJpgReceipts() throws Exception {
      when(receiptService.isConnected()).thenReturn(true);
      when(receiptService.getReceiptName("item-1")).thenReturn("scan.jpg");
      when(receiptService.getReceiptContent("item-1"))
          .thenReturn(new ByteArrayInputStream("jpg-content".getBytes()));

      mockMvc
          .perform(get("/api/receipts/item-1/download"))
          .andExpect(status().isOk())
          .andExpect(content().contentType("image/jpeg"));
    }

    @Test
    void download_returns503WhenNotConnected() throws Exception {
      when(receiptService.isConnected()).thenReturn(false);

      mockMvc
          .perform(get("/api/receipts/item-1/download"))
          .andExpect(status().isServiceUnavailable());
    }
  }

  @Nested
  class Settings {

    @Test
    void getSettings_returnsFolderBaseAndImportFolders() throws Exception {
      when(receiptService.getReceiptsFolderBase()).thenReturn("bookie/taxes");
      when(receiptService.getReceiptsImportFolders()).thenReturn(List.of("Scans/Receipts"));

      mockMvc
          .perform(get("/api/receipts/settings"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.folderBase").value("bookie/taxes"))
          .andExpect(jsonPath("$.importFolders[0]").value("Scans/Receipts"));
    }

    @Test
    void updateSettings_savesFolderBaseAndImportFolders() throws Exception {
      doNothing().when(receiptService).updateReceiptsFolderBase("documents/receipts");
      doNothing().when(receiptService).updateReceiptsImportFolders(List.of("Scans/Receipts"));
      when(receiptService.getReceiptsImportFolders()).thenReturn(List.of("Scans/Receipts"));

      mockMvc
          .perform(
              put("/api/receipts/settings")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      "{\"folderBase\":\"documents/receipts\",\"importFolders\":[\"Scans/Receipts\"]}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.folderBase").value("documents/receipts"))
          .andExpect(jsonPath("$.importFolders[0]").value("Scans/Receipts"));

      verify(receiptService).updateReceiptsFolderBase("documents/receipts");
      verify(receiptService).updateReceiptsImportFolders(List.of("Scans/Receipts"));
    }

    @Test
    void updateSettings_returnsBadRequestForBlankFolder() throws Exception {
      mockMvc
          .perform(
              put("/api/receipts/settings")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"folderBase\":\"\"}"))
          .andExpect(status().isBadRequest());
    }
  }
}
