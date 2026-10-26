/*
 * Smart IDP Connector
 * Copyright (c) 2026 Vijay Kumar Kothapalli
 * Licensed under the Apache License, Version 2.0
 */
package io.github.vijaykumarkothapalli.idp.internal.param;

import io.github.vijaykumarkothapalli.idp.api.ResponseMode;
import io.github.vijaykumarkothapalli.idp.api.TimeoutBehavior;
import org.mule.runtime.extension.api.annotation.param.Optional;
import org.mule.runtime.extension.api.annotation.param.Parameter;
import org.mule.runtime.extension.api.annotation.param.display.DisplayName;
import org.mule.runtime.extension.api.annotation.param.display.Example;
import org.mule.runtime.extension.api.annotation.param.display.Placement;
import org.mule.runtime.extension.api.annotation.param.display.Summary;

/**
 * How Process Document returns the result: wait for it, hand it to a callback, or both.
 */
public class ResponseOptions {

  @Parameter
  @Optional(defaultValue = "WAIT_FOR_RESULT")
  @DisplayName("Response Mode")
  @Summary("WAIT_FOR_RESULT: wait and return the extracted data. CALLBACK: return immediately; IDP calls the callback "
      + "URL when done. WAIT_WITH_CALLBACK_FALLBACK: wait up to Max Wait, and if IDP is slower it still calls the callback.")
  @Placement(order = 1)
  private ResponseMode responseMode;

  @Parameter
  @Optional
  @DisplayName("Callback URL")
  @Summary("HTTPS URL IDP calls when processing finishes (SUCCEEDED, FAILED or MANUAL_VALIDATION_REQUIRED). "
      + "Required for the callback modes unless a default is set on the configuration.")
  @Example("https://my-app.example.com/api/idp/callback?caseId=123")
  @Placement(order = 2)
  private String callbackUrl;

  @Parameter
  @Optional
  @DisplayName("Max Wait (seconds)")
  @Summary("Overrides the configuration (default 60). Ignored in CALLBACK mode.")
  @Example("60")
  @Placement(order = 3)
  private Integer maxWaitSeconds;

  @Parameter
  @Optional
  @DisplayName("When Max Wait Is Reached")
  @Summary("Overrides the configuration. RETURN_PENDING returns completed=false; RAISE_ERROR raises SMART-IDP:TIMEOUT.")
  @Placement(order = 4)
  private TimeoutBehavior timeoutBehavior;

  @Parameter
  @Optional(defaultValue = "false")
  @DisplayName("Include Confidence And Geometry")
  @Summary("Return confidence scores, page numbers and positions with every value (valueOnly=false).")
  @Placement(order = 5)
  private boolean includeConfidenceDetails;

  @Parameter
  @Optional(defaultValue = "true")
  @DisplayName("Raise Error When Execution Fails")
  @Summary("Raise SMART-IDP:EXECUTION_FAILED when IDP reports FAILED. When false the FAILED response is returned.")
  @Placement(order = 6)
  private boolean raiseErrorOnFailure;

  @Parameter
  @Optional
  @DisplayName("Salesforce Org Id Override")
  @Summary("Run the action against a specific connected Salesforce org (x-sfdc-core-tenant-id header).")
  @Placement(tab = "Advanced", order = 1)
  private String salesforceOrgId;

  public ResponseMode getResponseMode() {
    return responseMode == null ? ResponseMode.WAIT_FOR_RESULT : responseMode;
  }

  public String getCallbackUrl() {
    return callbackUrl;
  }

  public Integer getMaxWaitSeconds() {
    return maxWaitSeconds;
  }

  public TimeoutBehavior getTimeoutBehavior() {
    return timeoutBehavior;
  }

  public boolean isIncludeConfidenceDetails() {
    return includeConfidenceDetails;
  }

  public boolean isRaiseErrorOnFailure() {
    return raiseErrorOnFailure;
  }

  public String getSalesforceOrgId() {
    return salesforceOrgId;
  }
}
