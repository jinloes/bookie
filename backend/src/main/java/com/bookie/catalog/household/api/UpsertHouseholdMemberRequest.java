package com.bookie.catalog.household.api;

import com.bookie.catalog.household.application.UpsertHouseholdMemberCommand;
import jakarta.validation.constraints.NotBlank;

public record UpsertHouseholdMemberRequest(@NotBlank String name, Boolean active) {

  UpsertHouseholdMemberCommand toCommand() {
    return new UpsertHouseholdMemberCommand(name, active);
  }
}
