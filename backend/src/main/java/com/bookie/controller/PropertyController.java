package com.bookie.controller;

import com.bookie.model.CreatePropertyRequest;
import com.bookie.model.EmailKeywordPropertyHistory;
import com.bookie.model.PropertyType;
import com.bookie.model.UpdatePropertyRequest;
import com.bookie.service.PropertyHistoryService;
import com.bookie.service.PropertyService;
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

  private final PropertyService propertyService;
  private final PropertyHistoryService propertyHistoryService;

  @Operation(operationId = "getProperties")
  @GetMapping
  public List<ApiResponses.PropertyResponse> getAll() {
    return propertyService.findAll().stream().map(ApiResponses.PropertyResponse::from).toList();
  }

  @Operation(operationId = "getPropertyById")
  @GetMapping("/{id}")
  public ApiResponses.PropertyResponse getById(@PathVariable Long id) {
    return ApiResponses.PropertyResponse.from(propertyService.findById(id));
  }

  @Operation(operationId = "createProperty")
  @PostMapping
  public ApiResponses.PropertyResponse create(@RequestBody CreatePropertyRequest req) {
    return ApiResponses.PropertyResponse.from(propertyService.create(req));
  }

  @Operation(operationId = "updateProperty")
  @PutMapping("/{id}")
  public ApiResponses.PropertyResponse update(
      @PathVariable Long id, @RequestBody UpdatePropertyRequest req) {
    return ApiResponses.PropertyResponse.from(propertyService.update(id, req));
  }

  @Operation(operationId = "deleteProperty")
  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable Long id) {
    propertyService.delete(id);
    return ResponseEntity.noContent().build();
  }

  @Operation(operationId = "getPropertyKeywords")
  @GetMapping("/keywords")
  public List<EmailKeywordPropertyHistory> getKeywords() {
    return propertyHistoryService.getAllPropertyKeywords();
  }

  @Operation(operationId = "getPropertyTypes")
  @GetMapping("/types")
  public List<ApiResponses.EnumOptionResponse> getTypes() {
    return Arrays.stream(PropertyType.values())
        .map(t -> new ApiResponses.EnumOptionResponse(t.name(), t.label))
        .toList();
  }
}
