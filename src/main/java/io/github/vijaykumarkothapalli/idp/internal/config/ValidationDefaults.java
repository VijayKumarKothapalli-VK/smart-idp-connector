/*
 * Smart IDP Connector
 * Copyright (c) 2026 Vijay Kumar Kothapalli
 * Licensed under the Apache License, Version 2.0
 */
package io.github.vijaykumarkothapalli.idp.internal.config;

import org.mule.runtime.extension.api.annotation.param.Optional;
import org.mule.runtime.extension.api.annotation.param.Parameter;
import org.mule.runtime.extension.api.annotation.param.display.DisplayName;
import org.mule.runtime.extension.api.annotation.param.display.Example;
import org.mule.runtime.extension.api.annotation.param.display.Placement;
import org.mule.runtime.extension.api.annotation.param.display.Summary;

/**
 * Document limits checked before a file is sent to IDP. Defaults are the IDP API limits (1 file per request, 150 pages,
 * 15 MB, PDF / PNG / JPG / TIFF). Every call can override them.
 */
public class ValidationDefaults {

  public static final double IDP_API_MAX_FILE_SIZE_MB = 15;
  public static final int IDP_API_MAX_PAGES = 150;
  public static final String IDP_ACCEPTED_TYPES = "PDF,PNG,JPEG,TIFF";

  @Parameter
  @Optional(defaultValue = "true")
  @DisplayName("Validate Before Submit")
  @Summary("Check file size, page count, type and readability before sending, so bad files fail fast with a clear error.")
  @Placement(order = 1)
  private boolean validateBeforeSubmit;

  @Parameter
  @Optional(defaultValue = "15")
  @DisplayName("Max File Size (MB)")
  @Summary("IDP API limit is 15 MB per file. Lower it for stricter projects; 0 disables the check.")
  @Placement(order = 2)
  private double maxFileSizeMb;

  @Parameter
  @Optional(defaultValue = "150")
  @DisplayName("Max Pages")
  @Summary("IDP API limit is 150 pages per file. Lower it for stricter projects; 0 disables the check.")
  @Placement(order = 3)
  private int maxPages;

  @Parameter
  @Optional(defaultValue = IDP_ACCEPTED_TYPES)
  @DisplayName("Allowed File Types")
  @Summary("IDP accepts PDF, PNG, JPG and TIFF (150 DPI or more recommended). Use * to skip the type check.")
  @Example(IDP_ACCEPTED_TYPES)
  @Placement(order = 4)
  private String allowedFileTypes;

  public boolean isValidateBeforeSubmit() {
    return validateBeforeSubmit;
  }

  public double getMaxFileSizeMb() {
    return maxFileSizeMb;
  }

  public int getMaxPages() {
    return maxPages;
  }

  public String getAllowedFileTypes() {
    return allowedFileTypes;
  }
}
