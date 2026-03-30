package com.bookie.repository;

import com.bookie.model.EmailKeywordPropertyHistory;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EmailKeywordPropertyHistoryRepository
    extends JpaRepository<EmailKeywordPropertyHistory, Long> {

  Optional<EmailKeywordPropertyHistory> findByKeywordAndPropertyId(String keyword, Long propertyId);

  List<EmailKeywordPropertyHistory> findByKeywordInOrderByOccurrencesDesc(
      Collection<String> keywords);
}
