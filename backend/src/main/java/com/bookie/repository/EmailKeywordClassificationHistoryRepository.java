package com.bookie.repository;

import com.bookie.model.EmailKeywordClassificationHistory;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

@Repository
public interface EmailKeywordClassificationHistoryRepository
    extends JpaRepository<EmailKeywordClassificationHistory, Long> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  List<EmailKeywordClassificationHistory> findByKeywordInAndActivityIdAndFinancialCategoryId(
      Collection<String> keywords, Long activityId, Long financialCategoryId);

  List<EmailKeywordClassificationHistory> findByKeywordInOrderByOccurrencesDesc(
      Collection<String> keywords);

  List<EmailKeywordClassificationHistory> findByKeywordInAndActivityIdOrderByOccurrencesDesc(
      Collection<String> keywords, Long activityId);
}
