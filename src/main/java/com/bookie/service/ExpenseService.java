package com.bookie.service;

import com.bookie.model.Expense;
import com.bookie.repository.ExpenseRepository;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class ExpenseService {

  private final ExpenseRepository expenseRepository;
  private final PropertyHistoryService propertyHistoryService;

  public List<Expense> findAll() {
    return expenseRepository.findAll(Sort.by(Sort.Direction.DESC, "date"));
  }

  public Expense findById(Long id) {
    return expenseRepository
        .findById(id)
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Expense not found: " + id));
  }

  @Transactional
  public Expense save(Expense expense) {
    Expense saved = expenseRepository.save(expense);
    propertyHistoryService.record(saved);
    return saved;
  }

  @Transactional
  public Expense update(Long id, Expense updated) {
    Expense existing = findById(id);
    existing.setAmount(updated.getAmount());
    existing.setDescription(updated.getDescription());
    existing.setDate(updated.getDate());
    existing.setCategory(updated.getCategory());
    existing.setProperty(updated.getProperty());
    existing.setPayer(updated.getPayer());
    existing.setReceiptOneDriveId(updated.getReceiptOneDriveId());
    existing.setReceiptFileName(updated.getReceiptFileName());
    Expense saved = expenseRepository.save(existing);
    propertyHistoryService.record(saved);
    return saved;
  }

  @Transactional
  public void delete(Long id) {
    expenseRepository.deleteById(id);
  }

  public BigDecimal getTotalExpenses() {
    return expenseRepository.getTotalExpenses();
  }
}
