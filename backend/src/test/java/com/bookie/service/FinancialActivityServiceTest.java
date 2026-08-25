package com.bookie.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bookie.model.ActivityType;
import com.bookie.model.FinancialActivity;
import com.bookie.model.HouseholdMember;
import com.bookie.model.Property;
import com.bookie.model.TaxTreatment;
import com.bookie.model.UpsertFinancialActivityRequest;
import com.bookie.repository.FinancialActivityRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class FinancialActivityServiceTest {

  @Mock private FinancialActivityRepository financialActivityRepository;
  @Mock private HouseholdMemberService householdMemberService;
  @Mock private PropertyService propertyService;

  @InjectMocks private FinancialActivityService financialActivityService;

  private HouseholdMember owner;
  private Property property;

  @BeforeEach
  void setUp() {
    owner = HouseholdMember.builder().id(1L).name("Alex").active(true).build();
    property = Property.builder().id(2L).name("Oak Street").address("123 Oak").build();
  }

  @Nested
  class Create {

    @Test
    void createsPropertylessEmploymentActivity() {
      when(householdMemberService.findById(1L)).thenReturn(owner);
      when(financialActivityRepository.findByNameIgnoreCase("Teaching — School District"))
          .thenReturn(Optional.empty());
      when(financialActivityRepository.save(any()))
          .thenAnswer(invocation -> invocation.getArgument(0));
      ArgumentCaptor<FinancialActivity> captor = ArgumentCaptor.forClass(FinancialActivity.class);

      financialActivityService.create(
          new UpsertFinancialActivityRequest(
              "Teaching — School District",
              ActivityType.EMPLOYMENT,
              TaxTreatment.W2,
              1L,
              null,
              true));

      verify(financialActivityRepository).save(captor.capture());
      assertThat(captor.getValue().getOwner()).isEqualTo(owner);
      assertThat(captor.getValue().getProperty()).isNull();
      assertThat(captor.getValue().getTaxTreatment()).isEqualTo(TaxTreatment.W2);
    }

    @Test
    void rejectsRentalWithoutProperty() {
      when(householdMemberService.findById(1L)).thenReturn(owner);

      assertThatThrownBy(
              () ->
                  financialActivityService.create(
                      new UpsertFinancialActivityRequest(
                          "Rental", ActivityType.RENTAL, TaxTreatment.SCHEDULE_E, 1L, null, true)))
          .isInstanceOf(ResponseStatusException.class)
          .hasMessageContaining("require a property");
    }

    @Test
    void rejectsNonRentalWithProperty() {
      when(householdMemberService.findById(1L)).thenReturn(owner);
      when(propertyService.findById(2L)).thenReturn(property);

      assertThatThrownBy(
              () ->
                  financialActivityService.create(
                      new UpsertFinancialActivityRequest(
                          "Teaching", ActivityType.EMPLOYMENT, TaxTreatment.W2, 1L, 2L, true)))
          .isInstanceOf(ResponseStatusException.class)
          .hasMessageContaining("Only rental");
    }

    @Test
    void rejectsScheduleETreatmentForNonRentalActivity() {
      when(householdMemberService.findById(1L)).thenReturn(owner);

      assertThatThrownBy(
              () ->
                  financialActivityService.create(
                      new UpsertFinancialActivityRequest(
                          "Teaching",
                          ActivityType.EMPLOYMENT,
                          TaxTreatment.SCHEDULE_E,
                          1L,
                          null,
                          true)))
          .isInstanceOf(ResponseStatusException.class)
          .hasMessageContaining("Schedule E");
    }
  }

  @Nested
  class ResolveForTransaction {

    @Test
    void resolvesLegacyPropertyToRentalActivity() {
      FinancialActivity rental =
          FinancialActivity.builder()
              .id(3L)
              .name("Oak")
              .activityType(ActivityType.RENTAL)
              .taxTreatment(TaxTreatment.SCHEDULE_E)
              .owner(owner)
              .property(property)
              .active(true)
              .build();
      when(financialActivityRepository.findByPropertyId(2L)).thenReturn(Optional.of(rental));

      assertThat(financialActivityService.resolveForTransaction(null, 2L)).isEqualTo(rental);
    }

    @Test
    void rejectsInactiveExplicitActivity() {
      FinancialActivity inactive =
          FinancialActivity.builder().id(3L).name("Old job").active(false).build();
      when(financialActivityRepository.findById(3L)).thenReturn(Optional.of(inactive));

      assertThatThrownBy(() -> financialActivityService.resolveForTransaction(3L, null))
          .isInstanceOf(ResponseStatusException.class)
          .hasMessageContaining("inactive");
    }

    @Test
    void rejectsActivityOwnedByInactiveHouseholdMember() {
      HouseholdMember inactiveOwner =
          HouseholdMember.builder().id(1L).name("Former owner").active(false).build();
      FinancialActivity activity =
          FinancialActivity.builder()
              .id(3L)
              .name("Old job")
              .activityType(ActivityType.EMPLOYMENT)
              .taxTreatment(TaxTreatment.W2)
              .owner(inactiveOwner)
              .active(true)
              .build();
      when(financialActivityRepository.findById(3L)).thenReturn(Optional.of(activity));

      assertThatThrownBy(() -> financialActivityService.resolveForTransaction(3L, null))
          .isInstanceOf(ResponseStatusException.class)
          .hasMessageContaining("inactive");
    }

    @Test
    void resolvesSuggestedPropertyWhenItsAddressIsMissing() {
      property.setAddress(null);
      FinancialActivity rental =
          FinancialActivity.builder()
              .id(3L)
              .name("Oak")
              .activityType(ActivityType.RENTAL)
              .taxTreatment(TaxTreatment.SCHEDULE_E)
              .owner(owner)
              .property(property)
              .active(true)
              .build();
      when(financialActivityRepository.findAllByOrderByNameAsc())
          .thenReturn(java.util.List.of(rental));

      assertThat(financialActivityService.resolveForSuggestedProperty("Oak Street"))
          .isEqualTo(rental);
    }
  }
}
