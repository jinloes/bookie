package com.bookie.repository;

import com.bookie.model.Property;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PropertyRepository extends JpaRepository<Property, Long> {

  Optional<Property> findByNameIgnoreCase(String name);

  @Query("SELECT DISTINCT p FROM Property p JOIN p.accounts a WHERE LOWER(a) IN :accounts")
  List<Property> findByAccountIn(@Param("accounts") List<String> accounts);
}
