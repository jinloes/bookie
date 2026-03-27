package com.bookie.model;

public record GraphMessageBody(String subject, String receivedDateTime, Body body) {

    public record Body(String contentType, String content) {}
}