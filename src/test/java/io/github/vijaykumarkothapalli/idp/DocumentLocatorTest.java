/*
 * Smart IDP Connector
 * Copyright (c) 2026 Vijay Kumar Kothapalli
 * Licensed under the Apache License, Version 2.0
 */
package io.github.vijaykumarkothapalli.idp;

import io.github.vijaykumarkothapalli.idp.internal.error.SmartIdpError;
import io.github.vijaykumarkothapalli.idp.internal.error.SmartIdpException;
import io.github.vijaykumarkothapalli.idp.internal.validation.DocumentInspector;
import io.github.vijaykumarkothapalli.idp.internal.validation.DocumentLocator;
import io.github.vijaykumarkothapalli.idp.internal.validation.DocumentLocator.Located;
import io.github.vijaykumarkothapalli.idp.internal.validation.DocumentType;
import io.github.vijaykumarkothapalli.idp.internal.validation.PreparedDocument;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mule.runtime.api.metadata.DataType;
import org.mule.runtime.api.metadata.MediaType;
import org.mule.runtime.api.metadata.TypedValue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class DocumentLocatorTest {

  private static byte[] pdf;
  private static String base64;
  private static final byte[] PNG = new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13};

  @BeforeClass
  public static void createSample() throws Exception {
    pdf = DocumentInspectorTest.pdf(2);
    base64 = Base64.getEncoder().encodeToString(pdf);
  }

  // ----- the file itself -----------------------------------------------------------------------------------------

  @Test
  public void binaryStream() throws Exception {
    Located located = locate(new ByteArrayInputStream(pdf), "application/pdf");
    assertArrayEquals(pdf, located.getBytes());
    assertEquals("binary content", located.getSource());
  }

  @Test
  public void byteArrayAndBoxedBytes() throws Exception {
    assertArrayEquals(pdf, locate(pdf, null).getBytes());
    Byte[] boxed = new Byte[pdf.length];
    for (int i = 0; i < pdf.length; i++) {
      boxed[i] = pdf[i];
    }
    assertArrayEquals(pdf, locate(boxed, null).getBytes());
  }

  @Test
  public void base64TextWithLineBreaksAndDataUri() throws Exception {
    String wrapped = base64.replaceAll("(.{76})", "$1\r\n");
    Located plain = locate(wrapped, "text/plain");
    assertArrayEquals(pdf, plain.getBytes());
    assertTrue(plain.isBase64Decoded());
    Located dataUri = locate("data:application/pdf;base64," + base64, null);
    assertArrayEquals(pdf, dataUri.getBytes());
    assertEquals("data URI (Base64)", dataUri.getSource());
  }

  @Test
  public void typedValueWrappingStream() throws Exception {
    TypedValue<InputStream> value = new TypedValue<>(new ByteArrayInputStream(pdf),
                                                     DataType.builder().type(InputStream.class).mediaType(MediaType.parse("application/pdf")).build());
    assertArrayEquals(pdf, DocumentLocator.locate(value, null, null).getBytes());
  }

  // ----- JSON ----------------------------------------------------------------------------------------------------

  @Test
  public void jsonContentFieldWithFileName() throws Exception {
    Located located = locate("{\"fileName\":\"invoice-77.pdf\",\"content\":\"" + base64 + "\"}", "application/json");
    assertArrayEquals(pdf, located.getBytes());
    assertEquals("invoice-77.pdf", located.getFileName());
    assertEquals("field 'content' (Base64)", located.getSource());
  }

  @Test
  public void jsonWithUnusualFieldNamesAndNesting() throws Exception {
    String json = "{\"request\":{\"id\":42,\"meta\":{\"customer\":\"ACME\"},"
        + "\"attachment\":{\"originalFileName\":\"scan.pdf\",\"blobValue\":\"" + base64 + "\"}}}";
    Located located = locate(json, "application/json");
    assertArrayEquals(pdf, located.getBytes());
    assertEquals("scan.pdf", located.getFileName());
    assertEquals("field 'request.attachment.blobValue' (Base64)", located.getSource());
  }

  @Test
  public void jsonDataUriField() throws Exception {
    Located located = locate("{\"name\":\"a.pdf\",\"file\":\"data:application/pdf;base64," + base64 + "\"}", null);
    assertArrayEquals(pdf, located.getBytes());
    assertEquals("a.pdf", located.getFileName());
  }

  @Test
  public void jsonArrayPicksFirstAndReportsOthers() throws Exception {
    String json = "[{\"fileName\":\"one.pdf\",\"content\":\"" + base64 + "\"},"
        + "{\"fileName\":\"two.pdf\",\"content\":\"" + base64 + "\"}]";
    Located located = locate(json, "application/json");
    assertEquals("one.pdf", located.getFileName());
    assertTrue(located.getSource().contains("1 other document(s) ignored"));
  }

  @Test
  public void documentFieldChoosesAmongSeveral() throws Exception {
    String json = "{\"front\":\"" + Base64.getEncoder().encodeToString(PNG) + "\",\"invoice\":{\"fileName\":\"b.pdf\","
        + "\"data\":\"" + base64 + "\"}}";
    Located located = DocumentLocator.locate(json, "application/json", "invoice.data");
    assertArrayEquals(pdf, located.getBytes());
    assertEquals("b.pdf", located.getFileName());
    assertEquals(DocumentType.PNG,
                 DocumentType.fromContent(DocumentLocator.locate(json, "application/json", "payload.front").getBytes()));
  }

  @Test
  public void salesforceContentVersionTitleAndExtension() throws Exception {
    Map<String, Object> contentVersion = new LinkedHashMap<>();
    contentVersion.put("Id", "068xx0000001");
    contentVersion.put("Title", "PO-2026-001");
    contentVersion.put("FileExtension", "pdf");
    contentVersion.put("VersionData", pdf);
    Located located = locate(Collections.singletonList(contentVersion), "application/java");
    assertArrayEquals(pdf, located.getBytes());
    assertEquals("PO-2026-001.pdf", located.getFileName());
  }

  @Test
  public void emailAttachmentsMapKeyedByFileName() throws Exception {
    Map<String, Object> attachments = new LinkedHashMap<>();
    attachments.put("signature.txt", new TypedValue<>("Regards", DataType.STRING));
    attachments.put("invoice.pdf", new TypedValue<>(new ByteArrayInputStream(pdf), DataType.INPUT_STREAM));
    Map<String, Object> email = new LinkedHashMap<>();
    email.put("subject", "Invoice");
    email.put("attachments", attachments);
    Located located = locate(email, "application/java");
    assertArrayEquals(pdf, located.getBytes());
    assertEquals("invoice.pdf", located.getFileName());
  }

  @Test
  public void documentFieldWithListIndexAndExpressionSyntax() throws Exception {
    String json = "[{\"fileName\":\"first.pdf\",\"content\":\"" + base64 + "\"},"
        + "{\"fileName\":\"second.pdf\",\"content\":\"" + base64 + "\"}]";
    for (String hint : new String[] {"1.content", "[1].content", "payload[1].content", "#[payload[1].content]"}) {
      assertEquals(hint, "second.pdf", DocumentLocator.locate(json, "application/json", hint).getFileName());
    }
  }

  @Test
  public void databaseRowNameFieldIsUsed() throws Exception {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("DOC_ID", 7);
    row.put("DOC_NAME", "db-stored.pdf");
    row.put("DOC_BLOB", pdf);
    assertEquals("db-stored.pdf", locate(Collections.singletonList(row), "application/java").getFileName());
  }

  // ----- XML and multipart ---------------------------------------------------------------------------------------

  @Test
  public void xmlElement() throws Exception {
    String xml = "<?xml version=\"1.0\"?><submission><fileName>po.pdf</fileName><document>" + base64
        + "</document></submission>";
    Located located = locate(xml, "application/xml");
    assertArrayEquals(pdf, located.getBytes());
    assertEquals("po.pdf", located.getFileName());
  }

  @Test
  public void xmlWithDoctypeIsRefused() {
    String xml = "<?xml version=\"1.0\"?><!DOCTYPE x [<!ENTITY e SYSTEM \"file:///etc/passwd\">]><x>&e;</x>";
    expectError(xml, "application/xml", SmartIdpError.INVALID_DOCUMENT);
  }

  @Test
  public void multipartUpload() throws Exception {
    ByteArrayOutputStream body = new ByteArrayOutputStream();
    write(body, "--XyZ\r\nContent-Disposition: form-data; name=\"customer\"\r\n\r\nACME\r\n");
    write(body, "--XyZ\r\nContent-Disposition: form-data; name=\"upload\"; filename=\"Q3 invoice.pdf\"\r\n"
        + "Content-Type: application/pdf\r\n\r\n");
    body.write(pdf);
    write(body, "\r\n--XyZ--\r\n");
    Located located = locate(body.toByteArray(), "multipart/form-data; boundary=XyZ");
    assertArrayEquals(pdf, located.getBytes());
    assertEquals("Q3 invoice.pdf", located.getFileName());
    // the boundary is also detected from the body when the media type was lost
    assertArrayEquals(pdf, locate(body.toByteArray(), null).getBytes());
  }

  // ----- mistakes get helpful errors ------------------------------------------------------------------------------

  @Test
  public void jsonWithoutDocumentExplainsWhatWasFound() {
    SmartIdpException error = expectError("{\"orderId\":1,\"customer\":\"ACME\"}", "application/json",
                                          SmartIdpError.INVALID_DOCUMENT);
    assertTrue(error.getMessage().contains("[orderId, customer]"));
    assertTrue(error.getMessage().contains("Document Field"));
  }

  @Test
  public void previousProcessDocumentResponseIsRecognised() {
    SmartIdpException error = expectError("{\"executionId\":\"x\",\"status\":\"SUCCEEDED\",\"result\":{}}",
                                          "application/json", SmartIdpError.INVALID_DOCUMENT);
    assertTrue(error.getMessage().contains("earlier Process Document"));
  }

  @Test
  public void wrongDocumentFieldListsAvailableFields() {
    try {
      DocumentLocator.locate("{\"content\":\"" + base64 + "\"}", "application/json", "file");
      fail("expected error");
    } catch (SmartIdpException e) {
      assertEquals(SmartIdpError.INVALID_INPUT, e.getType());
      assertTrue(e.getMessage().contains("[content]"));
    }
  }

  @Test
  public void emptyAndNullAreRejected() {
    expectError(new byte[0], null, SmartIdpError.INVALID_DOCUMENT);
    expectError(null, null, SmartIdpError.INVALID_DOCUMENT);
  }

  @Test
  public void unsupportedFileStillReachesValidation() throws Exception {
    // A Word file is located (so validation can say "DOCX is not allowed") rather than reported as "not found".
    byte[] docx = new byte[] {0x50, 0x4B, 0x03, 0x04, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16};
    PreparedDocument prepared = DocumentInspector.prepare(
        DocumentLocator.locate("{\"fileName\":\"c.docx\",\"content\":\"" + Base64.getEncoder().encodeToString(docx)
            + "\"}", "application/json", null), null);
    assertEquals(DocumentType.DOCX, prepared.getType());
    assertEquals("c.docx", prepared.getFileName());
  }

  @Test
  public void explicitFileNameWinsAndMissingExtensionIsAdded() throws Exception {
    Located located = locate("{\"fileName\":\"from-json.pdf\",\"content\":\"" + base64 + "\"}", "application/json");
    assertEquals("override.pdf", DocumentInspector.prepare(located, "override.pdf").getFileName());
    assertEquals("no-extension.pdf", DocumentInspector.prepare(located, "no-extension").getFileName());
    assertFalse(DocumentInspector.prepare(locate(pdf, null), null).getFileName().isEmpty());
  }

  // ---------------------------------------------------------------------------------------------------------------

  private static Located locate(Object value, String mediaType) throws SmartIdpException {
    return DocumentLocator.locate(value, mediaType, null);
  }

  private static SmartIdpException expectError(Object value, String mediaType, SmartIdpError expected) {
    try {
      DocumentLocator.locate(value, mediaType, null);
      fail("expected " + expected);
      return null;
    } catch (SmartIdpException e) {
      assertEquals(expected, e.getType());
      return e;
    }
  }

  private static void write(ByteArrayOutputStream out, String text) {
    byte[] bytes = text.getBytes(StandardCharsets.ISO_8859_1);
    out.write(bytes, 0, bytes.length);
  }

  @SuppressWarnings("unused")
  private static List<Object> list(Object... items) {
    return Arrays.asList(items);
  }
}
