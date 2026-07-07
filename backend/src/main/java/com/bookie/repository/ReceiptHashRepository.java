package com.bookie.repository;

import com.bookie.model.ReceiptHash;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReceiptHashRepository extends JpaRepository<ReceiptHash, Long> {

  Optional<ReceiptHash> findBySha256(String sha256);

  void deleteByDriveItemId(String driveItemId);
}
