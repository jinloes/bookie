package com.bookie.repository;

import java.math.BigDecimal;

public interface ActivityCategoryTotalProjection {
  Long getActivityId();

  Long getCategoryId();

  BigDecimal getTotal();
}
