package com.bookie.catalog.household.application;

import com.bookie.catalog.household.domain.HouseholdMember;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class HouseholdMemberService implements HouseholdCatalog {

  private final HouseholdMemberStore householdMemberStore;

  @Override
  public List<HouseholdMember> findAll() {
    return householdMemberStore.findAllByOrderByNameAsc();
  }

  @Override
  public HouseholdMember findById(Long id) {
    return householdMemberStore
        .findById(id)
        .orElseThrow(
            () ->
                new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Household member not found: " + id));
  }

  @Override
  public HouseholdMember getDefaultHouseholdMember() {
    return householdMemberStore
        .findBySystemKey(DEFAULT_HOUSEHOLD_KEY)
        .orElseThrow(
            () -> new IllegalStateException("Required default household member is missing"));
  }

  @Transactional
  @Override
  public HouseholdMember create(UpsertHouseholdMemberCommand request) {
    ensureUniqueName(request.name(), null);
    return householdMemberStore.save(
        HouseholdMember.builder()
            .name(request.name().trim())
            .active(request.active() == null || request.active())
            .build());
  }

  @Transactional
  @Override
  public HouseholdMember update(Long id, UpsertHouseholdMemberCommand request) {
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
    return householdMemberStore.save(existing);
  }

  private void ensureUniqueName(String name, Long currentId) {
    householdMemberStore
        .findByNameIgnoreCase(name.trim())
        .filter(existing -> !existing.getId().equals(currentId))
        .ifPresent(
            existing -> {
              throw new ResponseStatusException(
                  HttpStatus.CONFLICT, "A household member with that name already exists");
            });
  }
}
