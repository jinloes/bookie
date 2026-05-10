package com.bookie.repository;

import com.bookie.model.EmailKeywordPropertyHistory;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

@Repository
public interface EmailKeywordPropertyHistoryRepository
    extends JpaRepository<EmailKeywordPropertyHistory, Long> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<EmailKeywordPropertyHistory> findByKeywordAndPropertyId(String keyword, Long propertyId);

  void deleteByPropertyId(Long propertyId);

  List<EmailKeywordPropertyHistory> findByKeywordInOrderByOccurrencesDesc(
      Collection<String> keywords);
}
