package com.bookie.model;

/** Implemented by history entities that track occurrence counts. */
public interface HasOccurrences {
  int getOccurrences();

  void setOccurrences(int occurrences);
}
