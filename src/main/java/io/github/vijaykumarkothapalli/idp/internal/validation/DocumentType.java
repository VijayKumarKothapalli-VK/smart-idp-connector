/*
 * Smart IDP Connector
 * Copyright (c) 2026 Vijay Kumar Kothapalli
 * Licensed under the Apache License, Version 2.0
 */
package io.github.vijaykumarkothapalli.idp.internal.validation;

import java.util.Locale;

/**
 * Document formats the inspector can recognise from their content (magic bytes) or file extension.
 */
public enum DocumentType {
  PDF("application/pdf", "pdf"),
  PNG("image/png", "png"),
  JPEG("image/jpeg", "jpg", "jpeg"),
  TIFF("image/tiff", "tif", "tiff"),
  GIF("image/gif", "gif"),
  BMP("image/bmp", "bmp"),
  WEBP("image/webp", "webp"),
  DOCX("application/vnd.openxmlformats-officedocument.wordprocessingml.document", "docx"),
  TEXT("text/plain", "txt"),
  UNKNOWN("application/octet-stream");

  private final String mimeType;
  private final String[] extensions;

  DocumentType(String mimeType, String... extensions) {
    this.mimeType = mimeType;
    this.extensions = extensions;
  }

  public String getMimeType() {
    return mimeType;
  }

  public String defaultExtension() {
    return extensions.length == 0 ? "bin" : extensions[0];
  }

  /** Recognises the type from the leading bytes of the content. */
  public static DocumentType fromContent(byte[] bytes) {
    if (bytes == null || bytes.length < 4) {
      return UNKNOWN;
    }
    if (startsWith(bytes, 0x25, 0x50, 0x44, 0x46)) {
      return PDF;
    }
    if (startsWith(bytes, 0x89, 0x50, 0x4E, 0x47)) {
      return PNG;
    }
    if (startsWith(bytes, 0xFF, 0xD8, 0xFF)) {
      return JPEG;
    }
    if (startsWith(bytes, 0x49, 0x49, 0x2A, 0x00) || startsWith(bytes, 0x4D, 0x4D, 0x00, 0x2A)) {
      return TIFF;
    }
    if (startsWith(bytes, 0x47, 0x49, 0x46, 0x38)) {
      return GIF;
    }
    if (startsWith(bytes, 0x42, 0x4D)) {
      return BMP;
    }
    if (bytes.length >= 12 && startsWith(bytes, 0x52, 0x49, 0x46, 0x46)
        && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P') {
      return WEBP;
    }
    if (startsWith(bytes, 0x50, 0x4B, 0x03, 0x04)) {
      return DOCX;
    }
    return UNKNOWN;
  }

  /** Recognises the type from a file name extension. */
  public static DocumentType fromFileName(String fileName) {
    if (fileName == null || fileName.lastIndexOf('.') < 0) {
      return UNKNOWN;
    }
    String extension = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    for (DocumentType type : values()) {
      for (String candidate : type.extensions) {
        if (candidate.equals(extension)) {
          return type;
        }
      }
    }
    return UNKNOWN;
  }

  /** Recognises the type from a media type such as application/pdf or image/jpg. */
  public static DocumentType fromMimeType(String mediaType) {
    if (mediaType == null) {
      return UNKNOWN;
    }
    String mime = mediaType.toLowerCase(Locale.ROOT).trim();
    if (mime.equals("image/jpg") || mime.equals("image/pjpeg")) {
      return JPEG;
    }
    if (mime.equals("image/tif")) {
      return TIFF;
    }
    for (DocumentType type : values()) {
      if (type != UNKNOWN && type.mimeType.equals(mime)) {
        return type;
      }
    }
    return UNKNOWN;
  }

  /** Parses a user supplied type name such as "pdf", "JPG" or "tif". */
  public static DocumentType parse(String name) {
    String value = name.trim().toUpperCase(Locale.ROOT);
    if (value.equals("JPG")) {
      return JPEG;
    }
    if (value.equals("TIF")) {
      return TIFF;
    }
    try {
      return valueOf(value);
    } catch (IllegalArgumentException e) {
      return fromFileName("x." + value.toLowerCase(Locale.ROOT));
    }
  }

  private static boolean startsWith(byte[] bytes, int... signature) {
    if (bytes.length < signature.length) {
      return false;
    }
    for (int i = 0; i < signature.length; i++) {
      if ((bytes[i] & 0xFF) != signature[i]) {
        return false;
      }
    }
    return true;
  }
}
