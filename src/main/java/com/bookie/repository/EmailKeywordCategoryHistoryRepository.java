package com.bookie.repository;

import com.bookie.model.EmailKeywordCategoryHistory;
import com.bookie.model.ExpenseCategory;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EmailKeywordCategoryHistoryRepository
    extends JpaRepository<EmailKeywordCategoryHistory, Long> {

  Optional<EmailKeywordCategoryHistory> findByKeywordAndCategory(
      String keyword, ExpenseCategory category);

  List<EmailKeywordCategoryHistory> findByKeywordInOrderByOccurrencesDesc(
      Collection<String> keywords);
}
