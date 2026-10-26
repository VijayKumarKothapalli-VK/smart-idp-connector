/*
 * Smart IDP Connector
 * Copyright (c) 2026 Vijay Kumar Kothapalli
 * Licensed under the Apache License, Version 2.0
 */
package io.github.vijaykumarkothapalli.idp.internal.validation;

import io.github.vijaykumarkothapalli.idp.api.ContentEncoding;
import io.github.vijaykumarkothapalli.idp.api.DocumentValidationReport;
import io.github.vijaykumarkothapalli.idp.internal.error.SmartIdpError;
import io.github.vijaykumarkothapalli.idp.internal.error.SmartIdpException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Iterator;
import java.util.regex.Pattern;

/**
 * Decodes, identifies and measures documents, then checks them against {@link ValidationRules}.
 */
public final class DocumentInspector {

  public static final String FILE_TOO_LARGE = "FILE_TOO_LARGE";
  public static final String TOO_MANY_PAGES = "TOO_MANY_PAGES";
  public static final String UNSUPPORTED_FILE_TYPE = "UNSUPPORTED_FILE_TYPE";
  public static final String INVALID_DOCUMENT = "INVALID_DOCUMENT";

  private static final Pattern BASE64_TEXT = Pattern.compile("^[A-Za-z0-9+/_-]+={0,2}$");
  private static final Pattern DATA_URI_PREFIX = Pattern.compile("^data:[^,]*;base64,", Pattern.CASE_INSENSITIVE);
  private static final double BYTES_PER_MB = 1024d * 1024d;

  private DocumentInspector() {}

  /**
   * Decodes Base64 when needed, detects the file type and makes sure the file name carries a matching extension.
   */
  public static PreparedDocument prepare(byte[] content, String fileName, ContentEncoding encoding)
      throws SmartIdpException {
    if (content == null || content.length == 0) {
      throw new SmartIdpException("The document is empty.", SmartIdpError.INVALID_DOCUMENT);
    }
    ContentEncoding mode = encoding == null ? ContentEncoding.AUTO : encoding;
    byte[] bytes = content;
    boolean decoded = false;

    if (mode == ContentEncoding.BASE64) {
      byte[] result = decodeBase64(content);
      if (result == null) {
        throw new SmartIdpException("The document content is not valid Base64.", SmartIdpError.INVALID_DOCUMENT);
      }
      bytes = result;
      decoded = true;
    } else if (mode == ContentEncoding.AUTO && DocumentType.fromContent(content) == DocumentType.UNKNOWN) {
      byte[] result = decodeBase64(content);
      if (result != null && DocumentType.fromContent(result) != DocumentType.UNKNOWN) {
        bytes = result;
        decoded = true;
      }
    }

    DocumentType type = DocumentType.fromContent(bytes);
    if (type == DocumentType.UNKNOWN) {
      type = DocumentType.fromFileName(fileName);
    }
    return new PreparedDocument(bytes, normaliseFileName(fileName, type), type, decoded);
  }

  /**
   * Builds the document from what {@link DocumentLocator} found. An explicit file name wins over a detected one.
   */
  public static PreparedDocument prepare(DocumentLocator.Located located, String explicitFileName)
      throws SmartIdpException {
    byte[] bytes = located.getBytes();
    if (bytes == null || bytes.length == 0) {
      throw new SmartIdpException("The document is empty.", SmartIdpError.INVALID_DOCUMENT);
    }
    String fileName = explicitFileName != null && !explicitFileName.trim().isEmpty() ? explicitFileName
        : located.getFileName();
    DocumentType type = DocumentType.fromContent(bytes);
    if (type == DocumentType.UNKNOWN) {
      type = DocumentType.fromFileName(fileName);
    }
    if (type == DocumentType.UNKNOWN) {
      type = DocumentType.fromMimeType(located.getMediaType());
    }
    return new PreparedDocument(bytes, normaliseFileName(fileName, type), type, located.isBase64Decoded(),
                                located.getSource());
  }

