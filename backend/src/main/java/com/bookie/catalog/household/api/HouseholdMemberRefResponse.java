package com.bookie.catalog.household.api;

import com.bookie.catalog.household.domain.HouseholdMember;

public record HouseholdMemberRefResponse(Long id, String name) {

  public static HouseholdMemberRefResponse from(HouseholdMember member) {
    if (member == null) {
      return null;
    }
    return new HouseholdMemberRefResponse(member.getId(), member.getName());
  }
}
