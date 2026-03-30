package com.bookie.service;

import com.bookie.model.Payer;
import com.bookie.repository.PayerRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PayerService {

  private final PayerRepository payerRepository;

  public List<Payer> findAll() {
    return payerRepository.findAll();
  }

  public Optional<Payer> findByName(String name) {
    return payerRepository.findByNameIgnoreCase(name);
  }

  public Payer findById(Long id) {
    return payerRepository
        .findById(id)
        .orElseThrow(() -> new RuntimeException("Payer not found with id: " + id));
  }

  public Payer save(Payer payer) {
    return payerRepository.save(payer);
  }

  public Payer update(Long id, Payer updated) {
    Payer existing = findById(id);
    existing.setName(updated.getName());
    existing.setType(updated.getType());
    return payerRepository.save(existing);
  }

  public void delete(Long id) {
    payerRepository.deleteById(id);
  }
}
