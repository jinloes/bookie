package com.bookie.controller;

import com.bookie.model.UpsertHouseholdMemberRequest;
import com.bookie.service.HouseholdMemberService;
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

  private final HouseholdMemberService householdMemberService;

  @Operation(operationId = "getHouseholdMembers")
  @GetMapping
  public List<ApiResponses.HouseholdMemberResponse> getAll() {
    return householdMemberService.findAll().stream()
        .map(ApiResponses.HouseholdMemberResponse::from)
        .toList();
  }

  @Operation(operationId = "createHouseholdMember")
  @PostMapping
  public ApiResponses.HouseholdMemberResponse create(
      @Valid @RequestBody UpsertHouseholdMemberRequest request) {
    return ApiResponses.HouseholdMemberResponse.from(householdMemberService.create(request));
  }

  @Operation(operationId = "updateHouseholdMember")
  @PutMapping("/{id}")
  public ApiResponses.HouseholdMemberResponse update(
      @PathVariable Long id, @Valid @RequestBody UpsertHouseholdMemberRequest request) {
    return ApiResponses.HouseholdMemberResponse.from(householdMemberService.update(id, request));
  }
}
