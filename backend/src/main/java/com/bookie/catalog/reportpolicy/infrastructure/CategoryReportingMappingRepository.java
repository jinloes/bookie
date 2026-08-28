package com.bookie.catalog.reportpolicy.infrastructure;

import com.bookie.catalog.reportpolicy.domain.CategoryReportingMapping;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
interface CategoryReportingMappingRepository extends JpaRepository<CategoryReportingMapping, Long> {

  List<CategoryReportingMapping> findAllByNeutralCategoryIdAndReportingProfileId(
      Long neutralCategoryId, Long reportingProfileId);
}
