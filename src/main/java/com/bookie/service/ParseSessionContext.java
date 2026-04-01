package com.bookie.service;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

@Component
@RequestScope
public class ParseSessionContext {

  private final List<String> unrecognizedAliases = new ArrayList<>();

  public void addUnrecognizedAlias(String alias) {
    unrecognizedAliases.add(alias);
  }

  public List<String> getUnrecognizedAliases() {
    return List.copyOf(unrecognizedAliases);
  }
}
