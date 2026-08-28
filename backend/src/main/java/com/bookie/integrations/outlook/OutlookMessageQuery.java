package com.bookie.integrations.outlook;

import java.util.List;
import lombok.Builder;

@Builder
public record OutlookMessageQuery(
    String filter,
    List<String> select,
    List<String> orderBy,
    Integer top,
    boolean expandAttachments) {

  public OutlookMessageQuery {
    select = select == null ? List.of() : List.copyOf(select);
    orderBy = orderBy == null ? List.of() : List.copyOf(orderBy);
  }
}
