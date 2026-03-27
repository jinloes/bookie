package com.bookie.model;

public enum PayerType {
    PERSON("Person"),
    COMPANY("Company");

    public final String label;

    PayerType(String label) {
        this.label = label;
    }
}