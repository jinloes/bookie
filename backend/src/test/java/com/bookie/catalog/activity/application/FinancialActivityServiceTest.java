package com.bookie.catalog.activity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bookie.catalog.activity.domain.ActivityType;
import com.bookie.catalog.activity.domain.FinancialActivity;
import com.bookie.catalog.activity.domain.TaxTreatment;
import com.bookie.catalog.household.application.HouseholdCatalog;
import com.bookie.catalog.household.domain.HouseholdMember;
import com.bookie.catalog.property.domain.Property;
import com.bookie.catalog.reportpolicy.application.ReportingProfileAssignmentCatalog;
import com.bookie.catalog.reportpolicy.domain.ReportingProfileKey;
import java.util.List;
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

  @Mock private FinancialActivityStore financialActivityStore;
  @Mock private HouseholdCatalog householdCatalog;
  @Mock private PropertyLookup propertyLookup;
  @Mock private ReportingProfileAssignmentCatalog reportingProfileAssignmentCatalog;

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
      when(householdCatalog.findById(1L)).thenReturn(owner);
      when(financialActivityStore.findByNameIgnoreCase("Teaching — School District"))
          .thenReturn(Optional.empty());
      when(financialActivityStore.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
      ArgumentCaptor<FinancialActivity> captor = ArgumentCaptor.forClass(FinancialActivity.class);

      financialActivityService.create(
          new UpsertFinancialActivityCommand(
              "Teaching — School District",
              ActivityType.EMPLOYMENT,
              TaxTreatment.W2,
              1L,
              null,
              true));

      verify(financialActivityStore).save(captor.capture());
      verify(reportingProfileAssignmentCatalog).synchronizeCurrent(null, ReportingProfileKey.W2);
      assertThat(captor.getValue().getOwner()).isEqualTo(owner);
      assertThat(captor.getValue().getProperty()).isNull();
      assertThat(captor.getValue().getTaxTreatment()).isEqualTo(TaxTreatment.W2);
    }

    @Test
    void rejectsRentalWithoutProperty() {
      when(householdCatalog.findById(1L)).thenReturn(owner);

      assertThatThrownBy(
              () ->
                  financialActivityService.create(
                      new UpsertFinancialActivityCommand(
                          "Rental", ActivityType.RENTAL, TaxTreatment.SCHEDULE_E, 1L, null, true)))
          .isInstanceOf(ResponseStatusException.class)
          .hasMessageContaining("require a property");
    }

    @Test
    void rejectsNonRentalWithProperty() {
      when(householdCatalog.findById(1L)).thenReturn(owner);
      when(propertyLookup.findById(2L)).thenReturn(property);

      assertThatThrownBy(
              () ->
                  financialActivityService.create(
                      new UpsertFinancialActivityCommand(
                          "Teaching", ActivityType.EMPLOYMENT, TaxTreatment.W2, 1L, 2L, true)))
          .isInstanceOf(ResponseStatusException.class)
          .hasMessageContaining("Only rental");
    }

    @Test
    void rejectsScheduleETreatmentForNonRentalActivity() {
      when(householdCatalog.findById(1L)).thenReturn(owner);

      assertThatThrownBy(
              () ->
                  financialActivityService.create(
                      new UpsertFinancialActivityCommand(
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
  class Update {

    @Test
    void synchronizesTheEffectiveReportingProfileAfterAnActivityChange() {
      FinancialActivity existing =
          FinancialActivity.builder()
              .id(3L)
              .name("Side work")
              .activityType(ActivityType.OTHER)
              .taxTreatment(TaxTreatment.NONE)
              .owner(owner)
              .active(true)
              .build();
      when(financialActivityStore.findById(3L)).thenReturn(Optional.of(existing));
      when(householdCatalog.findById(1L)).thenReturn(owner);
      when(financialActivityStore.findByNameIgnoreCase("Tutoring")).thenReturn(Optional.empty());
      when(financialActivityStore.save(existing)).thenReturn(existing);

      FinancialActivity updated =
          financialActivityService.update(
              3L,
              new UpsertFinancialActivityCommand(
                  "Tutoring",
                  ActivityType.SELF_EMPLOYMENT,
                  TaxTreatment.SCHEDULE_C,
                  1L,
                  null,
                  true));

      assertThat(updated.getTaxTreatment()).isEqualTo(TaxTreatment.SCHEDULE_C);
      verify(reportingProfileAssignmentCatalog)
          .synchronizeCurrent(3L, ReportingProfileKey.SCHEDULE_C);
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
      when(financialActivityStore.findByPropertyId(2L)).thenReturn(Optional.of(rental));

      assertThat(financialActivityService.resolveForTransaction(null, 2L)).isEqualTo(rental);
    }

    @Test
    void rejectsInactiveExplicitActivity() {
      FinancialActivity inactive =
          FinancialActivity.builder().id(3L).name("Old job").active(false).build();
      when(financialActivityStore.findById(3L)).thenReturn(Optional.of(inactive));

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
      when(financialActivityStore.findById(3L)).thenReturn(Optional.of(activity));

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
      when(financialActivityStore.findAllByOrderByNameAsc()).thenReturn(java.util.List.of(rental));

      assertThat(financialActivityService.resolveForSuggestedProperty("Oak Street"))
          .isEqualTo(rental);
    }
  }

  @Nested
  class CreateRentalActivity {

    @Test
    void createsRentalActivityForDefaultHousehold() {
      when(householdCatalog.getDefaultHouseholdMember()).thenReturn(owner);
      when(financialActivityStore.findAll()).thenReturn(List.of());
      when(financialActivityStore.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

      FinancialActivity activity = financialActivityService.createRentalActivity(property);

      assertThat(activity.getName()).isEqualTo("Oak Street");
      assertThat(activity.getActivityType()).isEqualTo(ActivityType.RENTAL);
      assertThat(activity.getTaxTreatment()).isEqualTo(TaxTreatment.SCHEDULE_E);
      assertThat(activity.getOwner()).isSameAs(owner);
      assertThat(activity.getProperty()).isSameAs(property);
      assertThat(activity.isActive()).isTrue();
      verify(reportingProfileAssignmentCatalog)
          .synchronizeCurrent(null, ReportingProfileKey.SCHEDULE_E);
    }

    @Test
    void disambiguatesDuplicateActivityNameWithPropertyId() {
      FinancialActivity existing =
          FinancialActivity.builder().name("OAK STREET").owner(owner).build();
      when(householdCatalog.getDefaultHouseholdMember()).thenReturn(owner);
      when(financialActivityStore.findAll()).thenReturn(List.of(existing));
      when(financialActivityStore.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

      assertThat(financialActivityService.createRentalActivity(property).getName())
          .isEqualTo("Oak Street (2)");
    }
  }

  @Nested
  class PersistenceDelegation {

    @Test
    void findsActivityByPropertyId() {
      FinancialActivity activity = FinancialActivity.builder().id(3L).build();
      when(financialActivityStore.findByPropertyId(2L)).thenReturn(Optional.of(activity));

      assertThat(financialActivityService.findByPropertyId(2L)).contains(activity);
    }

    @Test
    void deletesActivity() {
      FinancialActivity activity = FinancialActivity.builder().id(3L).build();

      financialActivityService.delete(activity);

      verify(reportingProfileAssignmentCatalog).removeAll(3L);
      verify(financialActivityStore).delete(activity);
    }
  }
}
