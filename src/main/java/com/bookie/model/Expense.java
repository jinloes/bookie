package com.bookie.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "expenses")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Expense {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false)
    private LocalDate date;

    @Enumerated(EnumType.STRING)
    private ExpenseCategory category;

    private String propertyName;

    @Enumerated(EnumType.STRING)
    private ExpenseSource sourceType;

    private String sourceId;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "payer_id")
    private Payer payer;
}
