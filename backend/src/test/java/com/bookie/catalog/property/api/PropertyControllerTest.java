package com.bookie.catalog.property.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bookie.catalog.property.application.PropertyCatalog;
import com.bookie.catalog.property.domain.Property;
import com.bookie.catalog.property.domain.PropertyType;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PropertyController.class)
class PropertyControllerTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @MockitoBean private PropertyCatalog propertyCatalog;

  private Property property() {
    return Property.builder()
        .id(1L)
        .name("123 Main St")
        .address("123 Main St, Springfield, IL")
        .type(PropertyType.SINGLE_FAMILY)
        .notes("Corner lot")
        .build();
  }

  @Test
  void getAllReturnsPropertyList() throws Exception {
    when(propertyCatalog.findAll()).thenReturn(List.of(property()));

    mockMvc
        .perform(get("/api/properties"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(1))
        .andExpect(jsonPath("$[0].name").value("123 Main St"));
  }

  @Test
  void getByIdReturnsProperty() throws Exception {
    when(propertyCatalog.findById(1L)).thenReturn(property());

    mockMvc
        .perform(get("/api/properties/1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(1))
        .andExpect(jsonPath("$.type").value("SINGLE_FAMILY"));
  }

  @Test
  void createPreservesLegacyPayload() throws Exception {
    when(propertyCatalog.create(any())).thenReturn(property());
    CreatePropertyRequest request =
        new CreatePropertyRequest(
            "123 Main St",
            "123 Main St, Springfield, IL",
            PropertyType.SINGLE_FAMILY,
            "Corner lot",
            null);

    mockMvc
        .perform(
            post("/api/properties")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(1));
  }

  @Test
  void updatePreservesLegacyPayload() throws Exception {
    when(propertyCatalog.update(eq(1L), any())).thenReturn(property());
    UpdatePropertyRequest request =
        new UpdatePropertyRequest(
            "123 Main St",
            "123 Main St, Springfield, IL",
            PropertyType.SINGLE_FAMILY,
            "Corner lot",
            null);

    mockMvc
        .perform(
            put("/api/properties/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(1));
  }

  @Test
  void deleteReturnsNoContent() throws Exception {
    doNothing().when(propertyCatalog).delete(1L);

    mockMvc.perform(delete("/api/properties/1")).andExpect(status().isNoContent());
  }

  @Test
  void getTypesReturnsAllPropertyTypes() throws Exception {
    mockMvc
        .perform(get("/api/properties/types"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].value").value("SINGLE_FAMILY"))
        .andExpect(jsonPath("$[0].label").value("Single Family"));
  }
}
