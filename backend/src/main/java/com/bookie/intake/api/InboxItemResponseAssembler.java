package com.bookie.intake.api;

import com.bookie.catalog.activity.api.FinancialActivityResponse;
import com.bookie.catalog.activity.application.ActivityCatalog;
import com.bookie.catalog.activity.domain.FinancialActivity;
import com.bookie.catalog.category.api.FinancialCategoryResponse;
import com.bookie.catalog.category.application.CategoryCatalog;
import com.bookie.intake.domain.InboxItem;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Resolves the catalog references an inbox item proposes into its API representation. */
@Component
@RequiredArgsConstructor
class InboxItemResponseAssembler {

  private final ActivityCatalog activityCatalog;
  private final CategoryCatalog categoryCatalog;

  InboxItemResponse toResponse(InboxItem item) {
    return toResponses(List.of(item)).getFirst();
  }

  List<InboxItemResponse> toResponses(List<InboxItem> items) {
    Map<Long, FinancialActivity> activities =
        activityCatalog.findAll().stream()
            .collect(Collectors.toMap(FinancialActivity::getId, Function.identity()));
    return items.stream().map(item -> toResponse(item, activities)).toList();
  }

  private InboxItemResponse toResponse(InboxItem item, Map<Long, FinancialActivity> activities) {
    return InboxItemResponse.builder()
        .id(item.getId())
        .origin(item.getOrigin())
        .sourceId(item.getLegacySourceId())
        .immutableSourceId(item.getImmutableSourceId())
        .state(item.getState())
        .externalSyncState(item.getExternalSyncState())
        .proposedDirection(item.getProposedDirection())
        .subject(item.getSubject())
        .amount(item.getProposedAmount())
        .description(item.getProposedDescription())
        .date(item.getProposedDate())
        .proposedCategory(item.getProposedCategory())
        .propertyName(item.getProposedPropertyName())
        .counterpartyName(item.getProposedCounterpartyName())
        .sourceLabel(item.getProposedSourceLabel())
        .activity(
            Optional.ofNullable(item.getLegacyActivityId())
                .map(activities::get)
                .map(FinancialActivityResponse::from)
                .orElse(null))
        .category(
            Optional.ofNullable(item.getLegacyCategoryId())
                .flatMap(categoryCatalog::findLegacyCategoryById)
                .map(FinancialCategoryResponse::from)
                .orElse(null))
        .counterpartyId(item.getLegacyCounterpartyId())
        .classificationAmbiguous(item.isClassificationAmbiguous())
        .receiptExternalId(item.getReceiptExternalId())
        .receiptFileName(item.getReceiptFileName())
        .financialTransactionId(item.getFinancialTransactionId())
        .errorMessage(item.getErrorMessage())
        .createdAt(item.getCreatedAt())
        .updatedAt(item.getUpdatedAt())
        .version(item.getVersion())
        .build();
  }
}
