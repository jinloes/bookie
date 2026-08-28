package com.bookie.catalog.activity.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.bookie.catalog.activity.domain.ActivityType;
import com.bookie.catalog.activity.domain.FinancialActivity;
import com.bookie.catalog.activity.domain.TaxTreatment;
import com.bookie.catalog.household.domain.HouseholdMember;
import com.bookie.catalog.property.api.PropertyRefResponse;
import com.bookie.catalog.property.domain.Property;
import org.junit.jupiter.api.Test;

class FinancialActivityResponseTest {

  @Test
  void fromMapsCatalogReferences() {
    HouseholdMember owner = HouseholdMember.builder().id(1L).name("Alex").build();
    Property property = Property.builder().id(2L).name("Oak Street").build();
    FinancialActivity activity =
        FinancialActivity.builder()
            .id(3L)
            .name("Oak Street")
            .activityType(ActivityType.RENTAL)
            .taxTreatment(TaxTreatment.SCHEDULE_E)
            .owner(owner)
            .property(property)
            .active(true)
            .systemKey("SYSTEM")
            .build();

    FinancialActivityResponse response = FinancialActivityResponse.from(activity);

    assertThat(response.id()).isEqualTo(3L);
    assertThat(response.owner().name()).isEqualTo("Alex");
    assertThat(response.property().name()).isEqualTo("Oak Street");
    assertThat(response.needsClassification()).isTrue();
  }

  @Test
  void fromReturnsNullForNullActivity() {
    assertThat(FinancialActivityResponse.from(null)).isNull();
  }

  @Test
  void propertyReferenceReturnsNullForNullProperty() {
    assertThat(PropertyRefResponse.from(null)).isNull();
  }
}
