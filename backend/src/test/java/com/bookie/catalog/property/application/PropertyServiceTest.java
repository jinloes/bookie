package com.bookie.catalog.property.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bookie.catalog.property.domain.Property;
import com.bookie.catalog.property.domain.PropertyType;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PropertyServiceTest {

  @Mock private PropertyStore propertyStore;
  @Mock private PropertyActivityLifecycle activityLifecycle;
  @Mock private PropertyLedgerReferences ledgerReferences;
  @Mock private PropertyIntakeReferences intakeReferences;
  @Mock private PropertyClassificationReferences classificationReferences;

  @InjectMocks private PropertyService propertyService;

  private Property property;

  @BeforeEach
  void setUp() {
    property =
        Property.builder()
            .id(1L)
            .name("123 Main St")
            .address("123 Main St, Springfield, IL")
            .type(PropertyType.SINGLE_FAMILY)
            .notes("Corner lot")
            .build();
  }

  @Nested
  class Find {

    @Test
    void returnsAllProperties() {
      when(propertyStore.findAll()).thenReturn(List.of(property));

      assertThat(propertyService.findAll()).containsExactly(property);
    }

    @Test
    void returnsPropertyById() {
      when(propertyStore.findById(1L)).thenReturn(Optional.of(property));

      assertThat(propertyService.findById(1L)).isEqualTo(property);
    }

    @Test
    void delegatesNameAndAccountLookupsToTheCatalogStore() {
      when(propertyStore.findByNameIgnoreCase("123 Main St")).thenReturn(Optional.of(property));
      when(propertyStore.findByAccountIn(List.of("acc-001"))).thenReturn(List.of(property));

      assertThat(propertyService.findByName("123 Main St")).contains(property);
      assertThat(propertyService.findByAccounts(List.of("acc-001"))).containsExactly(property);
    }

    @Test
    void missingPropertyThrowsNotFound() {
      when(propertyStore.findById(99L)).thenReturn(Optional.empty());

      assertThatThrownBy(() -> propertyService.findById(99L))
          .isInstanceOf(RuntimeException.class)
          .hasMessageContaining("99");
    }
  }

  @Nested
  class Create {

    @Test
    void savesPropertyAndCreatesRentalActivity() {
      CreatePropertyCommand command =
          new CreatePropertyCommand(
              "123 Main St",
              "123 Main St, Springfield, IL",
              PropertyType.SINGLE_FAMILY,
              "Corner lot",
              Set.of("ACC-001"));
      when(propertyStore.save(org.mockito.ArgumentMatchers.any())).thenReturn(property);

      assertThat(propertyService.create(command)).isEqualTo(property);

      ArgumentCaptor<Property> captor = ArgumentCaptor.forClass(Property.class);
      verify(propertyStore).save(captor.capture());
      assertThat(captor.getValue().getAccounts()).containsExactly("ACC-001");
      verify(activityLifecycle).createRentalActivity(property);
    }

    @Test
    void nullAccountsBecomeEmptySet() {
      CreatePropertyCommand command =
          new CreatePropertyCommand(
              "123 Main St", "123 Main St", PropertyType.SINGLE_FAMILY, null, null);
      when(propertyStore.save(org.mockito.ArgumentMatchers.any())).thenReturn(property);

      propertyService.create(command);

      ArgumentCaptor<Property> captor = ArgumentCaptor.forClass(Property.class);
      verify(propertyStore).save(captor.capture());
      assertThat(captor.getValue().getAccounts()).isEmpty();
    }
  }

  @Nested
  class Update {

    @Test
    void updatesAllFields() {
      UpdatePropertyCommand command =
          new UpdatePropertyCommand(
              "456 Oak Ave",
              "456 Oak Ave, Springfield, IL",
              PropertyType.CONDO,
              "Updated notes",
              null);
      when(propertyStore.findById(1L)).thenReturn(Optional.of(property));
      when(propertyStore.save(property)).thenReturn(property);

      propertyService.update(1L, command);

      assertThat(property.getName()).isEqualTo("456 Oak Ave");
      assertThat(property.getAddress()).isEqualTo("456 Oak Ave, Springfield, IL");
      assertThat(property.getType()).isEqualTo(PropertyType.CONDO);
      assertThat(property.getNotes()).isEqualTo("Updated notes");
      assertThat(property.getAccounts()).isEmpty();
    }
  }

  @Nested
  class Delete {

    @Test
    void delegatesReferenceOwnershipAndDeletesOnlyCatalogRows() {
      when(propertyStore.findById(1L)).thenReturn(Optional.of(property));
      when(activityLifecycle.findRentalActivityId(1L)).thenReturn(Optional.of(10L));

      propertyService.delete(1L);

      var order =
          inOrder(
              ledgerReferences,
              intakeReferences,
              activityLifecycle,
              classificationReferences,
              propertyStore);
      order.verify(ledgerReferences).reassignActivity(10L);
      order.verify(intakeReferences).reassignActivity(10L);
      order.verify(activityLifecycle).deleteRentalActivity(10L);
      order.verify(ledgerReferences).detachProperty(1L);
      order.verify(intakeReferences).detachProperty(1L);
      order.verify(classificationReferences).removePropertyReferences(1L);
      order.verify(ledgerReferences).requireNoReferences(1L, Optional.of(10L));
      order.verify(intakeReferences).requireNoReferences(1L, Optional.of(10L));
      order.verify(classificationReferences).requireNoPropertyReferences(1L);
      order.verify(propertyStore).deleteById(1L);
    }

    @Test
    void propertyWithoutRentalActivityStillDetachesAllReferences() {
      when(propertyStore.findById(1L)).thenReturn(Optional.of(property));
      when(activityLifecycle.findRentalActivityId(1L)).thenReturn(Optional.empty());

      propertyService.delete(1L);

      verify(ledgerReferences).detachProperty(1L);
      verify(intakeReferences).detachProperty(1L);
      verify(propertyStore).deleteById(1L);
    }
  }
}
