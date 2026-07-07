package com.bookie.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Stores the SHA-256 hash of every uploaded receipt PDF for content-based duplicate detection. */
@Entity
@Table(name = "receipt_hashes")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReceiptHash {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  /** Lowercase hex SHA-256 of the raw PDF bytes. */
  @Column(nullable = false, unique = true, length = 64)
  private String sha256;

  /** OneDrive item ID of the uploaded file (used to look up the existing ReceiptDto). */
  @Column(nullable = false)
  private String driveItemId;

  @Column(nullable = false)
  private LocalDateTime uploadedAt;
}
