package com.bookie.ledger.infrastructure;

import com.bookie.catalog.activity.domain.FinancialActivity;
import com.bookie.catalog.category.domain.LegacyCategoryMap;
import com.bookie.catalog.category.domain.NeutralCategory;
import com.bookie.ledger.application.LedgerReferenceResolver;
import com.bookie.ledger.application.ResolvedTransactionReferences;
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
  public ResolvedTransactionReferences resolve(
      Long activityId, Long neutralCategoryId, Long counterpartyId, LocalDate effectiveOn) {
    FinancialActivity activity = requiredActivity(activityId);
    NeutralCategory neutralCategory = entityManager.find(NeutralCategory.class, neutralCategoryId);
    if (neutralCategory == null) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Neutral category is missing: " + neutralCategoryId);
    }
    List<Long> financialCategoryIds =
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
    if (financialCategoryIds.size() != 1) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "Neutral category has no unique mapping for the selected activity and date");
    }
    if (counterpartyId != null && !counterpartyExists(counterpartyId)) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Counterparty is missing: " + counterpartyId);
    }
    return new ResolvedTransactionReferences(
        activity, neutralCategory, financialCategoryIds.getFirst(), counterpartyId);
  }

  @Override
  public ResolvedTransactionReferences resolveFinancialCategory(
      Long activityId, Long financialCategoryId) {
    FinancialActivity activity = requiredActivity(activityId);
    LegacyCategoryMap categoryMap =
        entityManager.find(LegacyCategoryMap.class, financialCategoryId);
    if (categoryMap == null) {
      throw new IllegalStateException(
          "Financial category is missing its neutral mapping: " + financialCategoryId);
    }
    return new ResolvedTransactionReferences(
        activity, categoryMap.getNeutralCategory(), financialCategoryId, null);
  }

  private boolean counterpartyExists(Long counterpartyId) {
    Long matches =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM counterparties WHERE id = ?", Long.class, counterpartyId);
    return matches != null && matches > 0;
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
