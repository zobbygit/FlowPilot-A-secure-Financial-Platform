package com.flowpilot.common;

import java.util.Map;

/** Uniform error body for every failed API call. */
public record ApiError(String code, String message, Map<String, String> fieldErrors, String requestId, String timestamp) {}
