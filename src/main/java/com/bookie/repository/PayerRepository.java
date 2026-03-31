package com.bookie.repository;

import com.bookie.model.Payer;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PayerRepository extends JpaRepository<Payer, Long> {

  Optional<Payer> findByNameIgnoreCase(String name);

  @Query("SELECT p FROM Payer p JOIN p.aliases a WHERE LOWER(a) = LOWER(:alias)")
  Optional<Payer> findByAliasIgnoreCase(@Param("alias") String alias);

  @Query("SELECT DISTINCT p FROM Payer p JOIN p.accounts a WHERE LOWER(a) IN :accounts")
  List<Payer> findByAccountIn(@Param("accounts") List<String> accounts);
}
