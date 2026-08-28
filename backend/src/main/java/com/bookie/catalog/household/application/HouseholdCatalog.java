package com.bookie.catalog.household.application;

import com.bookie.catalog.household.domain.HouseholdMember;
import java.util.List;

public interface HouseholdCatalog {

  String DEFAULT_HOUSEHOLD_KEY = "DEFAULT_HOUSEHOLD";

  List<HouseholdMember> findAll();

  HouseholdMember findById(Long id);

  HouseholdMember getDefaultHouseholdMember();

  HouseholdMember create(UpsertHouseholdMemberCommand command);

  HouseholdMember update(Long id, UpsertHouseholdMemberCommand command);
}
