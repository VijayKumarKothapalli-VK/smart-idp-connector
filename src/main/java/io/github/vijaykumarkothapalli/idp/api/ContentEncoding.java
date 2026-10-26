/*
 * Smart IDP Connector
 * Copyright (c) 2026 Vijay Kumar Kothapalli
 * Licensed under the Apache License, Version 2.0
 */
package io.github.vijaykumarkothapalli.idp.api;

/**
 * Encoding of the document content handed to the connector.
 */
public enum ContentEncoding {
  /** Detect automatically: raw binary or Base64 text (with or without a data URI prefix). */
  AUTO,
  /** The content is raw binary. */
  BINARY,
  /** The content is Base64 text. */
  BASE64
}
