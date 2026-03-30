package com.bookie.repository;

import com.bookie.model.Property;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PropertyRepository extends JpaRepository<Property, Long> {

  Optional<Property> findByNameIgnoreCase(String name);
}
