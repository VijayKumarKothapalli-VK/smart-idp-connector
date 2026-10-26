/*
 * Smart IDP Connector
 * Copyright (c) 2026 Vijay Kumar Kothapalli
 * Licensed under the Apache License, Version 2.0
 */
package io.github.vijaykumarkothapalli.idp.internal.error;

import org.mule.runtime.extension.api.exception.ModuleException;

/**
 * Exception carrying a {@link SmartIdpError} so Mule flows can handle it by type.
 */
public class SmartIdpException extends ModuleException {

  private static final long serialVersionUID = 1L;

  public SmartIdpException(String message, SmartIdpError error) {
    super(message, error);
  }

  public SmartIdpException(String message, SmartIdpError error, Throwable cause) {
    super(message, error, cause);
  }

  /** Maps an HTTP status code returned by IDP to the matching error type. */
  public static SmartIdpError errorForStatus(int status) {
    if (status == 400 || status == 422) {
      return SmartIdpError.BAD_REQUEST;
    }
    if (status == 401) {
      return SmartIdpError.UNAUTHORIZED;
    }
    if (status == 403) {
      return SmartIdpError.FORBIDDEN;
    }
    if (status == 404) {
      return SmartIdpError.NOT_FOUND;
    }
    if (status == 409) {
      return SmartIdpError.CONFLICT;
    }
    if (status == 429) {
      return SmartIdpError.RATE_LIMITED;
    }
    if (status >= 500) {
      return SmartIdpError.SERVER_ERROR;
    }
    return SmartIdpError.CONNECTIVITY;
  }
}
