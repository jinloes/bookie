package com.bookie.repository;

import java.math.BigDecimal;

public interface ActivityTotalProjection {
  Long getActivityId();

  BigDecimal getTotal();
}
