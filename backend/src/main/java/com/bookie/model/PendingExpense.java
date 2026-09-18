package com.bookie.model;

import com.bookie.catalog.activity.domain.FinancialActivity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "pending_expenses")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PendingExpense {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(unique = true)
  private String sourceId;

  private String outlookMessageId;

  private String outlookAttachmentId;

  @Column(length = 500)
  private String outlookAttachmentName;

  @Enumerated(EnumType.STRING)
  private ExpenseSource sourceType;

  @Enumerated(EnumType.STRING)
  private EmailType emailType;

  private String subject;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private PendingExpenseStatus status;

  private BigDecimal amount;
  private String description;
  private LocalDate date;
  private String category;
  private String propertyName;
  private String payerName;

  @NotNull
  @ManyToOne(fetch = FetchType.EAGER, optional = false)
  @JoinColumn(name = "activity_id", nullable = false)
  private FinancialActivity activity;

  @NotNull
  @ManyToOne(fetch = FetchType.EAGER, optional = false)
  @JoinColumn(name = "category_id", nullable = false)
  private FinancialCategory financialCategory;

  @Column(name = "configured_activity_id")
  private Long configuredActivityId;

  @Column(nullable = false)
  @Builder.Default
  private boolean classificationAmbiguous = true;

  @Column(length = 2000)
  private String errorMessage;

  // EAGER because the list endpoint serializes pending expenses outside the JPA session in some
  // paths (e.g. error handlers, async-completed handlers) where OSIV doesn't apply. The volume is
  // small (a handful of pending rows × a few aliases) so the cost is negligible.
  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(
      name = "pending_expense_aliases",
      joinColumns = @JoinColumn(name = "pending_expense_id"))
  @Column(name = "alias")
  @Builder.Default
  private List<String> unrecognizedAliases = new ArrayList<>();

  @Column(nullable = false)
  private LocalDateTime createdAt;
}
