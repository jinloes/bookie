package com.bookie.catalog.property.api;

import com.bookie.catalog.api.EnumOptionResponse;
import com.bookie.catalog.property.application.PropertyCatalog;
import com.bookie.catalog.property.domain.PropertyType;
import io.swagger.v3.oas.annotations.Operation;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/properties")
@RequiredArgsConstructor
public class PropertyController {

  private final PropertyCatalog propertyCatalog;

  @Operation(operationId = "getProperties")
  @GetMapping
  public List<PropertyResponse> getAll() {
    return propertyCatalog.findAll().stream().map(PropertyResponse::from).toList();
  }

  @Operation(operationId = "getPropertyById")
  @GetMapping("/{id}")
  public PropertyResponse getById(@PathVariable Long id) {
    return PropertyResponse.from(propertyCatalog.findById(id));
  }

  @Operation(operationId = "createProperty")
  @PostMapping
  public PropertyResponse create(@RequestBody CreatePropertyRequest request) {
    return PropertyResponse.from(propertyCatalog.create(request.toCommand()));
  }

  @Operation(operationId = "updateProperty")
  @PutMapping("/{id}")
  public PropertyResponse update(
      @PathVariable Long id, @RequestBody UpdatePropertyRequest request) {
    return PropertyResponse.from(propertyCatalog.update(id, request.toCommand()));
  }

  @Operation(operationId = "deleteProperty")
  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable Long id) {
    propertyCatalog.delete(id);
    return ResponseEntity.noContent().build();
  }

  @Operation(operationId = "getPropertyTypes")
  @GetMapping("/types")
  public List<EnumOptionResponse> getTypes() {
    return Arrays.stream(PropertyType.values())
        .map(t -> new EnumOptionResponse(t.name(), t.label))
        .toList();
  }
}
