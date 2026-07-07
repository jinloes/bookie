package com.bookie.service;

import com.bookie.model.Payer;
import com.bookie.model.UpsertPayerRequest;
import com.bookie.repository.EmailKeywordPayerHistoryRepository;
import com.bookie.repository.ExpenseRepository;
import com.bookie.repository.PayerCategoryHistoryRepository;
import com.bookie.repository.PayerPropertyHistoryRepository;
import com.bookie.repository.PayerRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@Service
@RequiredArgsConstructor
public class PayerService {

  private final PayerRepository payerRepository;
  private final ExpenseRepository expenseRepository;
  private final PayerCategoryHistoryRepository payerCategoryHistoryRepo;
  private final PayerPropertyHistoryRepository payerPropertyHistoryRepo;
  private final EmailKeywordPayerHistoryRepository keywordPayerHistoryRepo;

  public List<Payer> findAll() {
    return payerRepository.findAll();
  }

  public Optional<Payer> findByName(String name) {
    return payerRepository.findByNameIgnoreCase(name);
  }

  public Payer findById(Long id) {
    return payerRepository
        .findById(id)
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payer not found: " + id));
  }

  public Payer save(Payer payer) {
    return payerRepository.save(payer);
  }

  public Payer create(UpsertPayerRequest req) {
    Payer payer =
        Payer.builder()
            .name(req.name())
            .type(req.type())
            .aliases(req.aliases() != null ? req.aliases() : List.of())
            .accounts(req.accounts() != null ? new HashSet<>(req.accounts()) : new HashSet<>())
            .build();
    return payerRepository.save(payer);
  }

  public Payer update(Long id, UpsertPayerRequest req) {
    Payer existing = findById(id);
    existing.setName(req.name());
    existing.setType(req.type());
    existing.setAliases(req.aliases() != null ? req.aliases() : List.of());
    existing.setAccounts(req.accounts() != null ? new HashSet<>(req.accounts()) : new HashSet<>());
    return payerRepository.save(existing);
  }

  @Transactional
  public void delete(Long id) {
    expenseRepository.clearPayerById(id);
    payerCategoryHistoryRepo.deleteByPayerId(id);
    payerPropertyHistoryRepo.deleteByPayerId(id);
    keywordPayerHistoryRepo.deleteByPayerId(id);
    payerRepository.deleteById(id);
  }

  @Transactional
  public void addAliasIfAbsent(String payerName, String alias) {
    payerRepository
        .findByNameIgnoreCase(payerName)
        .ifPresent(
            payer -> {
              boolean alreadyPresent =
                  payer.getName().equalsIgnoreCase(alias)
                      || payer.getAliases().stream().anyMatch(a -> a.equalsIgnoreCase(alias));
              if (!alreadyPresent) {
                payer.getAliases().add(alias);
                payerRepository.save(payer);
                log.info("Auto-saved alias '{}' for payer '{}'", alias, payerName);
              }
            });
  }
}
