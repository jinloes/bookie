package com.bookie.service;

import com.bookie.model.Income;
import com.bookie.repository.IncomeRepository;
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
public class IncomeService {

  private final IncomeRepository incomeRepository;

  public List<Income> findAll() {
    return incomeRepository.findAll(Sort.by(Sort.Direction.DESC, "date"));
  }

  public Income findById(Long id) {
    return incomeRepository
        .findById(id)
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Income not found: " + id));
  }

  @Transactional
  public Income save(Income income) {
    return incomeRepository.save(income);
  }

  @Transactional
  public Income update(Long id, Income updated) {
    Income existing = findById(id);
    existing.setAmount(updated.getAmount());
    existing.setDescription(updated.getDescription());
    existing.setDate(updated.getDate());
    existing.setSource(updated.getSource());
    existing.setProperty(updated.getProperty());
    return incomeRepository.save(existing);
  }

  @Transactional
  public void delete(Long id) {
    incomeRepository.deleteById(id);
  }

  public BigDecimal getTotalIncome() {
    return incomeRepository.getTotalIncome();
  }
}
