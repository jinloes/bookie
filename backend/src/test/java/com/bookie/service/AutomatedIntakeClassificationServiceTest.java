package com.bookie.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.bookie.model.ActivityType;
import com.bookie.model.FinancialActivity;
import com.bookie.model.FinancialCategory;
import com.bookie.model.HistoryHint;
import com.bookie.model.HouseholdMember;
import com.bookie.model.TaxTreatment;
import com.bookie.model.TransactionDirection;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AutomatedIntakeClassificationServiceTest {

  @Mock private FinancialActivityService financialActivityService;
  @Mock private FinancialCategoryService financialCategoryService;
  @Mock private PropertyHistoryService propertyHistoryService;

  private AutomatedIntakeClassificationService service;
  private FinancialActivity needsClassification;

  @BeforeEach
  void setUp() {
    service =
        new AutomatedIntakeClassificationService(
            financialActivityService, financialCategoryService, propertyHistoryService);
    needsClassification =
        activity(
            999L,
            "Needs classification",
            ActivityType.OTHER,
            TaxTreatment.NONE,
            FinancialActivityService.NEEDS_CLASSIFICATION_KEY);
    lenient()
        .when(financialActivityService.getNeedsClassification())
        .thenReturn(needsClassification);
    lenient().when(propertyHistoryService.getActivityHints(anyList())).thenReturn(List.of());
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("fixtures")
  void resolvesSyntheticFixtureWithoutModelOwnedClassification(IntakeFixture fixture) {
    TransactionDirection direction = TransactionDirection.valueOf(fixture.direction());
    FinancialActivity expectedActivity;
    if (fixture.activityId() == null) {
      expectedActivity = needsClassification;
    } else {
      expectedActivity =
          activity(
              fixture.activityId(),
              fixture.activityName(),
              fixture.taxTreatment().equals("W2")
                  ? ActivityType.EMPLOYMENT
                  : ActivityType.SELF_EMPLOYMENT,
              TaxTreatment.valueOf(fixture.taxTreatment()),
              null);
      when(financialActivityService.findActiveById(fixture.activityId()))
          .thenReturn(expectedActivity);
    }

    FinancialCategory expectedCategory =
        FinancialCategory.builder()
            .id(fixture.activityId() == null ? 900L : fixture.activityId() + 1_000)
            .key(fixture.categoryKey())
            .label(fixture.categoryKey())
            .direction(direction)
            .taxTreatment(expectedActivity.getTaxTreatment())
            .active(true)
            .build();
    List<String> keywords = List.of(keywordFrom(fixture));

    if (!fixture.classificationAmbiguous()) {
      when(propertyHistoryService.getFinancialCategoryHints(
              fixture.activityId(), direction, keywords))
          .thenReturn(
              List.of(
                  new HistoryHint(fixture.categoryKey(), 3, "activity-category-keyword-history")));
      when(financialCategoryService.resolve(
              null, fixture.categoryKey(), direction, expectedActivity))
          .thenReturn(expectedCategory);
    } else {
      when(propertyHistoryService.getFinancialCategoryHints(
              expectedActivity.getId(), direction, keywords))
          .thenReturn(List.of());
      when(financialCategoryService.defaultFor(expectedActivity, direction))
          .thenReturn(expectedCategory);
    }

    AutomatedIntakeClassificationService.Resolution resolution =
        service.resolve(direction, fixture.activityId(), null, keywords, "Synthetic Counterparty");

    assertThat(resolution.activity().getName()).isEqualTo(fixture.activityName());
    assertThat(resolution.activity().getOwner().getName()).isEqualTo("Synthetic Household Member");
    assertThat(resolution.category().getKey()).isEqualTo(fixture.categoryKey());
    assertThat(resolution.classificationAmbiguous()).isEqualTo(fixture.classificationAmbiguous());
  }

  static Stream<IntakeFixture> fixtures() throws Exception {
    ObjectMapper mapper = new ObjectMapper();
    try (InputStream input =
        AutomatedIntakeClassificationServiceTest.class.getResourceAsStream(
            "/fixtures/automated-intake-fixtures.json")) {
      return mapper.readValue(input, new TypeReference<List<IntakeFixture>>() {}).stream();
    }
  }

  private static FinancialActivity activity(
      Long id,
      String name,
      ActivityType activityType,
      TaxTreatment taxTreatment,
      String systemKey) {
    return FinancialActivity.builder()
        .id(id)
        .name(name)
        .activityType(activityType)
        .taxTreatment(taxTreatment)
        .owner(
            HouseholdMember.builder()
                .id(1L)
                .name("Synthetic Household Member")
                .active(true)
                .build())
        .active(true)
        .systemKey(systemKey)
        .build();
  }

  private static String keywordFrom(IntakeFixture fixture) {
    int start = fixture.body().indexOf("Reference ") + "Reference ".length();
    int end = fixture.body().indexOf('.', start);
    return fixture.body().substring(start, end);
  }

  record IntakeFixture(
      String id,
      String subject,
      String body,
      String modelJson,
      Long activityId,
      String activityName,
      String taxTreatment,
      String categoryKey,
      String direction,
      boolean classificationAmbiguous) {

    @Override
    public String toString() {
      return id;
    }
  }
}
