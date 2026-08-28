package com.bookie.ledger.infrastructure;

import com.bookie.catalog.activity.domain.FinancialActivity;
import com.bookie.catalog.category.domain.LegacyCategoryMap;
import com.bookie.catalog.category.domain.NeutralCategory;
import com.bookie.ledger.application.LedgerReferenceResolver;
import com.bookie.ledger.application.ResolvedLedgerReferences;
import com.bookie.ledger.application.ResolvedLegacyReferences;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
@RequiredArgsConstructor
class JpaLedgerReferenceResolver implements LedgerReferenceResolver {

  private final EntityManager entityManager;
  private final JdbcTemplate jdbcTemplate;

  @Override
  public ResolvedLedgerReferences resolveFromLegacy(
      Long activityId, Long legacyCategoryId, Long legacyCounterpartyId) {
    FinancialActivity activity = requiredActivity(activityId);
    LegacyCategoryMap categoryMap = entityManager.find(LegacyCategoryMap.class, legacyCategoryId);
    if (categoryMap == null) {
      throw new IllegalStateException(
          "Legacy category is missing its neutral mapping: " + legacyCategoryId);
    }
    Long counterpartyId =
        legacyCounterpartyId == null
            ? null
            : requireSingleId(
                """
                SELECT counterparty_id
                FROM legacy_payer_map
                WHERE payer_id = ?
                """,
                legacyCounterpartyId,
                "Legacy counterparty is missing its normalized mapping");
    return new ResolvedLedgerReferences(activity, categoryMap.getNeutralCategory(), counterpartyId);
  }

  @Override
  public ResolvedLegacyReferences resolveForUnified(
      Long activityId, Long neutralCategoryId, Long counterpartyId, LocalDate effectiveOn) {
    FinancialActivity activity = requiredActivity(activityId);
    NeutralCategory neutralCategory = entityManager.find(NeutralCategory.class, neutralCategoryId);
    if (neutralCategory == null) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Neutral category is missing: " + neutralCategoryId);
    }
    List<Long> legacyCategoryIds =
        jdbcTemplate.query(
            """
            SELECT mapping.legacy_category_id
            FROM category_reporting_mappings mapping
            JOIN activity_reporting_profile_assignments assignment
              ON assignment.reporting_profile_id = mapping.reporting_profile_id
            WHERE assignment.activity_id = ?
              AND assignment.effective_from <= ?
              AND (assignment.effective_to IS NULL OR assignment.effective_to >= ?)
              AND mapping.neutral_category_id = ?
              AND mapping.active = TRUE
            """,
            (result, rowNumber) -> result.getLong(1),
            activityId,
            effectiveOn,
            effectiveOn,
            neutralCategoryId);
    if (legacyCategoryIds.size() != 1) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "Neutral category has no unique legacy mapping for the selected activity and date");
    }
    Long legacyCounterpartyId =
        counterpartyId == null
            ? null
            : requireSingleId(
                """
                SELECT payer_id
                FROM legacy_payer_map
                WHERE counterparty_id = ?
                """,
                counterpartyId,
                "Counterparty is missing its legacy compatibility mapping");
    return new ResolvedLegacyReferences(
        activity, neutralCategory, legacyCategoryIds.getFirst(), legacyCounterpartyId);
  }

  private Long requireSingleId(String sql, Long id, String failureMessage) {
    List<Long> ids = jdbcTemplate.query(sql, (result, rowNumber) -> result.getLong(1), id);
    if (ids.size() != 1) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, failureMessage + ": " + id);
    }
    return ids.getFirst();
  }

  private FinancialActivity requiredActivity(Long activityId) {
    FinancialActivity activity = entityManager.find(FinancialActivity.class, activityId);
    if (activity == null || !activity.isActive()) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Financial activity is missing or inactive: " + activityId);
    }
    return activity;
  }
}
