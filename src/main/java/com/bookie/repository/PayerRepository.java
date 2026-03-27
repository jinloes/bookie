package com.bookie.repository;

import com.bookie.model.Payer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PayerRepository extends JpaRepository<Payer, Long> {

    java.util.Optional<Payer> findByNameIgnoreCase(String name);
}