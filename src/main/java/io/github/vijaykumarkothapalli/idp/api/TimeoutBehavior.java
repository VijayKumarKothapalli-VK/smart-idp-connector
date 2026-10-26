/*
 * Smart IDP Connector
 * Copyright (c) 2026 Vijay Kumar Kothapalli
 * Licensed under the Apache License, Version 2.0
 */
package io.github.vijaykumarkothapalli.idp.api;

/**
 * What happens when IDP is still processing after Max Wait.
 */
public enum TimeoutBehavior {
  /** Return a response with completed=false, the execution id and the result URL so it can be collected later. */
  RETURN_PENDING,
  /** Raise SMART-IDP:TIMEOUT. */
  RAISE_ERROR
}
