package com.bookie.repository;

import com.bookie.model.EmailKeywordPayerHistory;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EmailKeywordPayerHistoryRepository
    extends JpaRepository<EmailKeywordPayerHistory, Long> {

  Optional<EmailKeywordPayerHistory> findByKeywordAndPayerName(String keyword, String payerName);

  List<EmailKeywordPayerHistory> findByKeywordInOrderByOccurrencesDesc(Collection<String> keywords);
}
