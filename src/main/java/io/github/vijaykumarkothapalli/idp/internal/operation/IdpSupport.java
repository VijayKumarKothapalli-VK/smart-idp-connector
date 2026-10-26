/*
 * Smart IDP Connector
 * Copyright (c) 2026 Vijay Kumar Kothapalli
 * Licensed under the Apache License, Version 2.0
 */
package io.github.vijaykumarkothapalli.idp.internal.operation;

import io.github.vijaykumarkothapalli.idp.internal.catalog.ActionCatalog;
import io.github.vijaykumarkothapalli.idp.internal.config.SmartIdpConfiguration;
import io.github.vijaykumarkothapalli.idp.internal.connection.SmartIdpConnection;
import io.github.vijaykumarkothapalli.idp.internal.error.SmartIdpError;
import io.github.vijaykumarkothapalli.idp.internal.error.SmartIdpException;
import io.github.vijaykumarkothapalli.idp.internal.resolver.IdpTarget;
import io.github.vijaykumarkothapalli.idp.internal.resolver.IdpUrlResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Resolution rules shared by the operations: which action and which version to run.
 */
public final class IdpSupport {

  private static final Logger LOGGER = LoggerFactory.getLogger(IdpSupport.class);

  public static final String LATEST = "latest";

  private IdpSupport() {}

  /** A resolved action id together with any version found in the same reference. */
  public static final class ActionRef {

    private final String actionId;
    private final String version;
    private final String source;

    ActionRef(String actionId, String version, String source) {
      this.actionId = actionId;
      this.version = version;
      this.source = source;
    }

    public String getActionId() {
      return actionId;
    }

    /** Version carried by the same reference (for example an execution URL), or null. */
    public String getVersion() {
      return version;
    }

    /** Where the action came from: operation, configuration or connection. */
    public String getSource() {
      return source;
    }
  }

  /** Resolves the action from the operation value, then the configuration default, then the connection URL. */
  public static ActionRef action(SmartIdpConnection connection, SmartIdpConfiguration config, String reference)
      throws SmartIdpException {
    if (!isBlank(reference)) {
      IdpTarget target = IdpUrlResolver.resolve(reference);
      if (target.getActionId() == null) {
        throw new SmartIdpException("Could not find a document action id in '" + reference
            + "'. Pick an action, or use an action id or a URL that contains /actions/{actionId}.",
                                    SmartIdpError.INVALID_INPUT);
      }
      return new ActionRef(target.getActionId(), target.getVersion(), "operation");
    }
    String configDefault = config == null ? null : config.getDefaultAction();
    if (!isBlank(configDefault)) {
      IdpTarget target = IdpUrlResolver.resolve(configDefault);
      if (target.getActionId() != null) {
        return new ActionRef(target.getActionId(), target.getVersion(), "configuration");
      }
    }
    if (connection.getDefaultActionId() != null) {
      return new ActionRef(connection.getDefaultActionId(), connection.getDefaultVersion(), "connection URL");
    }
    throw new SmartIdpException("No document action given. Pick one on the operation, set 'Default Document Action' on "
        + "the configuration, or use your action's execution URL as the connection 'IDP URL'.",
                                SmartIdpError.INVALID_INPUT);
  }

  /**
   * Resolves the version: an explicit version wins; empty or 'latest' means the newest published version from Exchange.
   * If Exchange cannot be read, the version carried by the action reference is used instead.
   */
  public static String version(SmartIdpConnection connection, ActionRef action, String explicitVersion)
      throws SmartIdpException {
    if (!isBlank(explicitVersion) && !explicitVersion.trim().equalsIgnoreCase(LATEST)) {
      String requested = explicitVersion.trim();
      List<String> published;
      try {
        published = ActionCatalog.versions(connection, action.getActionId());
      } catch (SmartIdpException e) {
        LOGGER.debug("Smart IDP: could not verify version {} in Exchange ({}); submitting as given", requested, e.getType());
        return requested;
      }
      if (!published.isEmpty() && !published.contains(requested)) {
        throw new SmartIdpException("Version " + requested + " is not published for document action '"
            + ActionCatalog.actionName(connection, action.getActionId()) + "' (" + action.getActionId()
            + "). Published versions: " + String.join(", ", published) + ". Use 'latest' to always run the newest one.",
                                    SmartIdpError.NOT_FOUND);
      }
      return requested;
    }
    try {
      String latest = ActionCatalog.latestVersion(connection, action.getActionId());
      LOGGER.debug("Smart IDP: using latest published version {} of action {}", latest, action.getActionId());
      return latest;
    } catch (SmartIdpException e) {
      if (e.getType() == SmartIdpError.UNAUTHORIZED || e.getType() == SmartIdpError.CONNECTIVITY) {
        // Login or network failures are not a version problem: report them as they are.
        throw e;
      }
      if (!isBlank(action.getVersion())) {
        LOGGER.warn("Smart IDP: could not look up the latest version of action {} in Exchange ({}); using version {} "
            + "from the {}", action.getActionId(), e.getType(), action.getVersion(), action.getSource());
        return action.getVersion();
      }
      throw new SmartIdpException("Could not determine the latest version of action " + action.getActionId()
          + ". Give the Connected App the 'Exchange Viewer' scope or set the version explicitly. Cause: " + e.getMessage(),
                                  SmartIdpError.INVALID_INPUT, e);
    }
  }

  /** Extracts an execution id from an id or any URL that contains /executions/{id}. */
  public static String executionId(String reference) throws SmartIdpException {
    if (isBlank(reference)) {
      throw new SmartIdpException("An execution id is required.", SmartIdpError.INVALID_INPUT);
    }
    if (IdpUrlResolver.isUuid(reference)) {
      return reference.trim();
    }
    String id = IdpUrlResolver.resolve(reference).getExecutionId();
    if (id == null) {
      throw new SmartIdpException("Could not find an execution id in '" + reference + "'.", SmartIdpError.INVALID_INPUT);
    }
    return id;
  }

  public static boolean isBlank(String value) {
    return value == null || value.trim().isEmpty();
  }

  public static String firstNonBlank(String... values) {
    for (String value : values) {
      if (!isBlank(value)) {
        return value.trim();
      }
    }
    return null;
  }
}
