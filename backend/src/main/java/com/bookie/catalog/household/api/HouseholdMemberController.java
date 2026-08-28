package com.bookie.catalog.household.api;

import com.bookie.catalog.household.application.HouseholdCatalog;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/household-members")
@RequiredArgsConstructor
public class HouseholdMemberController {

  private final HouseholdCatalog householdCatalog;

  @Operation(operationId = "getHouseholdMembers")
  @GetMapping
  public List<HouseholdMemberResponse> getAll() {
    return householdCatalog.findAll().stream().map(HouseholdMemberResponse::from).toList();
  }

  @Operation(operationId = "createHouseholdMember")
  @PostMapping
  public HouseholdMemberResponse create(@Valid @RequestBody UpsertHouseholdMemberRequest request) {
    return HouseholdMemberResponse.from(householdCatalog.create(request.toCommand()));
  }

  @Operation(operationId = "updateHouseholdMember")
  @PutMapping("/{id}")
  public HouseholdMemberResponse update(
      @PathVariable Long id, @Valid @RequestBody UpsertHouseholdMemberRequest request) {
    return HouseholdMemberResponse.from(householdCatalog.update(id, request.toCommand()));
  }
}
