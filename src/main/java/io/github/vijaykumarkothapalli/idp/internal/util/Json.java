/*
 * Smart IDP Connector
 * Copyright (c) 2026 Vijay Kumar Kothapalli
 * Licensed under the Apache License, Version 2.0
 */
package io.github.vijaykumarkothapalli.idp.internal.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import io.github.vijaykumarkothapalli.idp.internal.error.SmartIdpError;
import io.github.vijaykumarkothapalli.idp.internal.error.SmartIdpException;
import org.apache.commons.io.IOUtils;
import org.mule.runtime.api.metadata.TypedValue;
import org.mule.runtime.extension.api.runtime.operation.Result;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.mule.runtime.api.metadata.MediaType.APPLICATION_JSON;

/**
 * Small JSON helpers shared by operations.
 */
public final class Json {

  private static final Gson GSON = new GsonBuilder().serializeNulls().disableHtmlEscaping().create();
  private static final Gson PRETTY = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

  private Json() {}

  public static byte[] toBytes(Object value) {
    String text = value instanceof JsonElement ? GSON.toJson((JsonElement) value) : GSON.toJson(value);
    return text.getBytes(StandardCharsets.UTF_8);
  }

  public static String toPrettyString(JsonElement value) {
    return PRETTY.toJson(value);
  }

  public static JsonElement toTree(Object value) {
    return GSON.toJsonTree(value);
  }

  /** Wraps a JSON value as an operation result with the application/json media type. */
  public static Result<InputStream, Void> result(Object value) {
    return result(toBytes(value));
  }

  public static Result<InputStream, Void> result(byte[] jsonBytes) {
    return Result.<InputStream, Void>builder()
        .output(new ByteArrayInputStream(jsonBytes))
        .length(jsonBytes.length)
        .mediaType(APPLICATION_JSON)
        .build();
  }

  /** Reads a JSON payload passed to an operation. Returns null for an empty payload. */
  public static JsonElement read(TypedValue<InputStream> value) throws SmartIdpException {
    if (value == null || value.getValue() == null) {
      return null;
    }
    try (InputStream stream = value.getValue()) {
      String text = IOUtils.toString(stream, StandardCharsets.UTF_8).trim();
      return text.isEmpty() ? null : JsonParser.parseString(text);
    } catch (IOException | JsonParseException e) {
      throw new SmartIdpException("The supplied body is not valid JSON: " + e.getMessage(), SmartIdpError.INVALID_INPUT, e);
    }
  }

  public static byte[] readBytes(TypedValue<InputStream> value) throws SmartIdpException {
    if (value == null || value.getValue() == null) {
      return null;
    }
    try (InputStream stream = value.getValue()) {
      return IOUtils.toByteArray(stream);
    } catch (IOException e) {
      throw new SmartIdpException("Could not read the supplied content: " + e.getMessage(), SmartIdpError.INVALID_INPUT, e);
    }
  }

  /** Follows a dotted path such as "configuration.content.output". Returns null when any step is missing. */
  public static JsonElement path(JsonElement root, String dottedPath) {
    JsonElement current = root;
    for (String part : dottedPath.split("\\.")) {
      if (current == null || !current.isJsonObject()) {
        return null;
      }
      current = current.getAsJsonObject().get(part);
    }
    return current == null || current.isJsonNull() ? null : current;
  }

  /** Returns the first path that resolves to a primitive, as a string. */
  public static String firstString(JsonElement root, String... dottedPaths) {
    for (String dottedPath : dottedPaths) {
      JsonElement value = path(root, dottedPath);
      if (value != null && value.isJsonPrimitive()) {
        return value.getAsString();
      }
    }
    return null;
  }

  /** Returns or creates the object at the given dotted path. */
  public static JsonObject objectAt(JsonObject root, String dottedPath) {
    JsonObject current = root;
    for (String part : dottedPath.split("\\.")) {
      JsonElement next = current.get(part);
      if (next == null || !next.isJsonObject()) {
        JsonObject created = new JsonObject();
        current.add(part, created);
        current = created;
      } else {
        current = next.getAsJsonObject();
      }
    }
    return current;
  }

  /**
   * Lists are returned either as a bare array or wrapped in an object (content, items, data, versions, actions...).
   */
  public static JsonArray asArray(JsonElement element) {
    if (element == null || element.isJsonNull()) {
      return new JsonArray();
    }
    if (element.isJsonArray()) {
      return element.getAsJsonArray();
    }
    if (element.isJsonObject()) {
      for (String key : new String[] {"content", "items", "data", "versions", "actions", "results", "reviews"}) {
        JsonElement value = element.getAsJsonObject().get(key);
        if (value != null && value.isJsonArray()) {
          return value.getAsJsonArray();
        }
      }
    }
    return new JsonArray();
  }
}
