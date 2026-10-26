/*
 * Smart IDP Connector
 * Copyright (c) 2026 Vijay Kumar Kothapalli
 * Licensed under the Apache License, Version 2.0
 */
package io.github.vijaykumarkothapalli.idp.internal.connection;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.vijaykumarkothapalli.idp.internal.error.SmartIdpError;
import io.github.vijaykumarkothapalli.idp.internal.error.SmartIdpException;
import io.github.vijaykumarkothapalli.idp.internal.util.Json;
import org.apache.commons.io.IOUtils;
import org.mule.runtime.http.api.client.HttpClient;
import org.mule.runtime.http.api.client.HttpRequestOptions;
import org.mule.runtime.http.api.domain.entity.ByteArrayHttpEntity;
import org.mule.runtime.http.api.domain.entity.HttpEntity;
import org.mule.runtime.http.api.domain.message.request.HttpRequest;
import org.mule.runtime.http.api.domain.message.request.HttpRequestBuilder;
import org.mule.runtime.http.api.domain.message.response.HttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/**
 * Authenticated client for the IDP Runtime API and the Exchange catalog. The access token is cached and refreshed
 * automatically, and a request that fails with 401 is retried once with a fresh token. Tokens and secrets are never
 * logged.
 */
public class SmartIdpConnection {

  private static final Logger LOGGER = LoggerFactory.getLogger(SmartIdpConnection.class);

  public static final String RUNTIME_API = "/api/v1/organizations/";

  private static final long TOKEN_SAFETY_MARGIN_MILLIS = 60_000L;
  private static final long DEFAULT_TOKEN_LIFETIME_MILLIS = 30 * 60_000L;
  private static final int MAX_ERROR_BODY = 2000;

  private final HttpClient httpClient;
  private final ConnectedAppSettings connectedApp;
  private final int responseTimeoutMillis;
  private final String platformBaseUrl;
  private final String runtimeBaseUrl;
  private final String defaultActionId;
  private final String defaultVersion;
  private String organizationId;

  private String accessToken;
  private long accessTokenExpiresAt;

  public SmartIdpConnection(HttpClient httpClient, ConnectedAppSettings connectedApp, int responseTimeoutMillis,
                            String platformBaseUrl, String runtimeBaseUrl, String organizationId,
                            String defaultActionId, String defaultVersion) {
    this.httpClient = httpClient;
    this.connectedApp = connectedApp;
    this.responseTimeoutMillis = responseTimeoutMillis;
    this.platformBaseUrl = platformBaseUrl;
    this.runtimeBaseUrl = runtimeBaseUrl;
    this.organizationId = organizationId;
    this.defaultActionId = defaultActionId;
    this.defaultVersion = defaultVersion;
  }

  public String getPlatformBaseUrl() {
    return platformBaseUrl;
  }

  public String getRuntimeBaseUrl() {
    return runtimeBaseUrl;
  }

  /** Action id taken from the connection URL; used when an operation leaves the action empty. */
  public String getDefaultActionId() {
    return defaultActionId;
  }

  /** Version taken from the connection URL. */
  public String getDefaultVersion() {
    return defaultVersion;
  }

  /** Organization id; detected from the Connected App on first use when it was not configured or in the URL. */
  public synchronized String getOrganizationId() throws SmartIdpException {
    if (organizationId == null || organizationId.isEmpty()) {
      JsonElement me = sendOrFail("GET", platformBaseUrl + "/accounts/api/me", null, null, null).json();
      String detected = Json.firstString(me, "client.org_id", "client.organization.id", "user.organization.id");
      if (detected == null) {
        throw new SmartIdpException("Could not detect the organization id. Set 'Organization Id' on the connection "
            + "or paste an IDP URL that contains /organizations/{id}.", SmartIdpError.INVALID_INPUT);
      }
      LOGGER.info("Smart IDP: detected organization id {} from the Connected App", detected);
      organizationId = detected;
    }
    return organizationId;
  }

