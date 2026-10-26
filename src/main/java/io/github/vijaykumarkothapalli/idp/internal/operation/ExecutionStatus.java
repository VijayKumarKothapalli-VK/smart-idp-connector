/*
 * Smart IDP Connector
 * Copyright (c) 2026 Vijay Kumar Kothapalli
 * Licensed under the Apache License, Version 2.0
 */
package io.github.vijaykumarkothapalli.idp.internal.operation;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * IDP execution statuses and the polling schedule.
 * Documented statuses: ACKNOWLEDGED, IN_PROGRESS, RESULTS_PENDING (still working) and SUCCEEDED, PARTIAL_SUCCESS,
 * MANUAL_VALIDATION_REQUIRED, FAILED (final).
 */
public final class ExecutionStatus {

  private static final Set<String> IN_PROGRESS = new HashSet<>(Arrays.asList(
      "ACKNOWLEDGED", "IN_PROGRESS", "RESULTS_PENDING", "ACCEPTED", "CREATED", "QUEUED", "PENDING", "PROCESSING",
      "RUNNING", "SUBMITTED"));
  private static final Set<String> FAILED = new HashSet<>(Arrays.asList("FAILED", "ERROR", "REJECTED"));

  static final long MAX_POLL_INTERVAL_MILLIS = 10_000L;

  private ExecutionStatus() {}

  public static String normalise(String status) {
    return status == null ? "UNKNOWN" : status.trim().toUpperCase(Locale.ROOT);
  }

  /** True while IDP is still working on the document. Unknown statuses count as final so we never wait forever. */
  public static boolean isInProgress(String status) {
    return IN_PROGRESS.contains(normalise(status));
  }

  public static boolean isFailed(String status) {
    return FAILED.contains(normalise(status));
  }

  /** Next polling delay: grows by 1.5x from the first delay up to 10 seconds, and never past the deadline. */
  public static long nextDelayMillis(long previousDelayMillis, long millisUntilDeadline) {
    long next = Math.min((long) (previousDelayMillis * 1.5), MAX_POLL_INTERVAL_MILLIS);
    return Math.max(0, Math.min(next, millisUntilDeadline));
  }
}
