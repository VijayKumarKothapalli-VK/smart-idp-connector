/*
 * Smart IDP Connector
 * Copyright (c) 2026 Vijay Kumar Kothapalli
 * Licensed under the Apache License, Version 2.0
 */
package io.github.vijaykumarkothapalli.idp;

import io.github.vijaykumarkothapalli.idp.api.ContentEncoding;
import io.github.vijaykumarkothapalli.idp.api.DocumentValidationReport;
import io.github.vijaykumarkothapalli.idp.internal.error.SmartIdpError;
import io.github.vijaykumarkothapalli.idp.internal.error.SmartIdpException;
import io.github.vijaykumarkothapalli.idp.internal.validation.DocumentInspector;
import io.github.vijaykumarkothapalli.idp.internal.validation.DocumentType;
import io.github.vijaykumarkothapalli.idp.internal.validation.PreparedDocument;
import io.github.vijaykumarkothapalli.idp.internal.validation.ValidationRules;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class DocumentInspectorTest {

  @Test
  public void acceptsPdfWithinLimits() throws Exception {
    PreparedDocument document = DocumentInspector.prepare(pdf(3), "invoice.pdf", ContentEncoding.AUTO);
    DocumentValidationReport report = DocumentInspector.validate(document, new ValidationRules(10, 50, "PDF,PNG"));
    assertTrue(report.isValid());
    assertEquals(3, report.getPageCount());
    assertEquals("PDF", report.getDetectedFileType());
    assertFalse(report.isBase64Decoded());
  }

  @Test
  public void rejectsTooManyPagesWithTypedError() throws Exception {
    PreparedDocument document = DocumentInspector.prepare(pdf(6), "big.pdf", ContentEncoding.BINARY);
    DocumentValidationReport report = DocumentInspector.validate(document, new ValidationRules(10, 5, "PDF"));
    assertFalse(report.isValid());
    assertFalse(report.isPageCountValid());
    assertEquals(DocumentInspector.TOO_MANY_PAGES, report.getViolationCodes().get(0));
    assertRaises(report, SmartIdpError.TOO_MANY_PAGES);
  }

  @Test
  public void rejectsFileOverSizeLimit() throws Exception {
    PreparedDocument document = DocumentInspector.prepare(pdf(1), "small.pdf", ContentEncoding.AUTO);
    DocumentValidationReport report = DocumentInspector.validate(document, new ValidationRules(0.0001, 0, "*"));
    assertFalse(report.isFileSizeValid());
    assertRaises(report, SmartIdpError.FILE_TOO_LARGE);
  }

  @Test
  public void zeroMeansNoLimit() throws Exception {
    PreparedDocument document = DocumentInspector.prepare(pdf(80), "huge.pdf", ContentEncoding.AUTO);
    assertTrue(DocumentInspector.validate(document, new ValidationRules(0, 0, "")).isValid());
  }

  @Test
  public void detectsBase64AndDataUriAutomatically() throws Exception {
    String base64 = "data:application/pdf;base64," + Base64.getEncoder().encodeToString(pdf(2));
    PreparedDocument document = DocumentInspector.prepare(base64.getBytes(StandardCharsets.US_ASCII), null, ContentEncoding.AUTO);
    assertTrue(document.isBase64Decoded());
    assertEquals(DocumentType.PDF, document.getType());
    assertEquals("document.pdf", document.getFileName());
    assertEquals(2, DocumentInspector.validate(document, new ValidationRules(10, 50, "PDF")).getPageCount());
  }

  @Test
  public void addsMissingExtensionFromDetectedType() throws Exception {
    PreparedDocument document = DocumentInspector.prepare(pdf(1), "purchase-order", ContentEncoding.AUTO);
    assertEquals("purchase-order.pdf", document.getFileName());
  }

  @Test
  public void rejectsDisallowedType() throws Exception {
    byte[] png = new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0};
    PreparedDocument document = DocumentInspector.prepare(png, "scan.png", ContentEncoding.AUTO);
    DocumentValidationReport report = DocumentInspector.validate(document, new ValidationRules(10, 50, "PDF"));
    assertEquals(1, report.getPageCount());
    assertFalse(report.isFileTypeValid());
    assertRaises(report, SmartIdpError.UNSUPPORTED_FILE_TYPE);
  }

  @Test
  public void flagsPasswordProtectedPdf() throws Exception {
    DocumentValidationReport report = DocumentInspector.validate(
        DocumentInspector.prepare(encryptedPdf(), "secret.pdf", ContentEncoding.AUTO), new ValidationRules(10, 50, "PDF"));
    assertTrue(report.isEncrypted());
    assertEquals(-1, report.getPageCount());
    assertRaises(report, SmartIdpError.INVALID_DOCUMENT);
  }

  @Test
  public void emptyContentIsRejected() {
    try {
      DocumentInspector.prepare(new byte[0], "x.pdf", ContentEncoding.AUTO);
      fail("expected an error");
    } catch (SmartIdpException e) {
      assertEquals(SmartIdpError.INVALID_DOCUMENT, e.getType());
    }
  }

  private static void assertRaises(DocumentValidationReport report, SmartIdpError expected) {
    try {
      DocumentInspector.enforce(report);
      fail("expected " + expected);
    } catch (SmartIdpException e) {
      assertEquals(expected, e.getType());
    }
  }

  static byte[] pdf(int pages) throws IOException {
    try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      for (int i = 0; i < pages; i++) {
        document.addPage(new PDPage());
      }
      document.save(out);
      return out.toByteArray();
    }
  }

  private static byte[] encryptedPdf() throws IOException {
    try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      document.addPage(new PDPage());
      StandardProtectionPolicy policy = new StandardProtectionPolicy("owner", "user-secret", new AccessPermission());
      policy.setEncryptionKeyLength(128);
      document.protect(policy);
      document.save(out);
      return out.toByteArray();
    }
  }
}
