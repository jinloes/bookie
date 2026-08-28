package com.bookie.compatibility.api;

import java.util.Map;

/** Stable error payload shared by the retained legacy HTTP adapters. */
public record ApiErrorResponse(String code, String message, Map<String, Object> details) {}
