/*
 * Smart IDP Connector
 * Copyright (c) 2026 Vijay Kumar Kothapalli
 * Licensed under the Apache License, Version 2.0
 */
package io.github.vijaykumarkothapalli.idp.internal.error;

import org.mule.runtime.extension.api.error.ErrorTypeDefinition;
import org.mule.runtime.extension.api.error.MuleErrors;

import java.util.Optional;

/**
 * Error types raised by the Smart IDP connector. Use them in on-error handlers as SMART-IDP:&lt;TYPE&gt;.
 */
public enum SmartIdpError implements ErrorTypeDefinition<SmartIdpError> {
  CONNECTIVITY(MuleErrors.CONNECTIVITY),
  UNAUTHORIZED,
  FORBIDDEN,
  NOT_FOUND,
  BAD_REQUEST,
  CONFLICT,
  RATE_LIMITED,
  SERVER_ERROR,
  INVALID_INPUT,
  INVALID_DOCUMENT,
  FILE_TOO_LARGE(INVALID_DOCUMENT),
  TOO_MANY_PAGES(INVALID_DOCUMENT),
  UNSUPPORTED_FILE_TYPE(INVALID_DOCUMENT),
  EXECUTION_FAILED,
  TIMEOUT;

  private ErrorTypeDefinition<? extends Enum<?>> parent;

  SmartIdpError(ErrorTypeDefinition<? extends Enum<?>> parent) {
    this.parent = parent;
  }

  SmartIdpError() {}

  @Override
  public Optional<ErrorTypeDefinition<? extends Enum<?>>> getParent() {
    return Optional.ofNullable(parent);
  }
}
