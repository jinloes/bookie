package com.bookie.catalog.activity.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.bookie.catalog.property.application.PropertyStore;
import com.bookie.catalog.property.domain.Property;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class CatalogPropertyLookupTest {

  @Mock private PropertyStore propertyStore;

  @InjectMocks private CatalogPropertyLookup propertyLookup;

  @Test
  void findByIdReturnsProperty() {
    Property property = Property.builder().id(42L).name("Oak Street").build();
    when(propertyStore.findById(42L)).thenReturn(Optional.of(property));

    assertThat(propertyLookup.findById(42L)).isSameAs(property);
  }

  @Test
  void findByIdRejectsMissingProperty() {
    when(propertyStore.findById(42L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> propertyLookup.findById(42L))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("42");
  }
}
