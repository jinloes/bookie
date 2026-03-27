package com.bookie.service;

import com.bookie.model.Income;
import com.bookie.repository.IncomeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class IncomeService {

    private final IncomeRepository incomeRepository;

    public List<Income> findAll() {
        return incomeRepository.findAll();
    }

    public Income findById(Long id) {
        return incomeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Income not found with id: " + id));
    }

    public Income save(Income income) {
        return incomeRepository.save(income);
    }

    public Income update(Long id, Income updated) {
        Income existing = findById(id);
        existing.setAmount(updated.getAmount());
        existing.setDescription(updated.getDescription());
        existing.setDate(updated.getDate());
        existing.setSource(updated.getSource());
        existing.setPropertyName(updated.getPropertyName());
        return incomeRepository.save(existing);
    }

    public void delete(Long id) {
        incomeRepository.deleteById(id);
    }

    public BigDecimal getTotalIncome() {
        return incomeRepository.getTotalIncome();
    }
}
