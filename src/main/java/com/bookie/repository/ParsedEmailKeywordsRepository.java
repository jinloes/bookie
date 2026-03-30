package com.bookie.repository;

import com.bookie.model.ParsedEmailKeywords;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ParsedEmailKeywordsRepository extends JpaRepository<ParsedEmailKeywords, Long> {

  List<ParsedEmailKeywords> findBySourceId(String sourceId);

  void deleteBySourceId(String sourceId);
}
