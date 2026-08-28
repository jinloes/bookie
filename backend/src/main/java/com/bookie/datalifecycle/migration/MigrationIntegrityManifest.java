package com.bookie.datalifecycle.migration;

import java.math.BigDecimal;
import java.util.List;
import java.util.SortedMap;

public record MigrationIntegrityManifest(
    int manifestVersion,
    String schemaVersion,
    String applicationVersion,
    String sourceDatabaseHash,
    SortedMap<String, Long> tableCounts,
    SortedMap<String, List<String>> tableColumns,
    SortedMap<String, List<String>> tableIdentityColumns,
    List<FinancialAggregate> financialAggregates,
    SortedMap<String, Long> referenceCounts,
    List<ConstraintCheck> uniquenessChecks,
    List<ConstraintCheck> orphanChecks,
    SortedMap<String, List<String>> rowHashes,
    SortedMap<String, SortedMap<String, SortedMap<String, String>>> preservedColumnHashes,
    String contentChecksum) {

  public static final int CURRENT_VERSION = 1;

  public record FinancialAggregate(
      String table,
      String direction,
      int year,
      Long activityId,
      Long categoryId,
      long rowCount,
      BigDecimal amountTotal) {}

  public record ConstraintCheck(String name, long violationCount) {}
}
