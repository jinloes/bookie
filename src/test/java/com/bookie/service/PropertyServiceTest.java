package com.bookie.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.bookie.model.Property;
import com.bookie.model.PropertyType;
import com.bookie.repository.PropertyRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PropertyServiceTest {

  @Mock private PropertyRepository propertyRepository;

  @InjectMocks private PropertyService propertyService;

  private Property property;

  @BeforeEach
  void setUp() {
    property =
        new Property(
            1L,
            "123 Main St",
            "123 Main St, Springfield, IL",
            PropertyType.SINGLE_FAMILY,
            "Corner lot");
  }

  @Test
  void findAll_returnsAllProperties() {
    when(propertyRepository.findAll()).thenReturn(List.of(property));

    List<Property> result = propertyService.findAll();

    assertThat(result).hasSize(1).containsExactly(property);
  }

  @Test
  void findById_found_returnsProperty() {
    when(propertyRepository.findById(1L)).thenReturn(Optional.of(property));

    Property result = propertyService.findById(1L);

    assertThat(result).isEqualTo(property);
  }

  @Test
  void findById_notFound_throwsException() {
    when(propertyRepository.findById(99L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> propertyService.findById(99L))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("99");
  }

  @Test
  void save_persistsAndReturnsProperty() {
    when(propertyRepository.save(property)).thenReturn(property);

    Property result = propertyService.save(property);

    assertThat(result).isEqualTo(property);
    verify(propertyRepository).save(property);
  }

  @Test
  void update_updatesFieldsAndSaves() {
    Property updated =
        new Property(
            null,
            "456 Oak Ave",
            "456 Oak Ave, Springfield, IL",
            PropertyType.CONDO,
            "Updated notes");
    when(propertyRepository.findById(1L)).thenReturn(Optional.of(property));
    when(propertyRepository.save(property)).thenReturn(property);

    propertyService.update(1L, updated);

    assertThat(property.getName()).isEqualTo("456 Oak Ave");
    assertThat(property.getAddress()).isEqualTo("456 Oak Ave, Springfield, IL");
    assertThat(property.getType()).isEqualTo(PropertyType.CONDO);
    assertThat(property.getNotes()).isEqualTo("Updated notes");
    verify(propertyRepository).save(property);
  }

  @Test
  void delete_callsRepositoryDeleteById() {
    propertyService.delete(1L);

    verify(propertyRepository).deleteById(1L);
  }
}
