package com.bookie.catalog.household.api;

import com.bookie.catalog.household.domain.HouseholdMember;

public record HouseholdMemberResponse(Long id, String name, boolean active, boolean system) {

  public static HouseholdMemberResponse from(HouseholdMember member) {
    if (member == null) {
      return null;
    }
    return new HouseholdMemberResponse(
        member.getId(), member.getName(), member.isActive(), member.getSystemKey() != null);
  }
}
