package com.bookie.compatibility;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    properties = {"bookie.auto-import.enabled=false", "bookie.intake.worker.enabled=false"})
@AutoConfigureMockMvc
class LegacyOpenApiContractTest {

  @Autowired private MockMvc mockMvc;

  @Test
  void splitResponseTypesPreserveLegacyOperationsAndSchemaNames() throws Exception {
    mockMvc
        .perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.paths['/api/expenses'].get.operationId").value("getExpenses"))
        .andExpect(jsonPath("$.paths['/api/expenses'].post.operationId").value("createExpense"))
        .andExpect(
            jsonPath("$.paths['/api/expenses/{id}'].get.operationId").value("getExpenseById"))
        .andExpect(jsonPath("$.paths['/api/expenses/{id}'].put.operationId").value("updateExpense"))
        .andExpect(
            jsonPath("$.paths['/api/expenses/{id}'].delete.operationId").value("deleteExpense"))
        .andExpect(
            jsonPath("$.paths['/api/expenses/total'].get.operationId").value("getExpensesTotal"))
        .andExpect(
            jsonPath("$.paths['/api/expenses/categories'].get.operationId")
                .value("getExpenseCategories"))
        .andExpect(jsonPath("$.paths['/api/incomes'].get.operationId").value("getIncomes"))
        .andExpect(jsonPath("$.paths['/api/incomes'].post.operationId").value("createIncome"))
        .andExpect(jsonPath("$.paths['/api/incomes/{id}'].get.operationId").value("getIncomeById"))
        .andExpect(jsonPath("$.paths['/api/incomes/{id}'].put.operationId").value("updateIncome"))
        .andExpect(
            jsonPath("$.paths['/api/incomes/{id}'].delete.operationId").value("deleteIncome"))
        .andExpect(
            jsonPath("$.paths['/api/incomes/import/venmo'].post.operationId")
                .value("importVenmoIncomeCsv"))
        .andExpect(
            jsonPath("$.paths['/api/incomes/total'].get.operationId").value("getIncomesTotal"))
        .andExpect(
            jsonPath("$.paths['/api/incomes/pending'].get.operationId").value("getPendingIncomes"))
        .andExpect(
            jsonPath("$.paths['/api/incomes/pending/{id}'].get.operationId")
                .value("getPendingIncomeById"))
        .andExpect(
            jsonPath("$.paths['/api/incomes/pending/{id}'].delete.operationId")
                .value("rejectPendingIncome"))
        .andExpect(
            jsonPath("$.paths['/api/incomes/pending/{id}/accept'].post.operationId")
                .value("acceptPendingIncome"))
        .andExpect(
            jsonPath("$.paths['/api/pending-expenses'].get.operationId")
                .value("getPendingExpenses"))
        .andExpect(
            jsonPath("$.paths['/api/pending-expenses/events'].get.operationId")
                .value("subscribePendingExpenseEvents"))
        .andExpect(
            jsonPath("$.paths['/api/pending-expenses/{id}/save'].post.operationId")
                .value("createExpenseFromPendingExpense"))
        .andExpect(
            jsonPath("$.paths['/api/pending-expenses/{id}/save-income'].post.operationId")
                .value("createIncomeFromPendingExpense"))
        .andExpect(
            jsonPath("$.paths['/api/pending-expenses/{id}/retry'].post.operationId")
                .value("retryPendingExpense"))
        .andExpect(
            jsonPath("$.paths['/api/pending-expenses/{id}'].delete.operationId")
                .value("dismissPendingExpense"))
        .andExpect(
            jsonPath("$.paths['/api/financial-categories'].get.operationId")
                .value("getFinancialCategories"))
        .andExpect(jsonPath("$.components.schemas.ExpenseResponse.properties.id").exists())
        .andExpect(
            jsonPath("$.components.schemas.ExpenseResponse.properties.financialCategory").exists())
        .andExpect(jsonPath("$.components.schemas.IncomeResponse.properties.id").exists())
        .andExpect(
            jsonPath("$.components.schemas.IncomeResponse.properties.receiptOneDriveId").exists())
        .andExpect(jsonPath("$.components.schemas.TotalAmountResponse.properties.total").exists())
        .andExpect(
            jsonPath(
                    "$.components.schemas.PendingIncomeResponse.properties.classificationAmbiguous")
                .exists())
        .andExpect(
            jsonPath("$.components.schemas.PendingExpenseResponse.properties.counterpartyName")
                .exists())
        .andExpect(
            jsonPath("$.components.schemas.VenmoIncomeImportResponse.properties.importedRows")
                .exists())
        .andExpect(
            jsonPath("$.components.schemas.FinancialCategoryResponse.properties.taxTreatment")
                .exists());
  }
}
