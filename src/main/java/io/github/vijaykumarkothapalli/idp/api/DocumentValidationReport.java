/*
 * Smart IDP Connector
 * Copyright (c) 2026 Vijay Kumar Kothapalli
 * Licensed under the Apache License, Version 2.0
 */
package io.github.vijaykumarkothapalli.idp.api;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Result of inspecting a document against the configured validation rules before it is sent to IDP.
 */
public class DocumentValidationReport implements Serializable {

  private static final long serialVersionUID = 1L;

  private String fileName;
  private String detectedFileType;
  private String mimeType;
  private boolean base64Decoded;
  private String contentSource;
  private long fileSizeBytes;
  private double fileSizeKb;
  private double fileSizeMb;
  private int pageCount;
  private boolean encrypted;

  private double maxFileSizeMb;
  private int maxPages;
  private List<String> allowedFileTypes = new ArrayList<>();

  private boolean fileSizeValid;
  private boolean pageCountValid;
  private boolean fileTypeValid;
  private boolean valid;
  private List<String> violations = new ArrayList<>();
  private List<String> violationCodes = new ArrayList<>();

  /** Machine readable codes matching the error types: FILE_TOO_LARGE, TOO_MANY_PAGES, UNSUPPORTED_FILE_TYPE, INVALID_DOCUMENT. */
  public List<String> getViolationCodes() {
    return violationCodes;
  }

  public void setViolationCodes(List<String> violationCodes) {
    this.violationCodes = violationCodes;
  }

  public String getFileName() {
    return fileName;
  }

  public void setFileName(String fileName) {
    this.fileName = fileName;
  }

  public String getDetectedFileType() {
    return detectedFileType;
  }

  public void setDetectedFileType(String detectedFileType) {
    this.detectedFileType = detectedFileType;
  }

  public String getMimeType() {
    return mimeType;
  }

  public void setMimeType(String mimeType) {
    this.mimeType = mimeType;
  }

  /** Where the document was found in the input, e.g. "binary content" or "field 'content' (Base64)". */
  public String getContentSource() {
    return contentSource;
  }

  public void setContentSource(String contentSource) {
    this.contentSource = contentSource;
  }

  public boolean isBase64Decoded() {
    return base64Decoded;
  }

  public void setBase64Decoded(boolean base64Decoded) {
    this.base64Decoded = base64Decoded;
  }

  public long getFileSizeBytes() {
    return fileSizeBytes;
  }

  public void setFileSizeBytes(long fileSizeBytes) {
    this.fileSizeBytes = fileSizeBytes;
  }

  public double getFileSizeKb() {
    return fileSizeKb;
  }

  public void setFileSizeKb(double fileSizeKb) {
    this.fileSizeKb = fileSizeKb;
  }

  public double getFileSizeMb() {
    return fileSizeMb;
  }

  public void setFileSizeMb(double fileSizeMb) {
    this.fileSizeMb = fileSizeMb;
  }

  /** Number of pages, or -1 when it could not be determined (for example a password protected PDF). */
  public int getPageCount() {
    return pageCount;
  }

  public void setPageCount(int pageCount) {
    this.pageCount = pageCount;
  }

  public boolean isEncrypted() {
    return encrypted;
  }

  public void setEncrypted(boolean encrypted) {
    this.encrypted = encrypted;
  }

  public double getMaxFileSizeMb() {
    return maxFileSizeMb;
  }

  public void setMaxFileSizeMb(double maxFileSizeMb) {
    this.maxFileSizeMb = maxFileSizeMb;
  }

  public int getMaxPages() {
    return maxPages;
  }

  public void setMaxPages(int maxPages) {
    this.maxPages = maxPages;
  }

  public List<String> getAllowedFileTypes() {
    return allowedFileTypes;
  }

  public void setAllowedFileTypes(List<String> allowedFileTypes) {
    this.allowedFileTypes = allowedFileTypes;
  }

  public boolean isFileSizeValid() {
    return fileSizeValid;
  }

  public void setFileSizeValid(boolean fileSizeValid) {
    this.fileSizeValid = fileSizeValid;
  }

  public boolean isPageCountValid() {
    return pageCountValid;
  }

  public void setPageCountValid(boolean pageCountValid) {
    this.pageCountValid = pageCountValid;
  }

  public boolean isFileTypeValid() {
    return fileTypeValid;
  }

  public void setFileTypeValid(boolean fileTypeValid) {
    this.fileTypeValid = fileTypeValid;
  }

  public boolean isValid() {
    return valid;
  }

  public void setValid(boolean valid) {
    this.valid = valid;
  }

  public List<String> getViolations() {
    return violations;
  }

  public void setViolations(List<String> violations) {
    this.violations = violations;
  }
}
