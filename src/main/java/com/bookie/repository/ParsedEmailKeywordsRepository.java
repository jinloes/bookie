package com.bookie.repository;

import com.bookie.model.ParsedEmailKeywords;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ParsedEmailKeywordsRepository extends JpaRepository<ParsedEmailKeywords, Long> {

  List<ParsedEmailKeywords> findBySourceId(String sourceId);

  // Bulk DELETE so it runs immediately. The derived `deleteBySourceId` loads each entity into the
  // persistence context and queues a delete; Hibernate's action queue then flushes inserts before
  // deletes, so a delete-then-insert in the same transaction would hit the unique constraint on
  // (source_id, keyword) against the still-present old rows.
  // clearAutomatically=true so any stale entity snapshots in the persistence context cannot
  // resurrect deleted rows on a subsequent flush.
  @Modifying(clearAutomatically = true)
  @Query("DELETE FROM ParsedEmailKeywords k WHERE k.sourceId = :sourceId")
  int deleteBySourceId(@Param("sourceId") String sourceId);
}
