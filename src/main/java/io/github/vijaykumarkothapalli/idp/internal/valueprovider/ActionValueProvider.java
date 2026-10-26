/*
 * Smart IDP Connector
 * Copyright (c) 2026 Vijay Kumar Kothapalli
 * Licensed under the Apache License, Version 2.0
 */
package io.github.vijaykumarkothapalli.idp.internal.valueprovider;

import io.github.vijaykumarkothapalli.idp.internal.catalog.ActionCatalog;
import io.github.vijaykumarkothapalli.idp.internal.connection.SmartIdpConnection;
import io.github.vijaykumarkothapalli.idp.internal.error.SmartIdpException;
import org.mule.runtime.api.value.Value;
import org.mule.runtime.extension.api.annotation.param.Connection;
import org.mule.runtime.extension.api.values.ValueBuilder;
import org.mule.runtime.extension.api.values.ValueProvider;
import org.mule.runtime.extension.api.values.ValueResolvingException;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Pick list of the organization's published document actions, read from Exchange with the Connected App.
 */
public class ActionValueProvider implements ValueProvider {

  @Connection
  private SmartIdpConnection connection;

  @Override
  public Set<Value> resolve() throws ValueResolvingException {
    try {
      Set<Value> values = new LinkedHashSet<>();
      for (ActionCatalog.ActionInfo action : ActionCatalog.listActions(connection)) {
        String label = (action.getName() == null ? action.getActionId() : action.getName())
            + (action.getLatestVersion() == null ? "" : "  (latest " + action.getLatestVersion() + ")");
        values.add(ValueBuilder.newValue(action.getActionId()).withDisplayName(label).build());
      }
      return values;
    } catch (SmartIdpException e) {
      throw new ValueResolvingException("Could not list published IDP document actions from Exchange. The Connected App "
          + "needs the 'Exchange Viewer' scope. " + e.getMessage(), "CONNECTIVITY", e);
    }
  }
}
