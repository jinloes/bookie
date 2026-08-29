package com.bookie.service;

import com.bookie.integrations.IntegrationException;
import com.bookie.integrations.IntegrationFailureKind;
import com.bookie.integrations.onedrive.OneDriveItem;
import com.bookie.integrations.onedrive.OneDrivePort;
import com.bookie.integrations.outlook.OutlookAuthorization;
import com.bookie.ledger.compatibility.LegacyLedgerSynchronizer;
import com.bookie.model.Expense;
import com.bookie.model.Income;
import com.bookie.model.OutlookSettings;
import com.bookie.model.ReceiptDto;
import com.bookie.model.ReceiptHash;
import com.bookie.model.UploadReceiptResponse;
import com.bookie.repository.ExpenseRepository;
import com.bookie.repository.IncomeRepository;
import com.bookie.repository.OutlookSettingsRepository;
import com.bookie.repository.PendingExpenseRepository;
import com.bookie.repository.ReceiptHashRepository;
import jakarta.transaction.Transactional;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

/**
 * Manages PDF receipt files stored in OneDrive.
 *
 * <p>Uploaded receipts land in {@code {base}/pending/}. When the associated expense is saved,
 * {@link #moveTaxesFolder} relocates the file to {@code {base}/{year}/} using the expense date's
 * year. OneDrive moves preserve the item ID, so the stored {@code receiptOneDriveId} remains valid
 * after the move.
 *
 * <p>{@link #listReceipts()} also scans any additional folders configured via {@link
 * #updateReceiptsImportFolders(List)} — this lets receipts placed directly into OneDrive by
 * something other than Bookie (e.g. a phone scanning app) show up for parsing without first being
 * re-uploaded through the app.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReceiptService {

  private static final String PENDING_SUBFOLDER = "pending";

  private final OneDrivePort oneDrive;
  private final OutlookAuthorization outlookAuthorization;
  private final ExpenseRepository expenseRepository;
  private final IncomeRepository incomeRepository;
  private final OutlookSettingsRepository outlookSettingsRepository;
  private final ReceiptHashRepository receiptHashRepository;
  private final PendingExpenseRepository pendingExpenseRepository;
  private final LegacyLedgerSynchronizer ledgerSynchronizer;

  public boolean isConnected() {
    return outlookAuthorization.isConnected();
  }

  private static String sha256Hex(InputStream content) throws java.io.IOException {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] buffer = new byte[8192];
      int read;
      while ((read = content.read(buffer)) != -1) {
        digest.update(buffer, 0, read);
      }
      return HexFormat.of().formatHex(digest.digest());
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }

  /** Returns the configured base folder path (e.g., {@code "bookie/taxes"}). */
  public String getReceiptsFolderBase() {
    return outlookSettingsRepository
        .findById(1L)
        .map(OutlookSettings::getReceiptsFolderBase)
        .filter(StringUtils::isNotBlank)
        .orElse(OutlookSettings.DEFAULT_RECEIPTS_FOLDER);
  }

  /** Updates the base folder path where receipts are stored in OneDrive. */
  public void updateReceiptsFolderBase(String folderBase) {
    OutlookSettings settings =
        outlookSettingsRepository
            .findById(1L)
            .orElseGet(
                () ->
                    OutlookSettings.builder()
                        .id(1L)
                        .folderSettings(new ArrayList<>())
                        .receiptsFolderBase(OutlookSettings.DEFAULT_RECEIPTS_FOLDER)
                        .build());
    settings.setReceiptsFolderBase(folderBase);
    outlookSettingsRepository.save(settings);
  }

  /** Returns the additional OneDrive folders (outside the managed base) scanned for receipts. */
  public List<String> getReceiptsImportFolders() {
    return outlookSettingsRepository
        .findById(1L)
        .map(OutlookSettings::getReceiptsImportFolders)
        .orElse(List.of());
  }

  /**
   * Updates the additional OneDrive folders scanned for receipts already placed there (e.g. by a
   * phone scanning app), separate from the app-managed {@code {base}/pending} upload folder.
   */
  public void updateReceiptsImportFolders(List<String> importFolders) {
    List<String> cleaned =
        CollectionUtils.emptyIfNull(importFolders).stream()
            .map(String::trim)
            .filter(StringUtils::isNotBlank)
            .distinct()
            .toList();
    OutlookSettings settings =
        outlookSettingsRepository
            .findById(1L)
            .orElseGet(
                () ->
                    OutlookSettings.builder()
                        .id(1L)
                        .folderSettings(new ArrayList<>())
                        .receiptsFolderBase(OutlookSettings.DEFAULT_RECEIPTS_FOLDER)
                        .build());
    settings.setReceiptsImportFolders(new ArrayList<>(cleaned));
    outlookSettingsRepository.save(settings);
  }

  /**
   * Uploads a PDF receipt to the pending folder ({@code {base}/pending/}).
   *
   * <p>Duplicate detection uses a two-step strategy:
   *
   * <ol>
   *   <li><b>Content hash (primary):</b> the SHA-256 of the raw bytes is checked against the {@code
   *       receipt_hashes} table. This catches duplicates regardless of filename and works for files
   *       that have already been moved to a year subfolder.
   *   <li><b>Pending folder by name:</b> checks the pending folder for a file with the same name,
   *       as a fallback for receipts uploaded before hash tracking was introduced.
   * </ol>
   *
   * If a duplicate is found, the existing {@link ReceiptDto} is returned with {@code
   * duplicate=true} and no upload is performed.
   */
  public UploadReceiptResponse uploadReceipt(String filename, byte[] content) {
    String sha256 = sha256Hex(content);

    // Primary check: content hash — catches duplicates regardless of filename or folder location
    Optional<ReceiptHash> existingHash = receiptHashRepository.findBySha256(sha256);
    if (existingHash.isPresent()) {
      String driveItemId = existingHash.get().getDriveItemId();
      Long expenseId = findLinkedExpenseId(driveItemId);
      Long incomeId = expenseId != null ? null : findLinkedIncomeId(driveItemId);
      log.info("Duplicate receipt detected by content hash: {}", filename);
      try {
        Optional<OneDriveItem> item = oneDrive.getItem(driveItemId);
        if (item.isPresent()) {
          return new UploadReceiptResponse(toDto(item.get(), 0, expenseId, incomeId, true), true);
        }
      } catch (Exception e) {
        log.warn("Could not fetch duplicate item {}: {}", driveItemId, e.getMessage());
      }
      return new UploadReceiptResponse(
          new ReceiptDto(driveItemId, filename, 0, null, null, expenseId, incomeId, true), true);
    }

    // Fallback: name-based check in the pending folder (covers pre-hash pending receipts)
    String base = getReceiptsFolderBase();
    String pendingPath = base + "/" + PENDING_SUBFOLDER;
    Optional<OneDriveItem> inPending = findByName(pendingPath, filename);
    if (inPending.isPresent()) {
      OneDriveItem item = inPending.get();
      Long expenseId = findLinkedExpenseId(item.id());
      Long incomeId = expenseId != null ? null : findLinkedIncomeId(item.id());
      log.info("Duplicate receipt detected by name in pending folder: {}", filename);
      return new UploadReceiptResponse(toDto(item, 0, expenseId, incomeId, true), true);
    }

    OneDriveItem uploaded =
        oneDrive.upload(pendingPath + "/" + filename, new ByteArrayInputStream(content));

    receiptHashRepository.save(
        ReceiptHash.builder()
            .sha256(sha256)
            .driveItemId(uploaded.id())
            .uploadedAt(LocalDateTime.now())
            .build());
    log.info("Uploaded receipt to pending: {}", filename);
    return new UploadReceiptResponse(toDto(uploaded, 0, null, null, true), false);
  }

  private static String sha256Hex(byte[] content) {
    try {
      byte[] hash = MessageDigest.getInstance("SHA-256").digest(content);
      return HexFormat.of().formatHex(hash);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }

  /**
   * Idempotently moves a receipt for a durable job. Unlike the legacy convenience method, failures
   * are returned to the worker so they can be retried or surfaced for manual review.
   */
  public void moveTaxesFolderForJob(String itemId, int year) {
    String base = getReceiptsFolderBase();
    String targetPath = base + "/" + year;
    Optional<OneDriveItem> current = oneDrive.getItem(itemId);
    if (current
        .map(OneDriveItem::parentPath)
        .filter(StringUtils::isNotBlank)
        .map(path -> path.replace('\\', '/'))
        .filter(path -> path.endsWith("/" + targetPath) || path.endsWith(targetPath))
        .isPresent()) {
      return;
    }
    String folderId = oneDrive.ensureFolder(base, String.valueOf(year));
    oneDrive.move(itemId, folderId);
  }

  /** Verifies the current remote bytes before a durable receipt move. */
  public boolean hasReceiptChecksum(String itemId, String expectedSha256) {
    if (StringUtils.isBlank(expectedSha256)) {
      return false;
    }
    try (InputStream content = oneDrive.download(itemId)) {
      return content != null && expectedSha256.equals(sha256Hex(content));
    } catch (java.io.IOException e) {
      throw IntegrationException.builder()
          .kind(IntegrationFailureKind.TRANSIENT)
          .message("Could not checksum receipt before move")
          .cause(e)
          .build();
    }
  }

  /**
   * Moves a receipt from the pending folder to {@code {base}/{year}/}. Called when the expense is
   * saved so the file is organized by tax year.
   *
   * <p>OneDrive moves preserve the item ID, so the {@code receiptOneDriveId} stored on the expense
   * does not need to change after this call.
   */
  public void moveTaxesFolder(String itemId, int year) {
    String base = getReceiptsFolderBase();
    String targetPath = base + "/" + year;
    try {
      moveTaxesFolderForJob(itemId, year);
      log.info("Moved receipt {} to {}", itemId, targetPath);
    } catch (Exception e) {
      // Non-fatal: the expense is already saved, and the receipt is still accessible in
      // the pending folder via the same item ID.
      log.warn(
          "Could not move receipt {} to {} — expense saved, file stays in pending: {}",
          itemId,
          targetPath,
          e.getMessage());
    }
  }

  /**
   * Lists all receipts from both the pending folder and all year subfolders, annotated with their
   * linked expense IDs.
   */
  public List<ReceiptDto> listReceipts() {
    String base = getReceiptsFolderBase();

    // Use a map keyed by item ID to dedupe in case an import folder overlaps with the managed
    // pending/year folders.
    Map<String, DriveItemWithYear> filesById = new LinkedHashMap<>();

    // Pending folder
    for (OneDriveItem file : oneDrive.listChildren(base + "/" + PENDING_SUBFOLDER)) {
      if (file.folder()) {
        continue;
      }
      filesById.put(file.id(), new DriveItemWithYear(file, 0, true));
    }

    // Year subfolders — plus any loose files sitting directly in the base folder itself (e.g. a
    // file a user drags/drops or copies straight into {base} without knowing about the pending/
    // convention). Those are treated as unorganized, same as the pending folder.
    for (OneDriveItem child : oneDrive.listChildren(base)) {
      if (!child.folder()) {
        filesById.put(child.id(), new DriveItemWithYear(child, 0, true));
        continue;
      }
      if (PENDING_SUBFOLDER.equalsIgnoreCase(child.name())) {
        continue;
      }
      int year = parseYear(child.name());
      if (year < 0) {
        continue;
      }
      for (OneDriveItem file : oneDrive.listChildrenById(child.id())) {
        if (file.folder()) {
          continue;
        }
        filesById.put(file.id(), new DriveItemWithYear(file, year, false));
      }
    }

    // Additional import folders — files that were placed directly in OneDrive by something
    // other than Bookie's own upload flow (e.g. a phone scanning app). Treated the same as the
    // pending folder: unorganized (year 0), awaiting parse-and-save which moves them into a
    // year folder like any other receipt.
    for (String importFolder : getReceiptsImportFolders()) {
      for (OneDriveItem file : oneDrive.listChildren(importFolder)) {
        if (file.folder()) {
          continue;
        }
        filesById.putIfAbsent(file.id(), new DriveItemWithYear(file, 0, true));
      }
    }

    List<DriveItemWithYear> filesByYear = new ArrayList<>(filesById.values());
    if (filesByYear.isEmpty()) {
      return List.of();
    }

    List<String> driveItemIds =
        filesByYear.stream()
            .map(DriveItemWithYear::item)
            .map(OneDriveItem::id)
            .filter(StringUtils::isNotBlank)
            .distinct()
            .toList();

    Map<String, Long> expenseIdsByReceiptId = findExpenseIdsByReceiptId(driveItemIds);
    Map<String, Long> incomeIdsByReceiptId = findIncomeIdsByReceiptId(driveItemIds);

    List<ReceiptDto> receipts = new ArrayList<>(filesByYear.size());
    for (DriveItemWithYear fileWithYear : filesByYear) {
      String fileId = fileWithYear.item().id();
      Long expenseId = fileId != null ? expenseIdsByReceiptId.get(fileId) : null;
      Long incomeId = expenseId != null || fileId == null ? null : incomeIdsByReceiptId.get(fileId);
      receipts.add(
          toDto(
              fileWithYear.item(),
              fileWithYear.year(),
              expenseId,
              incomeId,
              fileWithYear.pending()));
    }
    return receipts;
  }

  /** Returns the raw content stream for the given OneDrive item. */
  public InputStream getReceiptContent(String itemId) {
    return oneDrive.download(itemId);
  }

  /** Returns the filename of the given OneDrive item, or {@code null} if not found. */
  public String getReceiptName(String itemId) {
    try {
      return oneDrive.getItem(itemId).map(OneDriveItem::name).orElse(null);
    } catch (Exception e) {
      log.warn("Could not fetch receipt name for {}: {}", itemId, e.getMessage());
      return null;
    }
  }

  /**
   * Deletes a receipt and all associated data.
   *
   * <p>In order:
   *
   * <ol>
   *   <li>Removes the file from OneDrive (best-effort; logs a warning on failure).
   *   <li>Deletes the linked expense, if any.
   *   <li>Dismisses any pending expense whose {@code sourceId} matches the item ID.
   *   <li>Removes the content-hash record so the file can be re-uploaded later.
   * </ol>
   */
  @Transactional
  public void deleteReceipt(String itemId) {
    try {
      oneDrive.delete(itemId);
      log.info("Deleted OneDrive item {}", itemId);
    } catch (Exception e) {
      log.warn("Could not delete OneDrive item {}: {}", itemId, e.getMessage());
    }

    expenseRepository
        .findByReceiptOneDriveId(itemId)
        .ifPresent(
            expense -> {
              ledgerSynchronizer.tombstoneExpense(expense.getId());
              expenseRepository.deleteById(expense.getId());
              log.info("Deleted expense {} linked to receipt {}", expense.getId(), itemId);
            });

    incomeRepository
        .findByReceiptOneDriveId(itemId)
        .ifPresent(
            income -> {
              ledgerSynchronizer.tombstoneIncome(income.getId());
              incomeRepository.deleteById(income.getId());
              log.info("Deleted income {} linked to receipt {}", income.getId(), itemId);
            });

    pendingExpenseRepository
        .findBySourceId(itemId)
        .ifPresent(pending -> pendingExpenseRepository.deleteById(pending.getId()));

    receiptHashRepository.deleteByDriveItemId(itemId);
  }

  private Optional<OneDriveItem> findByName(String folderPath, String filename) {
    if (StringUtils.isBlank(filename)) {
      return Optional.empty();
    }
    return oneDrive.listChildren(folderPath).stream()
        .filter(item -> filename.equalsIgnoreCase(item.name()))
        .findFirst();
  }

  private Long findLinkedExpenseId(String driveItemId) {
    return expenseRepository
        .findByReceiptOneDriveId(driveItemId)
        .or(() -> expenseRepository.findBySourceId(driveItemId))
        .map(Expense::getId)
        .orElse(null);
  }

  private Long findLinkedIncomeId(String driveItemId) {
    return incomeRepository
        .findByReceiptOneDriveId(driveItemId)
        .map(Income::getId)
        .or(
            () ->
                incomeRepository.findBySourceIdIn(List.of(driveItemId)).stream()
                    .findFirst()
                    .map(Income::getId))
        .orElse(null);
  }

  private Map<String, Long> findExpenseIdsByReceiptId(List<String> driveItemIds) {
    if (driveItemIds.isEmpty()) {
      return Map.of();
    }
    Map<String, Long> byOneDriveId =
        expenseRepository.findByReceiptOneDriveIdIn(driveItemIds).stream()
            .filter(expense -> StringUtils.isNotBlank(expense.getReceiptOneDriveId()))
            .collect(
                Collectors.toMap(
                    Expense::getReceiptOneDriveId, Expense::getId, (left, right) -> left));
    // A receipt's sourceId equals its OneDrive item ID, so this catches expenses saved before
    // receiptOneDriveId was populated (or by any path that only set sourceId), preventing an
    // already-expensed receipt from still being offered for parsing.
    Map<String, Long> bySourceId =
        expenseRepository.findBySourceIdIn(driveItemIds).stream()
            .collect(Collectors.toMap(Expense::getSourceId, Expense::getId, (left, right) -> left));
    Map<String, Long> merged = new HashMap<>(bySourceId);
    merged.putAll(byOneDriveId);
    return merged;
  }

  private Map<String, Long> findIncomeIdsByReceiptId(List<String> driveItemIds) {
    if (driveItemIds.isEmpty()) {
      return Map.of();
    }
    Map<String, Long> byOneDriveId =
        incomeRepository.findByReceiptOneDriveIdIn(driveItemIds).stream()
            .filter(income -> StringUtils.isNotBlank(income.getReceiptOneDriveId()))
            .collect(
                Collectors.toMap(
                    Income::getReceiptOneDriveId, Income::getId, (left, right) -> left));
    Map<String, Long> bySourceId =
        incomeRepository.findBySourceIdIn(driveItemIds).stream()
            .collect(Collectors.toMap(Income::getSourceId, Income::getId, (left, right) -> left));
    Map<String, Long> merged = new HashMap<>(bySourceId);
    merged.putAll(byOneDriveId);
    return merged;
  }

  private int parseYear(String name) {
    try {
      int year = Integer.parseInt(name);
      return (year >= 2000 && year <= 2100) ? year : -1;
    } catch (NumberFormatException e) {
      return -1;
    }
  }

  private ReceiptDto toDto(
      OneDriveItem item, int year, Long expenseId, Long incomeId, boolean pending) {
    String uploadedAt = item.created();
    return new ReceiptDto(
        item.id(), item.name(), year, item.webUrl(), uploadedAt, expenseId, incomeId, pending);
  }

  private record DriveItemWithYear(OneDriveItem item, int year, boolean pending) {}
}
