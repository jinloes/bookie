package com.bookie.intake.infrastructure;

import com.bookie.intake.domain.LegacyInboxMap;
import com.bookie.intake.domain.LegacyPendingKey;
import com.bookie.intake.domain.LegacyPendingTable;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface LegacyInboxMapRepository extends JpaRepository<LegacyInboxMap, LegacyPendingKey> {

  @Query(
      """
      SELECT mapping
      FROM LegacyInboxMap mapping
      WHERE mapping.key.table = :table
        AND mapping.inboxItem.state NOT IN (
          com.bookie.intake.domain.InboxState.SAVED,
          com.bookie.intake.domain.InboxState.DISMISSED
        )
        AND mapping.key.id = (
          SELECT MAX(newest.key.id)
          FROM LegacyInboxMap newest
          WHERE newest.key.table = mapping.key.table
            AND newest.inboxItem.id = mapping.inboxItem.id
        )
      ORDER BY mapping.inboxItem.createdAt DESC, mapping.key.id DESC
      """)
  List<LegacyInboxMap> findActiveByTable(@Param("table") LegacyPendingTable table);
}
