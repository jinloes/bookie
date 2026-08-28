package com.bookie.catalog.reportpolicy.infrastructure;

import com.bookie.catalog.reportpolicy.domain.ReportingProfile;
import com.bookie.catalog.reportpolicy.domain.ReportingProfileKey;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
interface ReportingProfileRepository extends JpaRepository<ReportingProfile, Long> {

  Optional<ReportingProfile> findByKeyAndActiveTrue(ReportingProfileKey key);
}
