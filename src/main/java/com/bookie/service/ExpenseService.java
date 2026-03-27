package com.bookie.service;

import com.bookie.model.Expense;
import com.bookie.repository.ExpenseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ExpenseService {

    private final ExpenseRepository expenseRepository;

    public List<Expense> findAll() {
        return expenseRepository.findAll();
    }

    public Expense findById(Long id) {
        return expenseRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Expense not found with id: " + id));
    }

    public Expense save(Expense expense) {
        return expenseRepository.save(expense);
    }

    public Expense update(Long id, Expense updated) {
        Expense existing = findById(id);
        existing.setAmount(updated.getAmount());
        existing.setDescription(updated.getDescription());
        existing.setDate(updated.getDate());
        existing.setCategory(updated.getCategory());
        existing.setPropertyName(updated.getPropertyName());
        existing.setPayer(updated.getPayer());
        return expenseRepository.save(existing);
    }

    public void delete(Long id) {
        expenseRepository.deleteById(id);
    }

    public BigDecimal getTotalExpenses() {
        return expenseRepository.getTotalExpenses();
    }
}
