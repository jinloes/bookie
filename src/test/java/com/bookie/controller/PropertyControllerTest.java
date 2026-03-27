package com.bookie.controller;

import com.bookie.model.Property;
import com.bookie.model.PropertyType;
import com.bookie.service.PropertyService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PropertyController.class)
class PropertyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private PropertyService propertyService;

    private Property property() {
        return new Property(1L, "123 Main St", "123 Main St, Springfield, IL", PropertyType.SINGLE_FAMILY, "Corner lot");
    }

    @Test
    void getAll_returnsPropertyList() throws Exception {
        when(propertyService.findAll()).thenReturn(List.of(property()));

        mockMvc.perform(get("/api/properties"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].name").value("123 Main St"));
    }

    @Test
    void getById_returnsProperty() throws Exception {
        when(propertyService.findById(1L)).thenReturn(property());

        mockMvc.perform(get("/api/properties/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.type").value("SINGLE_FAMILY"));
    }

    @Test
    void create_persistsAndReturnsProperty() throws Exception {
        when(propertyService.save(any())).thenReturn(property());

        mockMvc.perform(post("/api/properties")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(property())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void update_returnsUpdatedProperty() throws Exception {
        when(propertyService.update(eq(1L), any())).thenReturn(property());

        mockMvc.perform(put("/api/properties/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(property())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void delete_returnsNoContent() throws Exception {
        doNothing().when(propertyService).delete(1L);

        mockMvc.perform(delete("/api/properties/1"))
                .andExpect(status().isNoContent());
    }

    @Test
    void getTypes_returnsAllPropertyTypes() throws Exception {
        mockMvc.perform(get("/api/properties/types"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].value").value("SINGLE_FAMILY"))
                .andExpect(jsonPath("$[0].label").value("Single Family"));
    }
}