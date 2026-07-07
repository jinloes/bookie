package com.bookie.service;

import com.bookie.model.CreateIncomeRequest;
import com.bookie.model.Income;
import com.bookie.model.Property;
import com.bookie.model.UpdateIncomeRequest;
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
  private final PropertyService propertyService;

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
  public Income create(CreateIncomeRequest req) {
    Property property =
        req.propertyId() != null ? propertyService.findById(req.propertyId()) : null;
    Income income =
        Income.builder()
            .amount(req.amount())
            .description(req.description())
            .date(req.date())
            .source(req.source())
            .property(property)
            .sourceType(req.sourceType())
            .receiptOneDriveId(req.receiptOneDriveId())
            .receiptFileName(req.receiptFileName())
            .build();
    return incomeRepository.save(income);
  }

  @Transactional
  public Income save(Income income) {
    return incomeRepository.save(income);
  }

  @Transactional
  public Income update(Long id, UpdateIncomeRequest req) {
    Property property =
        req.propertyId() != null ? propertyService.findById(req.propertyId()) : null;
    Income existing = findById(id);
    existing.setAmount(req.amount());
    existing.setDescription(req.description());
    existing.setDate(req.date());
    existing.setSource(req.source());
    existing.setProperty(property);
    return incomeRepository.save(existing);
  }

  @Transactional
  public void delete(Long id) {
    incomeRepository.deleteById(id);
  }

  @Transactional
  public void updateSourceId(Long id, String newSourceId) {
    incomeRepository
        .findById(id)
        .ifPresent(
            income -> {
              income.setSourceId(newSourceId);
              incomeRepository.save(income);
            });
  }

  public BigDecimal getTotalIncome() {
    return incomeRepository.getTotalIncome();
  }
}
