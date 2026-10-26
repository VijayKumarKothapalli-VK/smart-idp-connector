/*
 * Smart IDP Connector
 * Copyright (c) 2026 Vijay Kumar Kothapalli
 * Licensed under the Apache License, Version 2.0
 */
package io.github.vijaykumarkothapalli.idp.internal.error;

import org.mule.runtime.extension.api.annotation.error.ErrorTypeProvider;
import org.mule.runtime.extension.api.error.ErrorTypeDefinition;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Errors the Smart IDP operations can raise.
 */
public class SmartIdpErrorProvider implements ErrorTypeProvider {

  @Override
  public Set<ErrorTypeDefinition> getErrorTypes() {
    return new HashSet<>(Arrays.asList(SmartIdpError.values()));
  }
}
