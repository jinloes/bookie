package com.bookie.intake.application;

import com.bookie.intake.domain.InboxArtifactType;
import com.bookie.intake.domain.InboxItem;
import com.bookie.intake.domain.InboxState;
import com.bookie.intake.domain.LegacyInboxMap;
import com.bookie.intake.domain.LegacyPendingTable;
import com.bookie.model.ExpenseSource;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LegacyInboxReadSelector {

  private final LegacyInboxMapStore mapStore;
  private final IntakeReadMode readMode;

  public LegacyInboxReadSelector(
      LegacyInboxMapStore mapStore,
      @Value("${bookie.intake.read-mode:UNIFIED}") String configuredReadMode) {
    this.mapStore = mapStore;
    this.readMode = IntakeReadMode.valueOf(configuredReadMode.trim().toUpperCase(Locale.ROOT));
  }

  @Transactional(readOnly = true)
  public <T> List<T> select(
      LegacyPendingTable table,
      List<T> legacyRows,
      Function<T, Long> idExtractor,
      Function<T, LegacyInboxSnapshot> snapshotFactory) {
    if (readMode == IntakeReadMode.LEGACY) {
      return legacyRows;
    }

    Map<Long, T> legacyById = new LinkedHashMap<>();
    for (T row : legacyRows) {
      Long id = idExtractor.apply(row);
      if (id == null || legacyById.put(id, row) != null) {
        throw new IllegalStateException("Legacy pending read contains a missing or duplicate ID");
      }
    }

    List<LegacyInboxMap> activeMaps = mapStore.findActiveByTable(table);
    List<Long> unifiedOrder = activeMaps.stream().map(map -> map.getKey().getId()).toList();
    if (activeMaps.size() != legacyById.size()
        || !legacyById.keySet().equals(new java.util.LinkedHashSet<>(unifiedOrder))) {
      throw new IllegalStateException("Legacy and durable intake reads differ for " + table.name());
    }
    for (LegacyInboxMap map : activeMaps) {
      T legacy = legacyById.get(map.getKey().getId());
      if (!matches(snapshotFactory.apply(legacy), map.getInboxItem())) {
        throw new IllegalStateException(
            "Legacy and durable intake fields differ for "
                + table.name()
                + " ID "
                + map.getKey().getId());
      }
    }
    if (readMode == IntakeReadMode.COMPARE) {
      return legacyRows;
    }
    return unifiedOrder.stream().map(legacyById::get).toList();
  }

  private boolean matches(LegacyInboxSnapshot legacy, InboxItem durable) {
    ExpenseSource expectedOrigin =
        legacy.getOrigin() == null ? ExpenseSource.MANUAL : legacy.getOrigin();
    return Objects.equals(expectedOrigin, durable.getOrigin())
        && Objects.equals(legacy.getLegacySourceType(), durable.getLegacySourceType())
        && Objects.equals(legacy.getLegacySourceId(), durable.getLegacySourceId())
        && Objects.equals(legacy.getRawStatus(), durable.getRawStatus())
        && stateMatches(legacy.getRawStatus(), durable.getState())
        && Objects.equals(legacy.getProposedDirection(), durable.getProposedDirection())
        && Objects.equals(legacy.getSubject(), durable.getSubject())
        && sameAmount(legacy.getProposedAmount(), durable.getProposedAmount())
        && Objects.equals(legacy.getProposedDescription(), durable.getProposedDescription())
        && Objects.equals(legacy.getProposedDate(), durable.getProposedDate())
        && Objects.equals(legacy.getProposedCategory(), durable.getProposedCategory())
        && Objects.equals(legacy.getProposedPropertyName(), durable.getProposedPropertyName())
        && Objects.equals(
            legacy.getProposedCounterpartyName(), durable.getProposedCounterpartyName())
        && Objects.equals(legacy.getProposedSourceLabel(), durable.getProposedSourceLabel())
        && Objects.equals(legacy.getLegacyActivityId(), durable.getLegacyActivityId())
        && Objects.equals(legacy.getLegacyCategoryId(), durable.getLegacyCategoryId())
        && Objects.equals(legacy.getLegacyPropertyId(), durable.getLegacyPropertyId())
        && Objects.equals(legacy.getLegacyCounterpartyId(), durable.getLegacyCounterpartyId())
        && Objects.equals(legacy.getConfiguredActivityId(), durable.getConfiguredActivityId())
        && legacy.isClassificationAmbiguous() == durable.isClassificationAmbiguous()
        && Objects.equals(legacy.getErrorMessage(), durable.getErrorMessage())
        && Objects.equals(legacy.getReceiptExternalId(), durable.getReceiptExternalId())
        && Objects.equals(legacy.getReceiptFileName(), durable.getReceiptFileName())
        && Objects.equals(legacy.getCreatedAt(), durable.getCreatedAt())
        && sorted(legacy.getUnrecognizedAliases()).equals(durableAliases(durable));
  }

  private boolean stateMatches(String rawStatus, InboxState durableState) {
    if ("PROCESSING".equals(rawStatus)) {
      return durableState == InboxState.QUEUED || durableState == InboxState.PROCESSING;
    }
    try {
      return InboxState.valueOf(rawStatus) == durableState;
    } catch (IllegalArgumentException | NullPointerException invalidLegacyStatus) {
      return false;
    }
  }

  private boolean sameAmount(BigDecimal legacy, BigDecimal durable) {
    return legacy == null ? durable == null : durable != null && legacy.compareTo(durable) == 0;
  }

  private List<String> durableAliases(InboxItem item) {
    return sorted(
        item.getArtifacts().stream()
            .filter(artifact -> artifact.getType() == InboxArtifactType.UNRECOGNIZED_ALIAS)
            .map(artifact -> artifact.getTextValue())
            .toList());
  }

  private List<String> sorted(List<String> values) {
    List<String> sorted = new ArrayList<>(values == null ? List.of() : values);
    sorted.sort(Comparator.nullsFirst(String::compareTo));
    return sorted;
  }
}
