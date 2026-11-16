package com.bookie.catalog.property.infrastructure;

import com.bookie.catalog.property.application.PropertyStore;
import com.bookie.catalog.property.domain.Property;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PropertyRepository extends JpaRepository<Property, Long>, PropertyStore {

  @Override
  Optional<Property> findById(Long id);

  @Override
  <S extends Property> S save(S property);

  @Override
  void deleteById(Long id);

  @Override
  Optional<Property> findByNameIgnoreCase(String name);

  @Override
  @Query(
      "SELECT DISTINCT property FROM Property property JOIN property.accounts account "
          + "WHERE LOWER(account) IN :accounts")
  List<Property> findByAccountIn(@Param("accounts") List<String> accounts);
}
