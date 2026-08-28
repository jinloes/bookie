package com.bookie.catalog.household.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.bookie.catalog.household.domain.HouseholdMember;
import org.junit.jupiter.api.Test;

class HouseholdMemberResponseTest {

  @Test
  void responsesMapHouseholdMember() {
    HouseholdMember member =
        HouseholdMember.builder()
            .id(1L)
            .name("Alex")
            .active(true)
            .systemKey("DEFAULT_HOUSEHOLD")
            .build();

    assertThat(HouseholdMemberResponse.from(member))
        .isEqualTo(new HouseholdMemberResponse(1L, "Alex", true, true));
    assertThat(HouseholdMemberRefResponse.from(member))
        .isEqualTo(new HouseholdMemberRefResponse(1L, "Alex"));
  }

  @Test
  void responsesReturnNullForNullMember() {
    assertThat(HouseholdMemberResponse.from(null)).isNull();
    assertThat(HouseholdMemberRefResponse.from(null)).isNull();
  }
}
