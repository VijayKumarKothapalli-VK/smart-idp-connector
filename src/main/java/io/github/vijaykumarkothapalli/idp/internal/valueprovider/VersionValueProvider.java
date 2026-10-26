/*
 * Smart IDP Connector
 * Copyright (c) 2026 Vijay Kumar Kothapalli
 * Licensed under the Apache License, Version 2.0
 */
package io.github.vijaykumarkothapalli.idp.internal.valueprovider;

import io.github.vijaykumarkothapalli.idp.internal.catalog.ActionCatalog;
import io.github.vijaykumarkothapalli.idp.internal.connection.SmartIdpConnection;
import io.github.vijaykumarkothapalli.idp.internal.error.SmartIdpException;
import io.github.vijaykumarkothapalli.idp.internal.operation.IdpSupport;
import io.github.vijaykumarkothapalli.idp.internal.resolver.IdpUrlResolver;
import org.mule.runtime.api.value.Value;
import org.mule.runtime.extension.api.annotation.param.Connection;
import org.mule.runtime.extension.api.annotation.param.Parameter;
import org.mule.runtime.extension.api.values.ValueBuilder;
import org.mule.runtime.extension.api.values.ValueProvider;
import org.mule.runtime.extension.api.values.ValueResolvingException;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Pick list of the published versions of the selected Document Action, newest first, plus "latest".
 * <p>
 * The action is a required acting parameter: the tooling only resolves this list once an action is selected, and an
 * empty action gives an empty list instead of falling back to another action's versions.
 */
public class VersionValueProvider implements ValueProvider {

  static final String LATEST_LABEL = "latest (newest published version)";

  @Connection
  private SmartIdpConnection connection;

  @Parameter
  private String action;

  @Override
  public Set<Value> resolve() throws ValueResolvingException {
    Set<Value> values = new LinkedHashSet<>();
    String actionId = IdpSupport.isBlank(action) ? null : IdpUrlResolver.resolve(action).getActionId();
    if (actionId == null) {
      return values;
    }
    try {
      String name = ActionCatalog.actionName(connection, actionId);
      values.add(ValueBuilder.newValue(IdpSupport.LATEST).withDisplayName(LATEST_LABEL).build());
      for (String version : ActionCatalog.versions(connection, actionId)) {
        values.add(ValueBuilder.newValue(version).withDisplayName(version + " (" + name + ")").build());
      }
      return values;
    } catch (SmartIdpException e) {
      throw new ValueResolvingException("Could not list versions of action " + actionId + " from Exchange: "
          + e.getMessage(), "CONNECTIVITY", e);
    }
  }
}
