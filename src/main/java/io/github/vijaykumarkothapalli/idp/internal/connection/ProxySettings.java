/*
 * Smart IDP Connector
 * Copyright (c) 2026 Vijay Kumar Kothapalli
 * Licensed under the Apache License, Version 2.0
 */
package io.github.vijaykumarkothapalli.idp.internal.connection;

import org.mule.runtime.extension.api.annotation.param.Optional;
import org.mule.runtime.extension.api.annotation.param.Parameter;
import org.mule.runtime.extension.api.annotation.param.display.DisplayName;
import org.mule.runtime.extension.api.annotation.param.display.Password;
import org.mule.runtime.extension.api.annotation.param.display.Summary;
import org.mule.runtime.http.api.client.proxy.ProxyConfig;

import java.util.Objects;

/**
 * Optional outbound HTTP proxy.
 */
public class ProxySettings implements ProxyConfig {

  @Parameter
  @DisplayName("Host")
  @Summary("Proxy host name. Requires the port to be set.")
  private String host;

  @Parameter
  @DisplayName("Port")
  private int port;

  @Parameter
  @Optional
  @DisplayName("Username")
  private String username;

  @Parameter
  @Optional
  @Password
  @DisplayName("Password")
  private String password;

  @Parameter
  @Optional
  @DisplayName("Non Proxied Hosts")
  @Summary("Comma separated list of hosts that bypass the proxy.")
  private String nonProxyHosts;

  @Override
  public String getHost() {
    return host;
  }

  @Override
  public int getPort() {
    return port;
  }

  @Override
  public String getUsername() {
    return username;
  }

  @Override
  public String getPassword() {
    return password;
  }

  @Override
  public String getNonProxyHosts() {
    return nonProxyHosts;
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof ProxySettings)) {
      return false;
    }
    ProxySettings that = (ProxySettings) o;
    return port == that.port && Objects.equals(host, that.host) && Objects.equals(username, that.username)
        && Objects.equals(password, that.password) && Objects.equals(nonProxyHosts, that.nonProxyHosts);
  }

  @Override
  public int hashCode() {
    return Objects.hash(host, port, username, password, nonProxyHosts);
  }
}
