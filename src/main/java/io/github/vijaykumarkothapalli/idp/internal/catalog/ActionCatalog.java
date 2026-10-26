/*
 * Smart IDP Connector
 * Copyright (c) 2026 Vijay Kumar Kothapalli
 * Licensed under the Apache License, Version 2.0
 */
package io.github.vijaykumarkothapalli.idp.internal.catalog;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import io.github.vijaykumarkothapalli.idp.internal.connection.SmartIdpConnection;
import io.github.vijaykumarkothapalli.idp.internal.error.SmartIdpError;
import io.github.vijaykumarkothapalli.idp.internal.error.SmartIdpException;
import io.github.vijaykumarkothapalli.idp.internal.util.Json;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static io.github.vijaykumarkothapalli.idp.internal.connection.SmartIdpConnection.urlEncode;

/**
 * Published IDP document actions, read from Anypoint Exchange. Every published action is an Exchange asset named
 * "idp-action-{actionId}" whose versions are the published action versions. Exchange is readable by a client
 * credentials Connected App, whereas the IDP design-time API is not.
 */
public final class ActionCatalog {

  private static final Logger LOGGER = LoggerFactory.getLogger(ActionCatalog.class);

  public static final String ASSET_PREFIX = "idp-action-";
  private static final int PAGE_SIZE = 100;
  private static final int MAX_ASSETS = 1000;
  private static final long CACHE_MILLIS = 5 * 60_000L;

  private static final Map<String, Cached<Published>> VERSION_CACHE = new ConcurrentHashMap<>();

  private ActionCatalog() {}

  /** A published document action. */
  public static final class ActionInfo {

    private final String actionId;
    private final String name;
    private final String latestVersion;

    ActionInfo(String actionId, String name, String latestVersion) {
      this.actionId = actionId;
      this.name = name;
      this.latestVersion = latestVersion;
    }

    public String getActionId() {
      return actionId;
    }

    public String getName() {
      return name;
    }

    public String getLatestVersion() {
      return latestVersion;
    }
  }

  /** Lists the published document actions of the organization. */
  public static List<ActionInfo> listActions(SmartIdpConnection connection) throws SmartIdpException {
    String organization = connection.getOrganizationId();
    List<ActionInfo> actions = new ArrayList<>();
    for (int offset = 0; offset < MAX_ASSETS; offset += PAGE_SIZE) {
      Map<String, String> query = new LinkedHashMap<>();
      query.put("organizationId", organization);
      query.put("search", ASSET_PREFIX);
      query.put("types", "rest-api");
      query.put("limit", String.valueOf(PAGE_SIZE));
      query.put("offset", String.valueOf(offset));
      JsonArray page = Json.asArray(connection.getJson(connection.exchangeUrl("/v2/assets/search"), query).json());
      for (JsonElement asset : page) {
        String assetId = Json.firstString(asset, "assetId");
        if (assetId != null && assetId.startsWith(ASSET_PREFIX)) {
          String actionId = assetId.substring(ASSET_PREFIX.length());
          actions.add(new ActionInfo(actionId, Json.firstString(asset, "name"), Json.firstString(asset, "version")));
        }
      }
      if (page.size() < PAGE_SIZE) {
        break;
      }
    }
    actions.sort(Comparator.comparing(a -> a.getName() == null ? a.getActionId() : a.getName().toLowerCase()));
    LOGGER.debug("Smart IDP: found {} published document actions in Exchange", actions.size());
    return actions;
  }

  /** Published versions of an action, newest first. */
  public static List<String> versions(SmartIdpConnection connection, String actionId) throws SmartIdpException {
    return published(connection, actionId).versions;
  }

  /** Display name of an action as published in Exchange, or the id when it has no name. */
  public static String actionName(SmartIdpConnection connection, String actionId) throws SmartIdpException {
    String name = published(connection, actionId).name;
    return name == null || name.trim().isEmpty() ? actionId : name;
  }

  private static Published published(SmartIdpConnection connection, String actionId) throws SmartIdpException {
    String organization = connection.getOrganizationId();
    String key = organization + "/" + actionId;
    Cached<Published> cached = VERSION_CACHE.get(key);
    if (cached != null && !cached.isExpired()) {
      return cached.value;
    }
    JsonElement asset = connection.getJson(connection.exchangeUrl("/v1/assets/" + urlEncode(organization) + "/"
        + urlEncode(ASSET_PREFIX + actionId)), null).json();
    List<String> versions = new ArrayList<>();
    JsonElement list = Json.path(asset, "versions");
    if (list != null && list.isJsonArray()) {
      for (JsonElement version : list.getAsJsonArray()) {
        String value = Json.firstString(version, "version");
        String status = Json.firstString(version, "status");
        if (value != null && (status == null || "published".equalsIgnoreCase(status))) {
          versions.add(value);
        }
      }
    }
    String current = Json.firstString(asset, "version");
    if (current != null && !versions.contains(current)) {
      versions.add(current);
    }
    versions.sort((left, right) -> compareVersions(right, left));
    Published result = new Published(Json.firstString(asset, "name"), versions);
    VERSION_CACHE.put(key, new Cached<>(result));
    return result;
  }

  private static final class Published {

    private final String name;
    private final List<String> versions;

    Published(String name, List<String> versions) {
      this.name = name;
      this.versions = versions;
    }
  }

  /** Newest published version of an action. */
  public static String latestVersion(SmartIdpConnection connection, String actionId) throws SmartIdpException {
    List<String> versions = versions(connection, actionId);
    if (versions.isEmpty()) {
      throw new SmartIdpException("Document action " + actionId + " has no published version in Exchange. "
          + "Publish the action first.", SmartIdpError.NOT_FOUND);
    }
    return versions.get(0);
  }

  /** Compares dotted versions numerically (1.10.0 &gt; 1.9.0); non numeric parts compare as text. */
  public static int compareVersions(String left, String right) {
    String[] a = left.split("[.\\-]");
    String[] b = right.split("[.\\-]");
    for (int i = 0; i < Math.max(a.length, b.length); i++) {
      String x = i < a.length ? a[i] : "0";
      String y = i < b.length ? b[i] : "0";
      int result;
      try {
        result = Long.compare(Long.parseLong(x), Long.parseLong(y));
      } catch (NumberFormatException e) {
        result = x.compareTo(y);
      }
      if (result != 0) {
        return result;
      }
    }
    return 0;
  }

  /** Forgets cached versions, e.g. after publishing a new one. */
  public static void clearCache() {
    VERSION_CACHE.clear();
  }

  private static final class Cached<T> {

    private final T value;
    private final long expiresAt = System.currentTimeMillis() + CACHE_MILLIS;

    Cached(T value) {
      this.value = value;
    }

    boolean isExpired() {
      return System.currentTimeMillis() > expiresAt;
    }
  }
}
