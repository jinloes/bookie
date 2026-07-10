package com.bookie.service;

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
import com.microsoft.graph.models.DriveItem;
import com.microsoft.graph.models.Folder;
import com.microsoft.graph.models.ItemReference;
import com.microsoft.graph.serviceclient.GraphServiceClient;
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

  private final GraphServiceClient graphClient;
  private final MsalTokenService msalTokenService;
  private final ExpenseRepository expenseRepository;
  private final IncomeRepository incomeRepository;
  private final OutlookSettingsRepository outlookSettingsRepository;
  private final ReceiptHashRepository receiptHashRepository;
  private final PendingExpenseRepository pendingExpenseRepository;

  public boolean isConnected() {
    return msalTokenService.isConnected();
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
        String driveId = graphClient.me().drive().get().getId();
        DriveItem item =
            graphClient.drives().byDriveId(driveId).items().byDriveItemId(driveItemId).get();
        if (item != null) {
          return new UploadReceiptResponse(toDto(item, 0, expenseId, incomeId, true), true);
        }
      } catch (Exception e) {
        log.warn("Could not fetch duplicate item {}: {}", driveItemId, e.getMessage());
      }
      return new UploadReceiptResponse(
          new ReceiptDto(driveItemId, filename, 0, null, null, expenseId, incomeId, true), true);
    }

    // Fallback: name-based check in the pending folder (covers pre-hash pending receipts)
    String base = getReceiptsFolderBase();
    String driveId = graphClient.me().drive().get().getId();
    String pendingPath = base + "/" + PENDING_SUBFOLDER;
    Optional<DriveItem> inPending = findByName(driveId, pendingPath, filename);
    if (inPending.isPresent()) {
      DriveItem item = inPending.get();
      Long expenseId = findLinkedExpenseId(item.getId());
      Long incomeId = expenseId != null ? null : findLinkedIncomeId(item.getId());
      log.info("Duplicate receipt detected by name in pending folder: {}", filename);
      return new UploadReceiptResponse(toDto(item, 0, expenseId, incomeId, true), true);
    }

    DriveItem uploaded =
        graphClient
            .drives()
            .byDriveId(driveId)
            .items()
            .byDriveItemId("root:/" + pendingPath + "/" + filename + ":")
            .content()
            .put(new ByteArrayInputStream(content));

    receiptHashRepository.save(
        ReceiptHash.builder()
            .sha256(sha256)
            .driveItemId(uploaded.getId())
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
      String driveId = graphClient.me().drive().get().getId();
      String folderId = ensureFolderExists(driveId, base, String.valueOf(year));

      DriveItem update = new DriveItem();
      ItemReference ref = new ItemReference();
      ref.setId(folderId);
      update.setParentReference(ref);
      graphClient.drives().byDriveId(driveId).items().byDriveItemId(itemId).patch(update);
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
    String driveId;
    try {
      driveId = graphClient.me().drive().get().getId();
    } catch (Exception e) {
      log.warn("Could not get drive ID: {}", e.getMessage());
      return List.of();
    }

    // Use a map keyed by item ID to dedupe in case an import folder overlaps with the managed
    // pending/year folders.
    Map<String, DriveItemWithYear> filesById = new LinkedHashMap<>();

    // Pending folder
    for (DriveItem file : listChildren(driveId, base + "/" + PENDING_SUBFOLDER)) {
      if (file.getFolder() != null) {
        continue;
      }
      filesById.put(file.getId(), new DriveItemWithYear(file, 0, true));
    }

    // Year subfolders — plus any loose files sitting directly in the base folder itself (e.g. a
    // file a user drags/drops or copies straight into {base} without knowing about the pending/
    // convention). Those are treated as unorganized, same as the pending folder.
    for (DriveItem child : listChildren(driveId, base)) {
      if (child.getFolder() == null) {
        filesById.put(child.getId(), new DriveItemWithYear(child, 0, true));
        continue;
      }
      if (PENDING_SUBFOLDER.equalsIgnoreCase(child.getName())) {
        continue;
      }
      int year = parseYear(child.getName());
      if (year < 0) {
        continue;
      }
      for (DriveItem file : listChildrenById(driveId, child.getId())) {
        if (file.getFolder() != null) {
          continue;
        }
        filesById.put(file.getId(), new DriveItemWithYear(file, year, false));
      }
    }

    // Additional import folders — files that were placed directly in OneDrive by something
    // other than Bookie's own upload flow (e.g. a phone scanning app). Treated the same as the
    // pending folder: unorganized (year 0), awaiting parse-and-save which moves them into a
    // year folder like any other receipt.
    for (String importFolder : getReceiptsImportFolders()) {
      for (DriveItem file : listChildren(driveId, importFolder)) {
        if (file.getFolder() != null) {
          continue;
        }
        filesById.putIfAbsent(file.getId(), new DriveItemWithYear(file, 0, true));
      }
    }

    List<DriveItemWithYear> filesByYear = new ArrayList<>(filesById.values());
    if (filesByYear.isEmpty()) {
      return List.of();
    }

    List<String> driveItemIds =
        filesByYear.stream()
            .map(DriveItemWithYear::item)
            .map(DriveItem::getId)
            .filter(StringUtils::isNotBlank)
            .distinct()
            .toList();

    Map<String, Long> expenseIdsByReceiptId = findExpenseIdsByReceiptId(driveItemIds);
    Map<String, Long> incomeIdsByReceiptId = findIncomeIdsByReceiptId(driveItemIds);

    List<ReceiptDto> receipts = new ArrayList<>(filesByYear.size());
    for (DriveItemWithYear fileWithYear : filesByYear) {
      String fileId = fileWithYear.item().getId();
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
    String driveId = graphClient.me().drive().get().getId();
    return graphClient.drives().byDriveId(driveId).items().byDriveItemId(itemId).content().get();
  }

  /** Returns the filename of the given OneDrive item, or {@code null} if not found. */
  public String getReceiptName(String itemId) {
    try {
      String driveId = graphClient.me().drive().get().getId();
      DriveItem item = graphClient.drives().byDriveId(driveId).items().byDriveItemId(itemId).get();
      return item != null ? item.getName() : null;
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
      String driveId = graphClient.me().drive().get().getId();
      graphClient.drives().byDriveId(driveId).items().byDriveItemId(itemId).delete();
      log.info("Deleted OneDrive item {}", itemId);
    } catch (Exception e) {
      log.warn("Could not delete OneDrive item {}: {}", itemId, e.getMessage());
    }

    expenseRepository
        .findByReceiptOneDriveId(itemId)
        .ifPresent(
            expense -> {
              expenseRepository.deleteById(expense.getId());
              log.info("Deleted expense {} linked to receipt {}", expense.getId(), itemId);
            });

    incomeRepository
        .findByReceiptOneDriveId(itemId)
        .ifPresent(
            income -> {
              incomeRepository.deleteById(income.getId());
              log.info("Deleted income {} linked to receipt {}", income.getId(), itemId);
            });

    pendingExpenseRepository
        .findBySourceId(itemId)
        .ifPresent(pending -> pendingExpenseRepository.deleteById(pending.getId()));

    receiptHashRepository.deleteByDriveItemId(itemId);
  }

  /**
   * Gets the OneDrive ID of {@code {parentPath}/{name}}, creating the folder if it doesn't exist.
   */
  private String ensureFolderExists(String driveId, String parentPath, String name) {
    String fullPath = parentPath + "/" + name;
    try {
      DriveItem existing =
          graphClient
              .drives()
              .byDriveId(driveId)
              .items()
              .byDriveItemId("root:/" + fullPath + ":")
              .get();
      if (existing != null && existing.getId() != null) {
        return existing.getId();
      }
    } catch (Exception e) {
      // Folder does not exist yet; create it below
      log.debug("Folder lookup failed for '{}': {}", fullPath, e.getMessage());
    }

    DriveItem newFolder = new DriveItem();
    newFolder.setName(name);
    newFolder.setFolder(new Folder());
    DriveItem created =
        graphClient
            .drives()
            .byDriveId(driveId)
            .items()
            .byDriveItemId("root:/" + parentPath + ":")
            .children()
            .post(newFolder);
    return created.getId();
  }

  private Optional<DriveItem> findByName(String driveId, String folderPath, String filename) {
    if (StringUtils.isBlank(filename)) {
      return Optional.empty();
    }
    return listChildren(driveId, folderPath).stream()
        .filter(item -> filename.equalsIgnoreCase(item.getName()))
        .findFirst();
  }

  private List<DriveItem> listChildren(String driveId, String folderPath) {
    try {
      var resp =
          graphClient
              .drives()
              .byDriveId(driveId)
              .items()
              .byDriveItemId("root:/" + folderPath + ":")
              .children()
              .get();
      return resp != null && resp.getValue() != null ? resp.getValue() : List.of();
    } catch (Exception e) {
      log.warn("Could not list '{}' (may not exist yet): {}", folderPath, e.getMessage());
      return List.of();
    }
  }

  private List<DriveItem> listChildrenById(String driveId, String itemId) {
    try {
      var resp =
          graphClient.drives().byDriveId(driveId).items().byDriveItemId(itemId).children().get();
      return resp != null && resp.getValue() != null ? resp.getValue() : List.of();
    } catch (Exception e) {
      log.warn("Could not list folder by ID '{}': {}", itemId, e.getMessage());
      return List.of();
    }
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
      DriveItem item, int year, Long expenseId, Long incomeId, boolean pending) {
    String uploadedAt =
        Optional.ofNullable(item.getCreatedDateTime()).map(Object::toString).orElse(null);
    return new ReceiptDto(
        item.getId(),
        item.getName(),
        year,
        item.getWebUrl(),
        uploadedAt,
        expenseId,
        incomeId,
        pending);
  }

  private record DriveItemWithYear(DriveItem item, int year, boolean pending) {}
}
