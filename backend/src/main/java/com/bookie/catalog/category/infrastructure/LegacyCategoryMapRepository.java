package com.bookie.catalog.category.infrastructure;

import com.bookie.catalog.category.domain.LegacyCategoryMap;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
interface LegacyCategoryMapRepository extends JpaRepository<LegacyCategoryMap, Long> {}
