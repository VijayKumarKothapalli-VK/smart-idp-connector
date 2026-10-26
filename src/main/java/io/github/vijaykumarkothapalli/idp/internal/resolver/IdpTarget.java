/*
 * Smart IDP Connector
 * Copyright (c) 2026 Vijay Kumar Kothapalli
 * Licensed under the Apache License, Version 2.0
 */
package io.github.vijaykumarkothapalli.idp.internal.resolver;

import com.google.gson.JsonObject;

/**
 * Everything the connector could work out from a pasted IDP URL, host name, region or identifier.
 * Any field may be null when the input did not contain it.
 */
public class IdpTarget {

  private String input;
  private String runtimeBaseUrl;
  private String platformBaseUrl;
  private String organizationId;
  private String actionId;
  private String version;
  private String executionId;
  private String apiType;

  public String getInput() {
    return input;
  }

  void setInput(String input) {
    this.input = input;
  }

  /** IDP Runtime (service) base URL, e.g. https://idp-rt.us-east-1.anypoint.mulesoft.com */
  public String getRuntimeBaseUrl() {
    return runtimeBaseUrl;
  }

  void setRuntimeBaseUrl(String runtimeBaseUrl) {
    this.runtimeBaseUrl = runtimeBaseUrl;
  }

  /** Anypoint Platform base URL, e.g. https://anypoint.mulesoft.com */
  public String getPlatformBaseUrl() {
    return platformBaseUrl;
  }

  void setPlatformBaseUrl(String platformBaseUrl) {
    this.platformBaseUrl = platformBaseUrl;
  }

  public String getOrganizationId() {
    return organizationId;
  }

  void setOrganizationId(String organizationId) {
    this.organizationId = organizationId;
  }

  public String getActionId() {
    return actionId;
  }

  void setActionId(String actionId) {
    this.actionId = actionId;
  }

  public String getVersion() {
    return version;
  }

  void setVersion(String version) {
    this.version = version;
  }

  public String getExecutionId() {
    return executionId;
  }

  void setExecutionId(String executionId) {
    this.executionId = executionId;
  }

  /** RUNTIME, PLATFORM or UNKNOWN depending on which API the URL pointed at. */
  public String getApiType() {
    return apiType;
  }

  void setApiType(String apiType) {
    this.apiType = apiType;
  }

  public JsonObject toJson() {
    JsonObject json = new JsonObject();
    json.addProperty("input", input);
    json.addProperty("apiType", apiType);
    json.addProperty("runtimeBaseUrl", runtimeBaseUrl);
    json.addProperty("platformBaseUrl", platformBaseUrl);
    json.addProperty("organizationId", organizationId);
    json.addProperty("actionId", actionId);
    json.addProperty("version", version);
    json.addProperty("executionId", executionId);
    return json;
  }
}
