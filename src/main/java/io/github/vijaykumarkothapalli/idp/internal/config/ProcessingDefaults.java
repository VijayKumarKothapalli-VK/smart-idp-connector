/*
 * Smart IDP Connector
 * Copyright (c) 2026 Vijay Kumar Kothapalli
 * Licensed under the Apache License, Version 2.0
 */
package io.github.vijaykumarkothapalli.idp.internal.config;

import io.github.vijaykumarkothapalli.idp.api.TimeoutBehavior;
import org.mule.runtime.extension.api.annotation.param.Optional;
import org.mule.runtime.extension.api.annotation.param.Parameter;
import org.mule.runtime.extension.api.annotation.param.display.DisplayName;
import org.mule.runtime.extension.api.annotation.param.display.Example;
import org.mule.runtime.extension.api.annotation.param.display.Placement;
import org.mule.runtime.extension.api.annotation.param.display.Summary;

/**
 * Project level processing defaults. Each Process Document call can override them.
 */
public class ProcessingDefaults {

  @Parameter
  @Optional
  @DisplayName("Default Document Action")
  @Summary("Action id or execution URL used when an operation leaves the action empty. "
      + "Not needed when the connection IDP URL is already an execution URL.")
  @Example("https://idp-rt.us-east-1.anypoint.mulesoft.com/api/v1/organizations/ORG_ID/actions/ACTION_ID/versions/1.0.0/executions")
  @Placement(order = 1)
  private String defaultAction;

  @Parameter
  @Optional(defaultValue = "60")
  @DisplayName("Max Wait (seconds)")
  @Summary("How long Process Document waits for IDP to finish. 60s covers typical documents; raise it for large "
      + "multi-page files, or use CALLBACK mode for long-running ones.")
  @Placement(order = 2)
  private int maxWaitSeconds;

  @Parameter
  @Optional(defaultValue = "RETURN_PENDING")
  @DisplayName("When Max Wait Is Reached")
  @Summary("RETURN_PENDING returns completed=false with the execution id so it can be collected later; "
      + "RAISE_ERROR raises SMART-IDP:TIMEOUT.")
  @Placement(order = 3)
  private TimeoutBehavior timeoutBehavior;

  @Parameter
  @Optional
  @DisplayName("Default Callback URL")
  @Summary("HTTPS URL IDP calls when processing finishes. Used by the CALLBACK modes when the operation does not set one.")
  @Example("https://my-app.example.com/api/idp/callback")
  @Placement(order = 4)
  private String defaultCallbackUrl;

  @Parameter
  @Optional(defaultValue = "2")
  @DisplayName("First Status Check (seconds)")
  @Summary("Delay before the first status check. Later checks back off gradually up to 10 seconds.")
  @Placement(tab = "Advanced", order = 1)
  private int initialPollSeconds;

  public String getDefaultAction() {
    return defaultAction;
  }

  public int getMaxWaitSeconds() {
    return maxWaitSeconds;
  }

  public TimeoutBehavior getTimeoutBehavior() {
    return timeoutBehavior == null ? TimeoutBehavior.RETURN_PENDING : timeoutBehavior;
  }

  public String getDefaultCallbackUrl() {
    return defaultCallbackUrl;
  }

  public int getInitialPollSeconds() {
    return initialPollSeconds;
  }
}
