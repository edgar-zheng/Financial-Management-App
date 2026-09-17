package com.edgar.portfolio.exception;

import java.util.Map;

/** Shared API contract, preserving the existing client-facing fields. */
public record ApiError(int status, String message, Map<String, String> fieldErrors) {}
