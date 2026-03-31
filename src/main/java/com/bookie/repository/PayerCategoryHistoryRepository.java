package com.bookie.repository;

import com.bookie.model.ExpenseCategory;
import com.bookie.model.Payer;
import com.bookie.model.PayerCategoryHistory;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PayerCategoryHistoryRepository extends JpaRepository<PayerCategoryHistory, Long> {

  List<PayerCategoryHistory> findByPayer_IdOrderByOccurrencesDesc(Long payerId);

  Optional<PayerCategoryHistory> findByPayerAndCategory(Payer payer, ExpenseCategory category);
}
