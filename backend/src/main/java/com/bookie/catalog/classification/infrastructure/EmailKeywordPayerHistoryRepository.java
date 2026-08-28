package com.bookie.catalog.classification.infrastructure;

import com.bookie.catalog.counterparty.domain.Counterparty;
import com.bookie.model.EmailKeywordPayerHistory;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

@Repository
public interface EmailKeywordPayerHistoryRepository
    extends JpaRepository<EmailKeywordPayerHistory, Long> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<EmailKeywordPayerHistory> findByKeywordAndPayer(String keyword, Counterparty payer);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  List<EmailKeywordPayerHistory> findByKeywordInAndPayer(
      Collection<String> keywords, Counterparty payer);

  List<EmailKeywordPayerHistory> findByKeywordInOrderByOccurrencesDesc(Collection<String> keywords);

  void deleteByPayerId(Long payerId);

  long countByPayerId(Long payerId);
}
