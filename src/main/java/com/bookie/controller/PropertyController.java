package com.bookie.controller;

import com.bookie.model.EmailKeywordPropertyHistory;
import com.bookie.model.Property;
import com.bookie.model.PropertyType;
import com.bookie.service.PropertyHistoryService;
import com.bookie.service.PropertyService;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
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
  public List<Property> getAll() {
    return propertyService.findAll();
  }

  @GetMapping("/{id}")
  public Property getById(@PathVariable Long id) {
    return propertyService.findById(id);
  }

  @PostMapping
  public Property create(@RequestBody Property property) {
    return propertyService.save(property);
  }

  @PutMapping("/{id}")
  public Property update(@PathVariable Long id, @RequestBody Property property) {
    return propertyService.update(id, property);
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
  public List<Map<String, String>> getTypes() {
    return Arrays.stream(PropertyType.values())
        .map(t -> Map.of("value", t.name(), "label", t.label))
        .toList();
  }
}
