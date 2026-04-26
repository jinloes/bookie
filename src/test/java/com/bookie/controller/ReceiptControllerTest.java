package com.bookie.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.bookie.model.ExpenseSource;
import com.bookie.model.PendingExpense;
import com.bookie.model.PendingExpenseStatus;
import com.bookie.model.ReceiptDto;
import com.bookie.model.UploadReceiptResponse;
import com.bookie.service.PendingExpenseService;
import com.bookie.service.PendingExpenseService.FindOrCreateResult;
import com.bookie.service.ReceiptParseQueueService;
import com.bookie.service.ReceiptService;
import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ReceiptController.class)
class ReceiptControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private ReceiptService receiptService;
  @MockitoBean private ReceiptParseQueueService receiptParseQueueService;
  @MockitoBean private PendingExpenseService pendingExpenseService;

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
    void parse_createsNewPendingAndQueues() throws Exception {
      when(receiptService.isConnected()).thenReturn(true);
      when(receiptService.getReceiptName("item-1")).thenReturn("bill.pdf");

      PendingExpense pending =
          PendingExpense.builder()
              .id(10L)
              .sourceId("item-1")
              .sourceType(ExpenseSource.RECEIPT)
              .status(PendingExpenseStatus.PROCESSING)
              .createdAt(LocalDateTime.now())
              .build();
      when(pendingExpenseService.findOrCreate("item-1", ExpenseSource.RECEIPT, "bill.pdf"))
          .thenReturn(new FindOrCreateResult(pending, false));

      mockMvc
          .perform(post("/api/receipts/item-1/parse"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.id").value(10))
          .andExpect(jsonPath("$.status").value("PROCESSING"));

      verify(receiptParseQueueService).processReceipt(10L, "item-1");
    }

    @Test
    void parse_returnsExistingIfAlreadyProcessing() throws Exception {
      when(receiptService.isConnected()).thenReturn(true);
      when(receiptService.getReceiptName("item-1")).thenReturn("bill.pdf");

      PendingExpense existing =
          PendingExpense.builder()
              .id(7L)
              .sourceId("item-1")
              .status(PendingExpenseStatus.PROCESSING)
              .createdAt(LocalDateTime.now())
              .build();
      when(pendingExpenseService.findOrCreate("item-1", ExpenseSource.RECEIPT, "bill.pdf"))
          .thenReturn(new FindOrCreateResult(existing, true));

      mockMvc
          .perform(post("/api/receipts/item-1/parse"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.id").value(7))
          .andExpect(jsonPath("$.status").value("PROCESSING"));

      verify(receiptParseQueueService, never()).processReceipt(any(), any());
    }

    @Test
    void parse_dismissesAndReParsesReadyEntry() throws Exception {
      when(receiptService.isConnected()).thenReturn(true);
      when(receiptService.getReceiptName("item-1")).thenReturn("bill.pdf");

      PendingExpense newPending =
          PendingExpense.builder()
              .id(10L)
              .sourceId("item-1")
              .sourceType(ExpenseSource.RECEIPT)
              .status(PendingExpenseStatus.PROCESSING)
              .createdAt(LocalDateTime.now())
              .build();
      when(pendingExpenseService.findOrCreate("item-1", ExpenseSource.RECEIPT, "bill.pdf"))
          .thenReturn(new FindOrCreateResult(newPending, false));

      mockMvc
          .perform(post("/api/receipts/item-1/parse"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.id").value(10))
          .andExpect(jsonPath("$.status").value("PROCESSING"));

      verify(receiptParseQueueService).processReceipt(10L, "item-1");
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
          .andExpect(header().string("Content-Disposition", "inline; filename=\"bill.pdf\""))
          .andExpect(content().contentType("application/pdf"));
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
    void getSettings_returnsFolderBase() throws Exception {
      when(receiptService.getReceiptsFolderBase()).thenReturn("bookie/taxes");

      mockMvc
          .perform(get("/api/receipts/settings"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.folderBase").value("bookie/taxes"));
    }

    @Test
    void updateSettings_savesFolderBase() throws Exception {
      doNothing().when(receiptService).updateReceiptsFolderBase("documents/receipts");

      mockMvc
          .perform(
              put("/api/receipts/settings")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"folderBase\":\"documents/receipts\"}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.folderBase").value("documents/receipts"));

      verify(receiptService).updateReceiptsFolderBase("documents/receipts");
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
