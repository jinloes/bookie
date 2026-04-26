package com.bookie.repository;

import com.bookie.model.PayerPropertyHistory;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

@Repository
public interface PayerPropertyHistoryRepository extends JpaRepository<PayerPropertyHistory, Long> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<PayerPropertyHistory> findByPayerIdAndPropertyId(Long payerId, Long propertyId);

  List<PayerPropertyHistory> findByPayerIdOrderByOccurrencesDesc(Long payerId);

  List<PayerPropertyHistory> findByPayer_NameIgnoreCaseOrderByOccurrencesDesc(String payerName);
}
