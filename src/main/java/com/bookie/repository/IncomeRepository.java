package com.bookie.repository;

import com.bookie.model.Income;
import com.bookie.model.Property;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface IncomeRepository extends JpaRepository<Income, Long> {

  List<Income> findByProperty(Property property);

  List<Income> findBySourceIdIn(List<String> sourceIds);

  Optional<Income> findByReceiptOneDriveId(String receiptOneDriveId);

  @Query("SELECT COALESCE(SUM(i.amount), 0) FROM Income i")
  BigDecimal getTotalIncome();

  /** Detaches a deleted property from all income records without removing them. */
  @Modifying
  @Query("UPDATE Income i SET i.property = null WHERE i.property.id = :propertyId")
  void clearPropertyById(@Param("propertyId") Long propertyId);
}
