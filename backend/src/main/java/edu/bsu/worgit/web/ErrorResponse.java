package edu.bsu.worgit.web;

import java.time.Instant;

/** The one JSON shape of every error. "field" is omitted when null. */
public record ErrorResponse(int status, String code, String message, String field, Instant timestamp) {}
