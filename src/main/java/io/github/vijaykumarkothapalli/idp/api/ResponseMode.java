/*
 * Smart IDP Connector
 * Copyright (c) 2026 Vijay Kumar Kothapalli
 * Licensed under the Apache License, Version 2.0
 */
package io.github.vijaykumarkothapalli.idp.api;

/**
 * How Process Document delivers the IDP result.
 */
public enum ResponseMode {
  /** Submit, then wait (up to Max Wait) and return the extracted result in the same call. */
  WAIT_FOR_RESULT,
  /** Submit with a callback URL and return immediately; IDP calls the callback when processing finishes. */
  CALLBACK,
  /**
   * Submit with a callback URL and wait up to Max Wait. Fast documents return their result directly; slow ones return
   * a pending response and IDP still calls the callback when they finish.
   */
  WAIT_WITH_CALLBACK_FALLBACK
}