  /** IDP Runtime API URL for this organization, e.g. .../api/v1/organizations/{org}/actions/... */
  public String runtimeUrl(String path) throws SmartIdpException {
    if (runtimeBaseUrl == null) {
      throw new SmartIdpException("The IDP runtime host could not be derived from the IDP URL. Paste your action's "
          + "execution URL or set 'Runtime Base URL Override'.", SmartIdpError.INVALID_INPUT);
    }
    return runtimeBaseUrl + RUNTIME_API + getOrganizationId() + path;
  }

  /** Anypoint Exchange API URL, e.g. {platform}/exchange/api/v2/assets/search */
  public String exchangeUrl(String path) {
    return platformBaseUrl + "/exchange/api" + path;
  }

  // ---------------------------------------------------------------------------------------------------------------
  // Requests
  // ---------------------------------------------------------------------------------------------------------------

  public IdpResponse getJson(String url, Map<String, String> query) throws SmartIdpException {
    Map<String, String> headers = new LinkedHashMap<>();
    headers.put("Accept", "application/json");
    return sendOrFail("GET", url, query, headers, null);
  }

  /** Sends an authenticated request and raises a typed error for any non 2xx response. */
  public IdpResponse sendOrFail(String method, String url, Map<String, String> query, Map<String, String> headers,
                                Supplier<HttpEntity> entity) throws SmartIdpException {
    IdpResponse response = send(method, url, query, headers, entity);
    if (!response.isSuccess()) {
      throw httpError(method, url, response);
    }
    return response;
  }

  /** Sends an authenticated request and returns the response whatever its status. */
  public IdpResponse send(String method, String url, Map<String, String> query, Map<String, String> headers,
                          Supplier<HttpEntity> entity) throws SmartIdpException {
    long started = System.currentTimeMillis();
    IdpResponse response = sendWithToken(method, url, query, headers, entity, accessToken(false));
    if (response.getStatusCode() == 401) {
      LOGGER.info("Smart IDP: access token rejected (401), refreshing and retrying {} {}", method, url);
      response = sendWithToken(method, url, query, headers, entity, accessToken(true));
    }
    LOGGER.debug("Smart IDP: {} {} -> HTTP {} in {} ms", method, url, response.getStatusCode(),
                 System.currentTimeMillis() - started);
    return response;
  }

  private IdpResponse sendWithToken(String method, String url, Map<String, String> query, Map<String, String> headers,
                                    Supplier<HttpEntity> entity, String token) throws SmartIdpException {
    Map<String, String> allHeaders = new LinkedHashMap<>();
    if (headers != null) {
      allHeaders.putAll(headers);
    }
    allHeaders.put("Authorization", "Bearer " + token);
    return rawSend(method, url, query, allHeaders, entity == null ? null : entity.get());
  }

  private IdpResponse rawSend(String method, String url, Map<String, String> query, Map<String, String> headers,
                              HttpEntity entity) throws SmartIdpException {
    HttpRequestBuilder builder = HttpRequest.builder().method(method).uri(url);
    if (query != null) {
      query.forEach((key, value) -> {
        if (value != null) {
          builder.addQueryParam(key, value);
        }
      });
    }
    if (headers != null) {
      headers.forEach(builder::addHeader);
    }
    if (entity != null) {
      builder.entity(entity);
    }
    HttpRequestOptions options = HttpRequestOptions.builder()
        .responseTimeout(responseTimeoutMillis)
        .followsRedirect(true)
        .build();
    try {
      HttpResponse response = httpClient.send(builder.build(), options);
      byte[] body;
      try (InputStream content = response.getEntity().getContent()) {
        body = IOUtils.toByteArray(content);
      }
      return new IdpResponse(response.getStatusCode(), response.getReasonPhrase(), body,
                             response.getHeaderValue("Content-Type"));
    } catch (TimeoutException e) {
      throw new SmartIdpException("No response within " + responseTimeoutMillis + " ms from " + method + " " + url,
                                  SmartIdpError.CONNECTIVITY, e);
    } catch (IOException e) {
      throw new SmartIdpException("Could not reach " + url + ": " + e.getMessage(), SmartIdpError.CONNECTIVITY, e);
    }
  }

