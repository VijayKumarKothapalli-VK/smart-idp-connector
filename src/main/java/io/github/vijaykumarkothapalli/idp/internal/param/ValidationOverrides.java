/*
 * Smart IDP Connector
 * Copyright (c) 2026 Vijay Kumar Kothapalli
 * Licensed under the Apache License, Version 2.0
 */
package io.github.vijaykumarkothapalli.idp.internal.param;

import org.mule.runtime.extension.api.annotation.param.Optional;
import org.mule.runtime.extension.api.annotation.param.Parameter;
import org.mule.runtime.extension.api.annotation.param.display.DisplayName;
import org.mule.runtime.extension.api.annotation.param.display.Example;
import org.mule.runtime.extension.api.annotation.param.display.Summary;

/**
 * Per operation overrides of the validation rules defined on the configuration. Empty values inherit the configuration.
 */
public class ValidationOverrides {

  @Parameter
  @Optional
  @DisplayName("Max File Size (MB)")
  @Summary("Overrides the configuration limit for this call. 0 means no limit.")
  @Example("10")
  private Double maxFileSizeMb;

  @Parameter
  @Optional
  @DisplayName("Max Pages")
  @Summary("Overrides the configuration limit for this call. 0 means no limit.")
  @Example("50")
  private Integer maxPages;

  @Parameter
  @Optional
  @DisplayName("Allowed File Types")
  @Summary("Overrides the configuration list for this call, e.g. PDF,PNG,JPEG,TIFF. Use * for any type.")
  @Example("PDF,PNG,JPEG,TIFF")
  private String allowedFileTypes;

  public ValidationOverrides() {}

  public ValidationOverrides(Double maxFileSizeMb, Integer maxPages, String allowedFileTypes) {
    this.maxFileSizeMb = maxFileSizeMb;
    this.maxPages = maxPages;
    this.allowedFileTypes = allowedFileTypes;
  }

  public Double getMaxFileSizeMb() {
    return maxFileSizeMb;
  }

  public Integer getMaxPages() {
    return maxPages;
  }

  public String getAllowedFileTypes() {
    return allowedFileTypes;
  }
}
