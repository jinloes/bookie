package com.bookie.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import lombok.Builder;

@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public record EmailParseResult(
    @JsonAlias("emailType") TransactionDirection direction,
    Double amount,
    String description,
    String date,
    @JsonAlias("payerName") String counterpartyName,
    List<String> keywords,
    List<String> accountNumbers) {}
