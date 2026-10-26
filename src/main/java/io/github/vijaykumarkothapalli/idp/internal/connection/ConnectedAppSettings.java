/*
 * Smart IDP Connector
 * Copyright (c) 2026 Vijay Kumar Kothapalli
 * Licensed under the Apache License, Version 2.0
 */
package io.github.vijaykumarkothapalli.idp.internal.connection;

import org.mule.runtime.extension.api.annotation.param.Parameter;
import org.mule.runtime.extension.api.annotation.param.display.DisplayName;
import org.mule.runtime.extension.api.annotation.param.display.Password;
import org.mule.runtime.extension.api.annotation.param.display.Placement;
import org.mule.runtime.extension.api.annotation.param.display.Summary;

/**
 * Anypoint Connected App that acts on its own behalf (client credentials).
 */
public class ConnectedAppSettings {

  @Parameter
  @DisplayName("Client Id")
  @Summary("Connected App (client credentials) with the 'Execute Published Actions' scope. "
      + "'Exchange Viewer' adds the document action and version pick lists.")
  @Placement(order = 1)
  private String clientId;

  @Parameter
  @Password
  @DisplayName("Client Secret")
  @Placement(order = 2)
  private String clientSecret;

  public String getClientId() {
    return clientId;
  }

  public String getClientSecret() {
    return clientSecret;
  }
}
