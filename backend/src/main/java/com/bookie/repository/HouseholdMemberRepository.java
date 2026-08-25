package com.bookie.repository;

import com.bookie.model.HouseholdMember;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface HouseholdMemberRepository extends JpaRepository<HouseholdMember, Long> {

  List<HouseholdMember> findAllByOrderByNameAsc();

  Optional<HouseholdMember> findByNameIgnoreCase(String name);

  Optional<HouseholdMember> findBySystemKey(String systemKey);
}