  public static SmartIdpException httpError(String method, String url, IdpResponse response) {
    String body = response.bodyAsString();
    if (body.length() > MAX_ERROR_BODY) {
      body = body.substring(0, MAX_ERROR_BODY) + "...";
    }
    JsonObject details = new JsonObject();
    details.addProperty("status", response.getStatusCode());
    details.addProperty("reason", response.getReasonPhrase());
    details.addProperty("method", method);
    details.addProperty("url", url);
    details.addProperty("response", body);
    String hint = response.getStatusCode() == 403
        ? " The Connected App is missing a scope: 'Execute Published Actions' for IDP calls, 'Exchange Viewer' for "
            + "action/version lookups."
        : "";
    return new SmartIdpException("IDP request failed: " + Json.toPrettyString(details) + hint,
                                 SmartIdpException.errorForStatus(response.getStatusCode()));
  }

  // ---------------------------------------------------------------------------------------------------------------
  // Authentication (Connected App, client credentials)
  // ---------------------------------------------------------------------------------------------------------------

  private synchronized String accessToken(boolean forceRefresh) throws SmartIdpException {
    if (!forceRefresh && accessToken != null && System.currentTimeMillis() < accessTokenExpiresAt) {
      return accessToken;
    }
    requireCredential(connectedApp.getClientId(), "Client Id");
    requireCredential(connectedApp.getClientSecret(), "Client Secret");
    String form = "grant_type=client_credentials"
        + "&client_id=" + urlEncode(connectedApp.getClientId())
        + "&client_secret=" + urlEncode(connectedApp.getClientSecret());
    Map<String, String> headers = new LinkedHashMap<>();
    headers.put("Content-Type", "application/x-www-form-urlencoded");
    headers.put("Accept", "application/json");
    IdpResponse response = rawSend("POST", platformBaseUrl + "/accounts/api/v2/oauth2/token", Collections.emptyMap(),
                                   headers, new ByteArrayHttpEntity(form.getBytes(StandardCharsets.UTF_8)));
    if (!response.isSuccess()) {
      LOGGER.error("Smart IDP: Connected App authentication failed with HTTP {} at {}", response.getStatusCode(),
                   platformBaseUrl);
      throw new SmartIdpException("Connected App authentication failed (HTTP " + response.getStatusCode() + "). Check the "
          + "Client Id / Client Secret and that the IDP URL points to the right control plane. Response: "
          + response.bodyAsString(), SmartIdpError.UNAUTHORIZED);
    }
    JsonElement json = response.json();
    String token = Json.firstString(json, "access_token");
    if (token == null) {
      throw new SmartIdpException("The token endpoint returned no access_token.", SmartIdpError.UNAUTHORIZED);
    }
    long lifetime = DEFAULT_TOKEN_LIFETIME_MILLIS;
    String expiresIn = Json.firstString(json, "expires_in");
    if (expiresIn != null) {
      try {
        lifetime = Long.parseLong(expiresIn) * 1000L;
      } catch (NumberFormatException e) {
        // keep the default lifetime
      }
    }
    accessToken = token;
    accessTokenExpiresAt = System.currentTimeMillis() + Math.max(lifetime - TOKEN_SAFETY_MARGIN_MILLIS, 30_000L);
    LOGGER.info("Smart IDP: obtained Connected App access token (valid ~{} min)", lifetime / 60_000L);
    return accessToken;
  }

  private static void requireCredential(String value, String name) throws SmartIdpException {
    if (value == null || value.trim().isEmpty()) {
      throw new SmartIdpException("'" + name + "' is required.", SmartIdpError.INVALID_INPUT);
    }
  }

  /** Forces a token request; used by connection validation. */
  public void authenticate() throws SmartIdpException {
    accessToken(true);
  }

  public static String urlEncode(String value) {
    try {
      return URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20");
    } catch (java.io.UnsupportedEncodingException e) {
      throw new IllegalStateException(e);
    }
  }

  public void invalidate() {
    accessToken = null;
  }
}
