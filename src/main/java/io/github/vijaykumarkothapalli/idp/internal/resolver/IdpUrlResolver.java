/*
 * Smart IDP Connector
 * Copyright (c) 2026 Vijay Kumar Kothapalli
 * Licensed under the Apache License, Version 2.0
 */
package io.github.vijaykumarkothapalli.idp.internal.resolver;

import java.net.URI;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns whatever the user pastes into a structured {@link IdpTarget}.
 * <p>
 * Accepted inputs include:
 * <ul>
 * <li>Runtime URLs: https://idp-rt.us-east-1.anypoint.mulesoft.com/api/v1/organizations/{org}/actions/{action}/versions/1.0.0/executions/{id}</li>
 * <li>Platform URLs: https://anypoint.mulesoft.com/idp/api/v1/organizations/{org}/actions/{action}</li>
 * <li>Anypoint UI links containing organizations/.../actions/... anywhere (including after #)</li>
 * <li>Bare hosts: idp-rt.eu-central-1.eu1.anypoint.mulesoft.com, eu1.anypoint.mulesoft.com</li>
 * <li>Region shortcuts: US, EU</li>
 * <li>A bare action id (UUID)</li>
 * </ul>
 */
public final class IdpUrlResolver {

  public static final String US_PLATFORM = "https://anypoint.mulesoft.com";
  public static final String EU_PLATFORM = "https://eu1.anypoint.mulesoft.com";
  public static final String US_RUNTIME = "https://idp-rt.us-east-1.anypoint.mulesoft.com";
  public static final String EU_RUNTIME = "https://idp-rt.eu-central-1.eu1.anypoint.mulesoft.com";

  private static final String RUNTIME_HOST_PREFIX = "idp-rt.";
  private static final String UUID = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}";

  private static final Pattern UUID_ONLY = Pattern.compile("^" + UUID + "$");
  private static final Pattern ORGANIZATION = Pattern.compile("organi[sz]ations/(" + UUID + ")");
  private static final Pattern ACTION = Pattern.compile("actions/(" + UUID + ")");
  private static final Pattern VERSION = Pattern.compile("versions/([0-9A-Za-z.\\-]+)");
  private static final Pattern EXECUTION = Pattern.compile("(?:executions|reviews)/(" + UUID + ")");

  private static final Map<String, String> PLATFORM_TO_RUNTIME = new HashMap<>();
  private static final Map<String, String> REGION_TO_PLATFORM = new HashMap<>();

  static {
    PLATFORM_TO_RUNTIME.put(US_PLATFORM, US_RUNTIME);
    PLATFORM_TO_RUNTIME.put(EU_PLATFORM, EU_RUNTIME);

    REGION_TO_PLATFORM.put("US", US_PLATFORM);
    REGION_TO_PLATFORM.put("NA", US_PLATFORM);
    REGION_TO_PLATFORM.put("EU", EU_PLATFORM);
    REGION_TO_PLATFORM.put("EU1", EU_PLATFORM);
  }

  private IdpUrlResolver() {}

  public static boolean isUuid(String value) {
    return value != null && UUID_ONLY.matcher(value.trim()).matches();
  }

  /**
   * Resolves any supported input. Never returns null; unknown parts are left null.
   */
  public static IdpTarget resolve(String input) {
    IdpTarget target = new IdpTarget();
    target.setInput(input);
    target.setApiType("UNKNOWN");
    if (input == null || input.trim().isEmpty()) {
      return target;
    }
    String value = input.trim();

    if (isUuid(value)) {
      target.setActionId(value.toLowerCase(Locale.ROOT));
      return target;
    }

    String region = REGION_TO_PLATFORM.get(value.toUpperCase(Locale.ROOT));
    if (region != null) {
      applyHost(target, hostOf(region));
      return target;
    }

    String url = value.contains("://") ? value : "https://" + value;
    String host = null;
    String path = "";
    try {
      URI uri = URI.create(url.replace(" ", "%20"));
      host = uri.getHost();
      path = (uri.getRawPath() == null ? "" : uri.getRawPath()) + "#" + (uri.getRawFragment() == null ? "" : uri.getRawFragment());
    } catch (IllegalArgumentException e) {
      // Not a parsable URL; fall back to pattern matching on the raw text.
      path = value;
    }

    if (host != null && host.contains(".")) {
      applyHost(target, host.toLowerCase(Locale.ROOT));
    }

    target.setOrganizationId(lower(find(ORGANIZATION, path)));
    target.setActionId(lower(find(ACTION, path)));
    target.setExecutionId(lower(find(EXECUTION, path)));
    String version = find(VERSION, path);
    if (version != null && !version.equalsIgnoreCase("executions")) {
      target.setVersion(version);
    }

    if (path.contains("/idp/api/") || path.contains("/idp/")) {
      target.setApiType("PLATFORM");
    } else if (host != null && host.startsWith(RUNTIME_HOST_PREFIX)) {
      target.setApiType("RUNTIME");
    }
    return target;
  }

  /**
   * Derives the IDP Runtime base URL that belongs to an Anypoint Platform base URL, or null when it is not a known
   * control plane (the connector then discovers it from the document action itself).
   */
  public static String runtimeForPlatform(String platformBaseUrl) {
    return platformBaseUrl == null ? null : PLATFORM_TO_RUNTIME.get(trimSlash(platformBaseUrl));
  }

  /**
   * Derives the Anypoint Platform base URL from an IDP Runtime URL, e.g. idp-rt.eu-central-1.eu1.anypoint.mulesoft.com
   * -&gt; eu1.anypoint.mulesoft.com.
   */
  public static String platformForRuntime(String runtimeBaseUrl) {
    if (runtimeBaseUrl == null) {
      return null;
    }
    String host = hostOf(runtimeBaseUrl);
    if (host == null || !host.startsWith(RUNTIME_HOST_PREFIX)) {
      return null;
    }
    String rest = host.substring(RUNTIME_HOST_PREFIX.length());
    int dot = rest.indexOf('.');
    return dot < 0 ? null : "https://" + rest.substring(dot + 1);
  }

  public static String hostOf(String url) {
    if (url == null) {
      return null;
    }
    try {
      String host = URI.create(url.contains("://") ? url : "https://" + url).getHost();
      return host == null ? null : host.toLowerCase(Locale.ROOT);
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  public static String trimSlash(String url) {
    if (url == null) {
      return null;
    }
    String value = url.trim();
    while (value.endsWith("/")) {
      value = value.substring(0, value.length() - 1);
    }
    return value;
  }

  private static void applyHost(IdpTarget target, String host) {
    if (host.startsWith(RUNTIME_HOST_PREFIX)) {
      target.setRuntimeBaseUrl("https://" + host);
      target.setPlatformBaseUrl(platformForRuntime("https://" + host));
    } else {
      target.setPlatformBaseUrl("https://" + host);
      target.setRuntimeBaseUrl(runtimeForPlatform("https://" + host));
    }
  }

  private static String find(Pattern pattern, String text) {
    Matcher matcher = pattern.matcher(text);
    return matcher.find() ? matcher.group(1) : null;
  }

  private static String lower(String value) {
    return value == null ? null : value.toLowerCase(Locale.ROOT);
  }
}
