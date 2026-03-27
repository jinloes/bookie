package com.bookie.repository;

import com.bookie.model.OutlookToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OutlookTokenRepository extends JpaRepository<OutlookToken, Long> {
}