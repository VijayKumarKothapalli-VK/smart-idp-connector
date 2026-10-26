/*
 * Smart IDP Connector
 * Copyright (c) 2026 Vijay Kumar Kothapalli
 * Licensed under the Apache License, Version 2.0
 */
package io.github.vijaykumarkothapalli.idp.internal.operation;

import com.google.gson.JsonObject;
import io.github.vijaykumarkothapalli.idp.api.ResponseMode;
import io.github.vijaykumarkothapalli.idp.internal.error.SmartIdpError;
import io.github.vijaykumarkothapalli.idp.internal.error.SmartIdpException;
import io.github.vijaykumarkothapalli.idp.internal.validation.DocumentType;
import io.github.vijaykumarkothapalli.idp.internal.validation.PreparedDocument;
import org.junit.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class DocumentOperationsLogicTest {

  @Test
  public void documentedStatusesAreClassified() {
    for (String waiting : new String[] {"ACKNOWLEDGED", "IN_PROGRESS", "RESULTS_PENDING", "in_progress"}) {
      assertTrue(waiting, ExecutionStatus.isInProgress(waiting));
    }
    for (String done : new String[] {"SUCCEEDED", "PARTIAL_SUCCESS", "MANUAL_VALIDATION_REQUIRED", "FAILED"}) {
      assertFalse(done, ExecutionStatus.isInProgress(done));
    }
    assertTrue(ExecutionStatus.isFailed("FAILED"));
    assertFalse(ExecutionStatus.isFailed("MANUAL_VALIDATION_REQUIRED"));
    // Unknown or missing statuses never keep the flow waiting forever.
    assertFalse(ExecutionStatus.isInProgress(null));
    assertFalse(ExecutionStatus.isInProgress("SOMETHING_NEW"));
  }

  @Test
  public void pollingBacksOffAndRespectsDeadline() {
    assertEquals(3000, ExecutionStatus.nextDelayMillis(2000, 60_000));
    assertEquals(10_000, ExecutionStatus.nextDelayMillis(9000, 60_000));
    assertEquals(10_000, ExecutionStatus.nextDelayMillis(10_000, 60_000));
    assertEquals(1500, ExecutionStatus.nextDelayMillis(4000, 1500));
    assertEquals(0, ExecutionStatus.nextDelayMillis(4000, -5));
  }

  @Test
  public void callbackMustBeHttps() throws SmartIdpException {
    assertEquals("https://x.example.com/cb?id=1",
                 DocumentOperations.callbackUrl(" https://x.example.com/cb?id=1 ", ResponseMode.CALLBACK));
    expectInvalid("http://x.example.com/cb");
    expectInvalid("x.example.com/cb");
    expectInvalid("");
  }

  @Test
  public void multipartContainsFileAndOnlyOptionalCallback() {
    PreparedDocument document = new PreparedDocument("%PDF-1.7 test".getBytes(StandardCharsets.US_ASCII), "a.pdf",
                                                     DocumentType.PDF, false);
    String withCallback = new String(DocumentOperations.multipartBody("B", document, "https://cb.example.com/x"),
                                     StandardCharsets.UTF_8);
    assertTrue(withCallback.contains("name=\"file\"; filename=\"a.pdf\""));
    assertTrue(withCallback.contains("Content-Type: application/pdf"));
    assertTrue(withCallback.contains("name=\"callback\""));
    assertTrue(withCallback.contains("{\"noAuthUrl\":\"https://cb.example.com/x\"}"));
    assertTrue(withCallback.endsWith("--B--\r\n"));
    assertFalse(new String(DocumentOperations.multipartBody("B", document, null), StandardCharsets.UTF_8)
        .contains("callback"));
  }

  @Test
  public void envelopeHasStableShape() {
    JsonObject envelope = DocumentOperations.envelope("e1", "SUBMITTED", false, ResponseMode.CALLBACK, "a1", "1.1.0",
                                                      "doc.pdf", "https://r/e1", "https://cb", null);
    for (String field : new String[] {"executionId", "status", "completed", "message", "responseMode", "action",
        "documentName", "resultUrl", "callbackUrl", "elapsedMs", "validation", "result"}) {
      assertTrue(field, envelope.has(field));
    }
    assertEquals("1.1.0", envelope.getAsJsonObject("action").get("version").getAsString());
    assertEquals("CALLBACK", envelope.get("responseMode").getAsString());
  }

  @Test
  public void humanReadableMessages() {
    assertTrue(DocumentOperations.messageFor("MANUAL_VALIDATION_REQUIRED").contains("human review"));
    assertTrue(DocumentOperations.messageFor("SUCCEEDED").contains("successfully"));
  }

  @Test
  public void executionIdFromIdOrResultUrl() throws SmartIdpException {
    String id = "c146a2cb-f271-414b-ba75-14f8e1aa85c0";
    assertEquals(id, IdpSupport.executionId(id));
    assertEquals(id, IdpSupport.executionId("https://idp-rt.us-east-1.anypoint.mulesoft.com/api/v1/organizations/"
        + "01f79c97-9a83-4194-b922-15b64fd7305e/actions/c333413d-6593-4423-b3d4-f8ff4bf88917/versions/1.2.0/executions/" + id));
  }

  private static void expectInvalid(String url) {
    try {
      DocumentOperations.callbackUrl(url, ResponseMode.CALLBACK);
      fail("expected INVALID_INPUT for " + url);
    } catch (SmartIdpException e) {
      assertEquals(SmartIdpError.INVALID_INPUT, e.getType());
    }
  }
}
