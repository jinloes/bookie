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
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

/**
 * Manages PDF receipt files stored in OneDrive.
 *
 * <p>Uploaded receipts land in {@code {base}/pending/}. When the associated expense is saved,
 * {@link #moveTaxesFolder} relocates the file to {@code {base}/{year}/} using the expense date's
 * year. OneDrive moves preserve the item ID, so the stored {@code receiptOneDriveId} remains valid
 * after the move.
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

    List<ReceiptDto> receipts = new ArrayList<>();

    // Pending folder
    for (DriveItem file : listChildren(driveId, base + "/" + PENDING_SUBFOLDER)) {
      if (file.getFolder() != null) {
        continue;
      }
      Long expenseId = findLinkedExpenseId(file.getId());
      Long incomeId = expenseId != null ? null : findLinkedIncomeId(file.getId());
      receipts.add(toDto(file, 0, expenseId, incomeId, true));
    }

    // Year subfolders
    for (DriveItem folder : listChildren(driveId, base)) {
      if (folder.getFolder() == null || PENDING_SUBFOLDER.equalsIgnoreCase(folder.getName())) {
        continue;
      }
      int year = parseYear(folder.getName());
      if (year < 0) {
        continue;
      }
      for (DriveItem file : listChildrenById(driveId, folder.getId())) {
        if (file.getFolder() != null) {
          continue;
        }
        Long expenseId = findLinkedExpenseId(file.getId());
        Long incomeId = expenseId != null ? null : findLinkedIncomeId(file.getId());
        receipts.add(toDto(file, year, expenseId, incomeId, false));
      }
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
    } catch (Exception ignored) {
      // Folder does not exist yet; create it below
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
    return expenseRepository.findByReceiptOneDriveId(driveItemId).map(Expense::getId).orElse(null);
  }

  private Long findLinkedIncomeId(String driveItemId) {
    return incomeRepository.findByReceiptOneDriveId(driveItemId).map(Income::getId).orElse(null);
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
}
