package com.bookie.model;

public record GraphMessage(String id, String subject, GraphFrom from, String receivedDateTime, String bodyPreview) {

    public record GraphFrom(GraphEmailAddress emailAddress) {}

    public record GraphEmailAddress(String name, String address) {}
}