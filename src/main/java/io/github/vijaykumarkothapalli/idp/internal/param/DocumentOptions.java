/*
 * Smart IDP Connector
 * Copyright (c) 2026 Vijay Kumar Kothapalli
 * Licensed under the Apache License, Version 2.0
 */
package io.github.vijaykumarkothapalli.idp.internal.param;

import io.github.vijaykumarkothapalli.idp.api.ContentEncoding;
import org.mule.runtime.extension.api.annotation.param.Optional;
import org.mule.runtime.extension.api.annotation.param.Parameter;
import org.mule.runtime.extension.api.annotation.param.display.DisplayName;
import org.mule.runtime.extension.api.annotation.param.display.Example;
import org.mule.runtime.extension.api.annotation.param.display.Summary;

/**
 * How the connector reads the document from the Document value.
 */
public class DocumentOptions {

  @Parameter
  @Optional
  @DisplayName("File Name")
  @Summary("Name sent to IDP. When empty it is taken from the input (fileName / name fields, multipart filename, "
      + "attachment name) or generated from the detected file type.")
  @Example("invoice-001.pdf")
  private String fileName;

  @Parameter
  @Optional
  @DisplayName("Document Field")
  @Summary("Only needed when the input holds several files: the field that contains the one to send, e.g. 'content', "
      + "'data.file' or 'attachments.invoice.pdf'. Leave empty and the connector finds the document itself.")
  @Example("content")
  private String documentField;

  @Parameter
  @Optional(defaultValue = "AUTO")
  @DisplayName("Content Encoding")
  @Summary("AUTO finds the document in any input (binary, Base64, JSON, XML, multipart, maps). BINARY / BASE64 send "
      + "the input as-is without any detection.")
  private ContentEncoding contentEncoding;

  public String getFileName() {
    return fileName;
  }

  public String getDocumentField() {
    return documentField;
  }

  public ContentEncoding getContentEncoding() {
    return contentEncoding;
  }
}
