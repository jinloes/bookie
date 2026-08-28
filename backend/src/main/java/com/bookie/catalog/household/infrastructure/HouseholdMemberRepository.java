package com.bookie.catalog.household.infrastructure;

import com.bookie.catalog.household.application.HouseholdMemberStore;
import com.bookie.catalog.household.domain.HouseholdMember;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface HouseholdMemberRepository
    extends JpaRepository<HouseholdMember, Long>, HouseholdMemberStore {

  List<HouseholdMember> findAllByOrderByNameAsc();

  Optional<HouseholdMember> findByNameIgnoreCase(String name);

  Optional<HouseholdMember> findBySystemKey(String systemKey);
}
