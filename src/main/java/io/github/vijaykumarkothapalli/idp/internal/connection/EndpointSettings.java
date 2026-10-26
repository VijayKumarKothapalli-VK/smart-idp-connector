/*
 * Smart IDP Connector
 * Copyright (c) 2026 Vijay Kumar Kothapalli
 * Licensed under the Apache License, Version 2.0
 */
package io.github.vijaykumarkothapalli.idp.internal.connection;

import org.mule.runtime.extension.api.annotation.param.Optional;
import org.mule.runtime.extension.api.annotation.param.Parameter;
import org.mule.runtime.extension.api.annotation.param.display.DisplayName;
import org.mule.runtime.extension.api.annotation.param.display.Example;
import org.mule.runtime.extension.api.annotation.param.display.Placement;
import org.mule.runtime.extension.api.annotation.param.display.Summary;

/**
 * Where the IDP APIs live. Paste any IDP URL and the connector works out the rest.
 */
public class EndpointSettings {

  /**
   * Any IDP URL: an action execution URL, a platform URL, an Anypoint IDP page link, a host name, or simply US / EU.
   */
  @Parameter
  @Optional(defaultValue = "US")
  @DisplayName("IDP URL / Host / Region")
  @Summary("Paste any IDP URL (for example the execution URL of your document action), a host name, or a region "
      + "shortcut (US, EU). Hosts, organization, action and version are detected automatically.")
  @Example("https://idp-rt.us-east-1.anypoint.mulesoft.com/api/v1/organizations/ORG_ID/actions/ACTION_ID/versions/1.0.0/executions")
  @Placement(order = 1)
  private String idpUrl;

  /**
   * Anypoint organization (business group) id. Leave empty to take it from the URL or from the authenticated account.
   */
  @Parameter
  @Optional
  @DisplayName("Organization Id")
  @Summary("Leave empty to detect it from the IDP URL or from the authenticated account.")
  @Placement(order = 2)
  private String organizationId;

  /**
   * Override for the IDP Runtime base URL (advanced).
   */
  @Parameter
  @Optional
  @DisplayName("Runtime Base URL Override")
  @Summary("Advanced. Only needed for control planes the connector cannot derive automatically.")
  @Example("https://idp-rt.us-east-1.anypoint.mulesoft.com")
  @Placement(tab = "Advanced", order = 1)
  private String runtimeBaseUrlOverride;

  /**
   * Override for the Anypoint Platform base URL (advanced).
   */
  @Parameter
  @Optional
  @DisplayName("Platform Base URL Override")
  @Summary("Advanced. Only needed for control planes the connector cannot derive automatically.")
  @Example("https://anypoint.mulesoft.com")
  @Placement(tab = "Advanced", order = 2)
  private String platformBaseUrlOverride;

  public String getIdpUrl() {
    return idpUrl;
  }

  public String getOrganizationId() {
    return organizationId;
  }

  public String getRuntimeBaseUrlOverride() {
    return runtimeBaseUrlOverride;
  }

  public String getPlatformBaseUrlOverride() {
    return platformBaseUrlOverride;
  }
}
