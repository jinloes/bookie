package com.bookie.catalog.activity.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bookie.catalog.activity.application.ActivityCatalog;
import com.bookie.catalog.activity.domain.FinancialActivity;
import com.bookie.catalog.property.domain.Property;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PropertyActivityLifecycleAdapterTest {

  @Mock private ActivityCatalog activityCatalog;

  @InjectMocks private PropertyActivityLifecycleAdapter lifecycle;

  @Test
  void createsThePropertyRentalActivityThroughTheActivityCatalog() {
    Property property = Property.builder().id(7L).build();

    lifecycle.createRentalActivity(property);

    verify(activityCatalog).createRentalActivity(property);
  }

  @Test
  void exposesOnlyTheRentalActivityIdentityToPropertyLifecycle() {
    FinancialActivity activity = FinancialActivity.builder().id(10L).build();
    when(activityCatalog.findByPropertyId(7L)).thenReturn(Optional.of(activity));

    assertThat(lifecycle.findRentalActivityId(7L)).contains(10L);
  }

  @Test
  void resolvesAndDeletesTheExactRentalActivity() {
    FinancialActivity activity = FinancialActivity.builder().id(10L).build();
    when(activityCatalog.findById(10L)).thenReturn(activity);

    lifecycle.deleteRentalActivity(10L);

    verify(activityCatalog).delete(activity);
  }
}
