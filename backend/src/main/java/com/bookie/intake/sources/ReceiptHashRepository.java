package com.bookie.intake.sources;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReceiptHashRepository extends JpaRepository<ReceiptHash, Long> {

  Optional<ReceiptHash> findBySha256(String sha256);

  Optional<ReceiptHash> findByDriveItemId(String driveItemId);

  void deleteByDriveItemId(String driveItemId);
}
