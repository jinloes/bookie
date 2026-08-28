package com.bookie.catalog.household.application;

import com.bookie.catalog.household.domain.HouseholdMember;
import java.util.List;
import java.util.Optional;

public interface HouseholdMemberStore {

  List<HouseholdMember> findAllByOrderByNameAsc();

  Optional<HouseholdMember> findById(Long id);

  Optional<HouseholdMember> findByNameIgnoreCase(String name);

  Optional<HouseholdMember> findBySystemKey(String systemKey);

  <S extends HouseholdMember> S save(S member);
}
