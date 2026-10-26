/*
 * Smart IDP Connector
 * Copyright (c) 2026 Vijay Kumar Kothapalli
 * Licensed under the Apache License, Version 2.0
 */
package io.github.vijaykumarkothapalli.idp.internal.connection;

import io.github.vijaykumarkothapalli.idp.internal.error.SmartIdpException;
import io.github.vijaykumarkothapalli.idp.internal.resolver.IdpTarget;
import io.github.vijaykumarkothapalli.idp.internal.resolver.IdpUrlResolver;
import org.mule.runtime.api.connection.CachedConnectionProvider;
import org.mule.runtime.api.connection.ConnectionException;
import org.mule.runtime.api.connection.ConnectionValidationResult;
import org.mule.runtime.api.exception.MuleException;
import org.mule.runtime.api.lifecycle.Initialisable;
import org.mule.runtime.api.lifecycle.InitialisationException;
import org.mule.runtime.api.lifecycle.Startable;
import org.mule.runtime.api.lifecycle.Stoppable;
import org.mule.runtime.api.tls.TlsContextFactory;
import org.mule.runtime.extension.api.annotation.Alias;
import org.mule.runtime.extension.api.annotation.param.Optional;
import org.mule.runtime.extension.api.annotation.param.Parameter;
import org.mule.runtime.extension.api.annotation.param.ParameterGroup;
import org.mule.runtime.extension.api.annotation.param.RefName;
import org.mule.runtime.extension.api.annotation.param.display.DisplayName;
import org.mule.runtime.extension.api.annotation.param.display.Placement;
import org.mule.runtime.extension.api.annotation.param.display.Summary;
import org.mule.runtime.http.api.HttpService;
import org.mule.runtime.http.api.client.HttpClient;
import org.mule.runtime.http.api.client.HttpClientConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;

import static org.mule.runtime.extension.api.annotation.param.display.Placement.SECURITY_TAB;

/**
 * Connects to MuleSoft IDP with a Connected App. Paste your document action's execution URL and the connector derives
 * the region, organization, action and version from it.
 */
@Alias("connected-app")
@DisplayName("Connected App")
public class SmartIdpConnectionProvider implements CachedConnectionProvider<SmartIdpConnection>, Initialisable,
    Startable, Stoppable {

  private static final Logger LOGGER = LoggerFactory.getLogger(SmartIdpConnectionProvider.class);

  @ParameterGroup(name = "IDP Endpoint")
  private EndpointSettings endpoint;

  @ParameterGroup(name = "Connected App")
  private ConnectedAppSettings connectedApp;

  @Parameter
  @Optional(defaultValue = "30000")
  @DisplayName("Response Timeout (ms)")
  @Summary("Maximum time to wait for a single HTTP response from IDP or Anypoint.")
  @Placement(tab = "Advanced", order = 3)
  private int responseTimeout;

  @Parameter
  @Optional
  @DisplayName("TLS Configuration")
  @Placement(tab = SECURITY_TAB)
  private TlsContextFactory tlsContext;

  @Parameter
  @Optional
  @DisplayName("Proxy")
  @Placement(tab = "Proxy")
  private ProxySettings proxy;

  @RefName
  private String configName;

  @Inject
  private HttpService httpService;

  private HttpClient httpClient;

  @Override
  public void initialise() throws InitialisationException {
    if (tlsContext instanceof Initialisable) {
      ((Initialisable) tlsContext).initialise();
    }
  }

  @Override
  public void start() throws MuleException {
    httpClient = httpService.getClientFactory().create(new HttpClientConfiguration.Builder()
        .setName(configName + "-smart-idp")
        .setTlsContextFactory(tlsContext)
        .setProxyConfig(proxy)
        .build());
    httpClient.start();
  }

  @Override
  public void stop() throws MuleException {
    if (httpClient != null) {
      httpClient.stop();
      httpClient = null;
    }
  }

  @Override
  public SmartIdpConnection connect() throws ConnectionException {
    IdpTarget target = IdpUrlResolver.resolve(endpoint.getIdpUrl());
    String platform = firstNonBlank(endpoint.getPlatformBaseUrlOverride(), target.getPlatformBaseUrl(),
                                    IdpUrlResolver.platformForRuntime(endpoint.getRuntimeBaseUrlOverride()),
                                    IdpUrlResolver.US_PLATFORM);
    String runtime = firstNonBlank(endpoint.getRuntimeBaseUrlOverride(), target.getRuntimeBaseUrl(),
                                   IdpUrlResolver.runtimeForPlatform(platform));
    String organization = firstNonBlank(endpoint.getOrganizationId(), target.getOrganizationId());

    LOGGER.info("Smart IDP [{}]: runtime={}, platform={}, organization={}, default action={}, default version={}",
                configName, runtime, platform, organization == null ? "(auto-detect)" : organization,
                target.getActionId() == null ? "(none)" : target.getActionId(),
                target.getVersion() == null ? "(latest)" : target.getVersion());

    // No network call here: the token and organization are resolved on first use, so document validation works
    // offline and invalid documents are rejected before anything is sent to IDP.
    return new SmartIdpConnection(httpClient, connectedApp, responseTimeout, IdpUrlResolver.trimSlash(platform),
                                  IdpUrlResolver.trimSlash(runtime), organization, target.getActionId(),
                                  target.getVersion());
  }

  @Override
  public void disconnect(SmartIdpConnection connection) {
    connection.invalidate();
  }

  @Override
  public ConnectionValidationResult validate(SmartIdpConnection connection) {
    try {
      connection.authenticate();
      connection.getOrganizationId();
      return ConnectionValidationResult.success();
    } catch (SmartIdpException e) {
      return ConnectionValidationResult.failure(e.getMessage(), new ConnectionException(e.getMessage(), e));
    } catch (RuntimeException e) {
      return ConnectionValidationResult.failure("Unexpected error validating the IDP connection: " + e.getMessage(), e);
    }
  }

  static String firstNonBlank(String... values) {
    for (String value : values) {
      if (value != null && !value.trim().isEmpty()) {
        return value.trim();
      }
    }
    return null;
  }
}
