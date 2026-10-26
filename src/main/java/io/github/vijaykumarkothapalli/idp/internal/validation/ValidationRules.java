/*
 * Smart IDP Connector
 * Copyright (c) 2026 Vijay Kumar Kothapalli
 * Licensed under the Apache License, Version 2.0
 */
package io.github.vijaykumarkothapalli.idp.internal.validation;

import io.github.vijaykumarkothapalli.idp.internal.config.ValidationDefaults;
import io.github.vijaykumarkothapalli.idp.internal.param.ValidationOverrides;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Effective limits for one document: operation overrides win over the configuration defaults.
 */
public class ValidationRules {

  private final double maxFileSizeMb;
  private final int maxPages;
  private final List<DocumentType> allowedTypes;
  private final List<String> allowedTypeNames;

  public ValidationRules(double maxFileSizeMb, int maxPages, String allowedFileTypes) {
    this.maxFileSizeMb = maxFileSizeMb;
    this.maxPages = maxPages;
    List<DocumentType> types = new ArrayList<>();
    List<String> names = new ArrayList<>();
    if (allowedFileTypes != null && !allowedFileTypes.trim().isEmpty() && !allowedFileTypes.trim().equals("*")) {
      for (String name : allowedFileTypes.split("[,;\\s]+")) {
        if (!name.isEmpty()) {
          DocumentType type = DocumentType.parse(name);
          if (type != DocumentType.UNKNOWN && !types.contains(type)) {
            types.add(type);
            names.add(type.name());
          }
        }
      }
    }
    this.allowedTypes = Collections.unmodifiableList(types);
    this.allowedTypeNames = Collections.unmodifiableList(names);
  }

  public static ValidationRules of(ValidationDefaults defaults, ValidationOverrides overrides) {
    double size = defaults == null ? ValidationDefaults.IDP_API_MAX_FILE_SIZE_MB : defaults.getMaxFileSizeMb();
    int pages = defaults == null ? ValidationDefaults.IDP_API_MAX_PAGES : defaults.getMaxPages();
    String types = defaults == null ? ValidationDefaults.IDP_ACCEPTED_TYPES : defaults.getAllowedFileTypes();
    if (overrides != null) {
      if (overrides.getMaxFileSizeMb() != null) {
        size = overrides.getMaxFileSizeMb();
      }
      if (overrides.getMaxPages() != null) {
        pages = overrides.getMaxPages();
      }
      if (overrides.getAllowedFileTypes() != null && !overrides.getAllowedFileTypes().trim().isEmpty()) {
        types = overrides.getAllowedFileTypes();
      }
    }
    return new ValidationRules(size, pages, types);
  }

  public double getMaxFileSizeMb() {
    return maxFileSizeMb;
  }

  public int getMaxPages() {
    return maxPages;
  }

  /** Empty means every type is allowed. */
  public List<DocumentType> getAllowedTypes() {
    return allowedTypes;
  }

  public List<String> getAllowedTypeNames() {
    return allowedTypeNames;
  }
}