  /**
   * Measures the document and checks it against the rules. Never throws; see {@link #enforce(DocumentValidationReport)}.
   */
  public static DocumentValidationReport validate(PreparedDocument document, ValidationRules rules) {
    DocumentValidationReport report = new DocumentValidationReport();
    byte[] bytes = document.getBytes();
    DocumentType type = document.getType();

    report.setFileName(document.getFileName());
    report.setDetectedFileType(type.name());
    report.setMimeType(type.getMimeType());
    report.setBase64Decoded(document.isBase64Decoded());
    report.setContentSource(document.getSource());
    report.setFileSizeBytes(bytes.length);
    report.setFileSizeKb(round(bytes.length / 1024d));
    report.setFileSizeMb(round(bytes.length / BYTES_PER_MB));
    report.setMaxFileSizeMb(rules.getMaxFileSizeMb());
    report.setMaxPages(rules.getMaxPages());
    report.setAllowedFileTypes(new ArrayList<>(rules.getAllowedTypeNames()));

    boolean typeValid = rules.getAllowedTypes().isEmpty() || rules.getAllowedTypes().contains(type);
    report.setFileTypeValid(typeValid);
    if (!typeValid) {
      addViolation(report, UNSUPPORTED_FILE_TYPE, "File type " + type.name() + " is not allowed. Allowed types: "
          + String.join(", ", rules.getAllowedTypeNames()) + ".");
    }

    boolean sizeValid = rules.getMaxFileSizeMb() <= 0 || bytes.length <= rules.getMaxFileSizeMb() * BYTES_PER_MB;
    report.setFileSizeValid(sizeValid);
    if (!sizeValid) {
      addViolation(report, FILE_TOO_LARGE, "File size " + report.getFileSizeMb() + " MB exceeds the limit of "
          + rules.getMaxFileSizeMb() + " MB.");
    }

    int pages = countPages(bytes, type, report);
    report.setPageCount(pages);
    boolean pagesValid = rules.getMaxPages() <= 0 || pages < 0 || pages <= rules.getMaxPages();
    report.setPageCountValid(pagesValid);
    if (!pagesValid) {
      addViolation(report, TOO_MANY_PAGES, "Document has " + pages + " pages which exceeds the limit of "
          + rules.getMaxPages() + " pages.");
    }

    report.setValid(report.getViolations().isEmpty());
    return report;
  }

  /**
   * Raises the error type of the first violation, so flows can catch SMART-IDP:FILE_TOO_LARGE and friends.
   */
  public static void enforce(DocumentValidationReport report) throws SmartIdpException {
    if (report.isValid()) {
      return;
    }
    SmartIdpError error;
    switch (report.getViolationCodes().get(0)) {
      case FILE_TOO_LARGE:
        error = SmartIdpError.FILE_TOO_LARGE;
        break;
      case TOO_MANY_PAGES:
        error = SmartIdpError.TOO_MANY_PAGES;
        break;
      case UNSUPPORTED_FILE_TYPE:
        error = SmartIdpError.UNSUPPORTED_FILE_TYPE;
        break;
      default:
        error = SmartIdpError.INVALID_DOCUMENT;
    }
    throw new SmartIdpException("Document '" + report.getFileName() + "' failed validation: "
        + String.join(" ", report.getViolations()), error);
  }

  private static int countPages(byte[] bytes, DocumentType type, DocumentValidationReport report) {
    switch (type) {
      case PDF:
        try (PDDocument pdf = Loader.loadPDF(bytes)) {
          report.setEncrypted(pdf.isEncrypted());
          return pdf.getNumberOfPages();
        } catch (InvalidPasswordException e) {
          report.setEncrypted(true);
          addViolation(report, INVALID_DOCUMENT, "The PDF is password protected and cannot be processed.");
          return -1;
        } catch (IOException e) {
          addViolation(report, INVALID_DOCUMENT, "The PDF could not be read: " + e.getMessage());
          return -1;
        }
      case TIFF:
        return countImageFrames(bytes);
      case PNG:
      case JPEG:
      case GIF:
      case BMP:
      case WEBP:
        return 1;
      default:
        return -1;
    }
  }

  private static int countImageFrames(byte[] bytes) {
    try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
      Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
      if (!readers.hasNext()) {
        return 1;
      }
      ImageReader reader = readers.next();
      try {
        reader.setInput(input);
        return reader.getNumImages(true);
      } finally {
        reader.dispose();
      }
    } catch (IOException | RuntimeException e) {
      return 1;
    }
  }

  static byte[] decodeBase64(byte[] content) {
    String text = new String(content, StandardCharsets.US_ASCII).trim();
    text = DATA_URI_PREFIX.matcher(text).replaceFirst("");
    text = text.replaceAll("\\s+", "");
    if (text.length() < 8 || !BASE64_TEXT.matcher(text).matches()) {
      return null;
    }
    try {
      if (text.indexOf('-') >= 0 || text.indexOf('_') >= 0) {
        return Base64.getUrlDecoder().decode(text);
      }
      return Base64.getDecoder().decode(text);
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  static String normaliseFileName(String fileName, DocumentType type) {
    String name = fileName == null ? "" : fileName.trim();
    if (name.isEmpty()) {
      return "document." + type.defaultExtension();
    }
    if (type != DocumentType.UNKNOWN && DocumentType.fromFileName(name) == DocumentType.UNKNOWN) {
      return name + "." + type.defaultExtension();
    }
    return name;
  }

  private static void addViolation(DocumentValidationReport report, String code, String message) {
    report.getViolationCodes().add(code);
    report.getViolations().add(message);
  }

  private static double round(double value) {
    return Math.round(value * 100d) / 100d;
  }
}
