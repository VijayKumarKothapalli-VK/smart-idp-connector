/*
 * Smart IDP Connector
 * Copyright (c) 2026 Vijay Kumar Kothapalli
 * Licensed under the Apache License, Version 2.0
 */
package io.github.vijaykumarkothapalli.idp.internal.validation;

/**
 * Decoded document bytes plus the file name and type the connector settled on.
 */
public class PreparedDocument {

  private final byte[] bytes;
  private final String fileName;
  private final DocumentType type;
  private final boolean base64Decoded;
  private final String source;

  public PreparedDocument(byte[] bytes, String fileName, DocumentType type, boolean base64Decoded) {
    this(bytes, fileName, type, base64Decoded, null);
  }

  public PreparedDocument(byte[] bytes, String fileName, DocumentType type, boolean base64Decoded, String source) {
    this.bytes = bytes;
    this.fileName = fileName;
    this.type = type;
    this.base64Decoded = base64Decoded;
    this.source = source;
  }

  /** Where the document was found in the input, e.g. "field 'content' (Base64)". */
  public String getSource() {
    return source;
  }

  public byte[] getBytes() {
    return bytes;
  }

  public String getFileName() {
    return fileName;
  }

  public DocumentType getType() {
    return type;
  }

  public boolean isBase64Decoded() {
    return base64Decoded;
  }
}
