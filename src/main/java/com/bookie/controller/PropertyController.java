package com.bookie.controller;

import com.bookie.model.CreatePropertyRequest;
import com.bookie.model.EmailKeywordPropertyHistory;
import com.bookie.model.Property;
import com.bookie.model.PropertyType;
import com.bookie.model.UpdatePropertyRequest;
import com.bookie.service.PropertyHistoryService;
import com.bookie.service.PropertyService;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/properties")
@RequiredArgsConstructor
public class PropertyController {

  private final PropertyService propertyService;
  private final PropertyHistoryService propertyHistoryService;

  @GetMapping
  public List<ApiResponses.PropertyResponse> getAll() {
    return propertyService.findAll().stream().map(ApiResponses.PropertyResponse::from).toList();
  }

  @GetMapping("/{id}")
  public ApiResponses.PropertyResponse getById(@PathVariable Long id) {
    return ApiResponses.PropertyResponse.from(propertyService.findById(id));
  }

  @PostMapping
  public ApiResponses.PropertyResponse create(@RequestBody CreatePropertyRequest req) {
    var property =
        Property.builder()
            .name(req.name())
            .address(req.address())
            .type(req.type())
            .notes(req.notes())
            .accounts(req.accounts() != null ? new HashSet<>(req.accounts()) : new HashSet<>())
            .build();
    return ApiResponses.PropertyResponse.from(propertyService.save(property));
  }

  @PutMapping("/{id}")
  public ApiResponses.PropertyResponse update(
      @PathVariable Long id, @RequestBody UpdatePropertyRequest req) {
    var updated =
        Property.builder()
            .name(req.name())
            .address(req.address())
            .type(req.type())
            .notes(req.notes())
            .accounts(req.accounts() != null ? new HashSet<>(req.accounts()) : new HashSet<>())
            .build();
    return ApiResponses.PropertyResponse.from(propertyService.update(id, updated));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable Long id) {
    propertyService.delete(id);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/keywords")
  public List<EmailKeywordPropertyHistory> getKeywords() {
    return propertyHistoryService.getAllPropertyKeywords();
  }

  @GetMapping("/types")
  public List<ApiResponses.EnumOptionResponse> getTypes() {
    return Arrays.stream(PropertyType.values())
        .map(t -> new ApiResponses.EnumOptionResponse(t.name(), t.label))
        .toList();
  }
}
