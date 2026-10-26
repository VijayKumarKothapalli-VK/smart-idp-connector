/*
 * Smart IDP Connector
 * Copyright (c) 2026 Vijay Kumar Kothapalli
 * Licensed under the Apache License, Version 2.0
 */
package io.github.vijaykumarkothapalli.idp;

import io.github.vijaykumarkothapalli.idp.internal.catalog.ActionCatalog;
import io.github.vijaykumarkothapalli.idp.internal.resolver.IdpTarget;
import io.github.vijaykumarkothapalli.idp.internal.resolver.IdpUrlResolver;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class IdpUrlResolverTest {

  private static final String ORG = "01f79c97-9a83-4194-b922-15b64fd7305e";
  private static final String ACTION = "c333413d-6593-4423-b3d4-f8ff4bf88917";
  private static final String EXECUTION = "c146a2cb-f271-414b-ba75-14f8e1aa85c0";

  @Test
  public void resolvesUsRuntimeExecutionUrl() {
    IdpTarget target = IdpUrlResolver.resolve("https://idp-rt.us-east-1.anypoint.mulesoft.com/api/v1/organizations/" + ORG
        + "/actions/" + ACTION + "/versions/1.2.0/executions/" + EXECUTION + "?valueOnly=true");
    assertEquals("https://idp-rt.us-east-1.anypoint.mulesoft.com", target.getRuntimeBaseUrl());
    assertEquals("https://anypoint.mulesoft.com", target.getPlatformBaseUrl());
    assertEquals(ORG, target.getOrganizationId());
    assertEquals(ACTION, target.getActionId());
    assertEquals("1.2.0", target.getVersion());
    assertEquals(EXECUTION, target.getExecutionId());
    assertEquals("RUNTIME", target.getApiType());
  }

  @Test
  public void resolvesEuRuntimeHostToEuPlatform() {
    IdpTarget target = IdpUrlResolver.resolve("idp-rt.eu-central-1.eu1.anypoint.mulesoft.com");
    assertEquals("https://idp-rt.eu-central-1.eu1.anypoint.mulesoft.com", target.getRuntimeBaseUrl());
    assertEquals("https://eu1.anypoint.mulesoft.com", target.getPlatformBaseUrl());
  }

  @Test
  public void resolvesPlatformUrl() {
    IdpTarget target = IdpUrlResolver.resolve("https://eu1.anypoint.mulesoft.com/idp/api/v1/organizations/" + ORG
        + "/actions/" + ACTION);
    assertEquals("https://eu1.anypoint.mulesoft.com", target.getPlatformBaseUrl());
    assertEquals("https://idp-rt.eu-central-1.eu1.anypoint.mulesoft.com", target.getRuntimeBaseUrl());
    assertEquals(ACTION, target.getActionId());
    assertNull(target.getVersion());
    assertEquals("PLATFORM", target.getApiType());
  }

  @Test
  public void resolvesIdsInsideUiFragment() {
    IdpTarget target = IdpUrlResolver.resolve("https://anypoint.mulesoft.com/idp/#/organizations/" + ORG
        + "/actions/" + ACTION + "/versions/2.0.0/overview");
    assertEquals(ORG, target.getOrganizationId());
    assertEquals(ACTION, target.getActionId());
    assertEquals("2.0.0", target.getVersion());
  }

  @Test
  public void resolvesRegionShortcutsAndBareIds() {
    assertEquals(IdpUrlResolver.EU_RUNTIME, IdpUrlResolver.resolve("eu").getRuntimeBaseUrl());
    assertEquals(IdpUrlResolver.US_PLATFORM, IdpUrlResolver.resolve("US").getPlatformBaseUrl());
    assertEquals(ACTION, IdpUrlResolver.resolve(ACTION.toUpperCase()).getActionId());
  }

  @Test
  public void unknownControlPlaneLeavesRuntimeForDiscovery() {
    IdpTarget target = IdpUrlResolver.resolve("https://gov.anypoint.mulesoft.com");
    assertEquals("https://gov.anypoint.mulesoft.com", target.getPlatformBaseUrl());
    assertNull(target.getRuntimeBaseUrl());
  }

  @Test
  public void comparesVersionsNumerically() {
    assertTrue(ActionCatalog.compareVersions("1.10.0", "1.9.3") > 0);
    assertTrue(ActionCatalog.compareVersions("2.0.0", "10.0.0") < 0);
    assertEquals(0, ActionCatalog.compareVersions("1.0", "1.0.0"));
  }
}
