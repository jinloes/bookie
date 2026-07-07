package com.bookie.repository;

import com.bookie.model.ExpenseCategory;
import com.bookie.model.Payer;
import com.bookie.model.PayerCategoryHistory;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface PayerCategoryHistoryRepository extends JpaRepository<PayerCategoryHistory, Long> {

  List<PayerCategoryHistory> findByPayer_IdOrderByOccurrencesDesc(Long payerId);

  void deleteByPayerId(Long payerId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<PayerCategoryHistory> findByPayerAndCategory(Payer payer, ExpenseCategory category);
}
