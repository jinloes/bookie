package com.bookie.service;

import com.bookie.model.HouseholdMember;
import com.bookie.model.UpsertHouseholdMemberRequest;
import com.bookie.repository.HouseholdMemberRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class HouseholdMemberService {

  public static final String DEFAULT_HOUSEHOLD_KEY = "DEFAULT_HOUSEHOLD";

  private final HouseholdMemberRepository householdMemberRepository;

  public List<HouseholdMember> findAll() {
    return householdMemberRepository.findAllByOrderByNameAsc();
  }

  public HouseholdMember findById(Long id) {
    return householdMemberRepository
        .findById(id)
        .orElseThrow(
            () ->
                new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Household member not found: " + id));
  }

  @Transactional
  public HouseholdMember create(UpsertHouseholdMemberRequest request) {
    ensureUniqueName(request.name(), null);
    return householdMemberRepository.save(
        HouseholdMember.builder()
            .name(request.name().trim())
            .active(request.active() == null || request.active())
            .build());
  }

  @Transactional
  public HouseholdMember update(Long id, UpsertHouseholdMemberRequest request) {
    HouseholdMember existing = findById(id);
    if (existing.getSystemKey() != null) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "The default household member cannot be edited");
    }
    ensureUniqueName(request.name(), id);
    existing.setName(request.name().trim());
    if (request.active() != null) {
      existing.setActive(request.active());
    }
    return householdMemberRepository.save(existing);
  }

  private void ensureUniqueName(String name, Long currentId) {
    householdMemberRepository
        .findByNameIgnoreCase(name.trim())
        .filter(existing -> !existing.getId().equals(currentId))
        .ifPresent(
            existing -> {
              throw new ResponseStatusException(
                  HttpStatus.CONFLICT, "A household member with that name already exists");
            });
  }
}
