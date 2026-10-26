/*
 * Smart IDP Connector
 * Copyright (c) 2026 Vijay Kumar Kothapalli
 * Licensed under the Apache License, Version 2.0
 */
package io.github.vijaykumarkothapalli.idp.internal.config;

import io.github.vijaykumarkothapalli.idp.internal.connection.SmartIdpConnectionProvider;
import io.github.vijaykumarkothapalli.idp.internal.operation.DocumentOperations;
import org.mule.runtime.extension.api.annotation.Configuration;
import org.mule.runtime.extension.api.annotation.Operations;
import org.mule.runtime.extension.api.annotation.connectivity.ConnectionProviders;
import org.mule.runtime.extension.api.annotation.param.ParameterGroup;

/**
 * Smart IDP configuration: Connected App connection, document limits and processing defaults.
 */
@Configuration(name = "config")
@ConnectionProviders(SmartIdpConnectionProvider.class)
@Operations(DocumentOperations.class)
public class SmartIdpConfiguration {

  @ParameterGroup(name = "Processing")
  private ProcessingDefaults processing;

  @ParameterGroup(name = "Document Limits")
  private ValidationDefaults validation;

  public ProcessingDefaults getProcessing() {
    return processing;
  }

  public ValidationDefaults getValidation() {
    return validation;
  }

  public String getDefaultAction() {
    return processing == null ? null : processing.getDefaultAction();
  }
}
