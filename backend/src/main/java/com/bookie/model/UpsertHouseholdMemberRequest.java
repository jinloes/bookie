package com.bookie.model;

import jakarta.validation.constraints.NotBlank;

public record UpsertHouseholdMemberRequest(@NotBlank String name, Boolean active) {}
