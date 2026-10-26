/*
 * Smart IDP Connector
 * Copyright (c) 2026 Vijay Kumar Kothapalli
 * Licensed under the Apache License, Version 2.0
 */
package io.github.vijaykumarkothapalli.idp.internal.connection;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;

/**
 * Fully read HTTP response from an IDP API.
 */
public class IdpResponse {

  private final int statusCode;
  private final String reasonPhrase;
  private final byte[] body;
  private final String contentType;

  public IdpResponse(int statusCode, String reasonPhrase, byte[] body, String contentType) {
    this.statusCode = statusCode;
    this.reasonPhrase = reasonPhrase;
    this.body = body == null ? new byte[0] : body;
    this.contentType = contentType;
  }

  public int getStatusCode() {
    return statusCode;
  }

  public String getReasonPhrase() {
    return reasonPhrase;
  }

  public byte[] getBody() {
    return body;
  }

  public String getContentType() {
    return contentType;
  }

  public boolean isSuccess() {
    return statusCode >= 200 && statusCode < 300;
  }

  public String bodyAsString() {
    return new String(body, StandardCharsets.UTF_8);
  }

  /** Parses the body as JSON; an empty body becomes JsonNull. */
  public JsonElement json() {
    String text = bodyAsString().trim();
    return text.isEmpty() ? JsonNull.INSTANCE : JsonParser.parseString(text);
  }
}
