/*
 * Smart IDP Connector
 * Copyright (c) 2026 Vijay Kumar Kothapalli
 * Licensed under the Apache License, Version 2.0
 */
package io.github.vijaykumarkothapalli.idp.internal.operation;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.vijaykumarkothapalli.idp.api.ContentEncoding;
import io.github.vijaykumarkothapalli.idp.api.DocumentValidationReport;
import io.github.vijaykumarkothapalli.idp.api.ResponseMode;
import io.github.vijaykumarkothapalli.idp.api.TimeoutBehavior;
import io.github.vijaykumarkothapalli.idp.internal.config.SmartIdpConfiguration;
import io.github.vijaykumarkothapalli.idp.internal.connection.SmartIdpConnection;
import io.github.vijaykumarkothapalli.idp.internal.error.SmartIdpError;
import io.github.vijaykumarkothapalli.idp.internal.error.SmartIdpErrorProvider;
import io.github.vijaykumarkothapalli.idp.internal.error.SmartIdpException;
import io.github.vijaykumarkothapalli.idp.internal.param.DocumentOptions;
import io.github.vijaykumarkothapalli.idp.internal.param.ResponseOptions;
import io.github.vijaykumarkothapalli.idp.internal.param.ValidationOverrides;
import io.github.vijaykumarkothapalli.idp.internal.resolver.IdpTarget;
import io.github.vijaykumarkothapalli.idp.internal.resolver.IdpUrlResolver;
import io.github.vijaykumarkothapalli.idp.internal.util.Json;
import io.github.vijaykumarkothapalli.idp.internal.validation.DocumentInspector;
import io.github.vijaykumarkothapalli.idp.internal.validation.DocumentLocator;
import io.github.vijaykumarkothapalli.idp.internal.validation.PreparedDocument;
import io.github.vijaykumarkothapalli.idp.internal.validation.ValidationRules;
import io.github.vijaykumarkothapalli.idp.internal.valueprovider.ActionValueProvider;
import io.github.vijaykumarkothapalli.idp.internal.valueprovider.VersionValueProvider;
import org.mule.runtime.api.metadata.TypedValue;
import org.mule.runtime.extension.api.annotation.error.Throws;
import org.mule.runtime.extension.api.annotation.param.Config;
import org.mule.runtime.extension.api.annotation.param.Connection;
import org.mule.runtime.extension.api.annotation.param.Content;
import org.mule.runtime.extension.api.annotation.param.MediaType;
import org.mule.runtime.extension.api.annotation.param.Optional;
import org.mule.runtime.extension.api.annotation.param.ParameterGroup;
import org.mule.runtime.extension.api.annotation.param.display.DisplayName;
import org.mule.runtime.extension.api.annotation.param.display.Placement;
import org.mule.runtime.extension.api.annotation.param.display.Summary;
import org.mule.runtime.extension.api.annotation.values.OfValues;
import org.mule.runtime.extension.api.runtime.operation.Result;
import org.mule.runtime.extension.api.runtime.parameter.CorrelationInfo;
import org.mule.runtime.http.api.domain.entity.ByteArrayHttpEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static io.github.vijaykumarkothapalli.idp.internal.connection.SmartIdpConnection.urlEncode;
import static org.mule.runtime.extension.api.annotation.param.MediaType.APPLICATION_JSON;

/**
 * Smart IDP operations: process a document end to end, collect a result later, or check a document offline.
 */
public class DocumentOperations {

  private static final Logger LOGGER = LoggerFactory.getLogger(DocumentOperations.class);

  private static final int MAX_CONSECUTIVE_POLL_ERRORS = 3;
  private static final EnumSet<SmartIdpError> TRANSIENT_ERRORS =
      EnumSet.of(SmartIdpError.CONNECTIVITY, SmartIdpError.SERVER_ERROR, SmartIdpError.RATE_LIMITED);

  /**
   * Validates a document, submits it to an IDP document action, and returns the extracted result, or hands it to a
   * callback URL, depending on the response mode.
   */
  @DisplayName("Process Document")
  @Summary("Validates the document, sends it to an IDP document action, and either waits for the extracted result, "
      + "lets IDP call your callback URL, or both.")
  @MediaType(value = APPLICATION_JSON, strict = false)
  @Throws(SmartIdpErrorProvider.class)
  public Result<InputStream, Void> processDocument(@Config SmartIdpConfiguration config,
                                                   @Connection SmartIdpConnection connection,
                                                   @Optional @OfValues(ActionValueProvider.class)
                                                   @DisplayName("Document Action")
                                                   @Summary("Pick a published action, or give an action id / execution URL. "
                                                       + "Empty uses the configuration default or the connection URL.")
                                                   @Placement(order = 1) String action,
                                                   @Optional @OfValues(VersionValueProvider.class)
                                                   @DisplayName("Version")
                                                   @Summary("Lists the versions of the selected Document Action. Empty or "
                                                       + "'latest' runs the newest published version.")
                                                   @Placement(order = 2) String version,
                                                   @Optional(defaultValue = "#[payload]") @Content
                                                   @DisplayName("Document")
                                                   @Summary("The file to process, in any form: binary, Base64, or a JSON / XML / multipart / map value that contains it.")
                                                   TypedValue<Object> document,
                                                   @ParameterGroup(name = "Document Details") DocumentOptions documentOptions,
                                                   @ParameterGroup(name = "Response") ResponseOptions response,
                                                   @ParameterGroup(name = "Document Limits (optional overrides)")
                                                   ValidationOverrides limits,
                                                   CorrelationInfo correlationInfo)
      throws SmartIdpException {
    long started = System.currentTimeMillis();
    String tag = tag(correlationInfo);
    ResponseMode mode = response.getResponseMode();

    // 1. Read and validate the document before anything is sent.
    PreparedDocument prepared = prepare(document, documentOptions);
    DocumentValidationReport report = null;
    if (config.getValidation().isValidateBeforeSubmit()) {
      report = DocumentInspector.validate(prepared, ValidationRules.of(config.getValidation(), limits));
      if (!report.isValid()) {
        LOGGER.warn("{} document '{}' rejected before submission: {}", tag, report.getFileName(), report.getViolations());
      }
      DocumentInspector.enforce(report);
    }

    // 2. Resolve the callback, action and version.
    String callbackUrl = null;
    if (mode != ResponseMode.WAIT_FOR_RESULT) {
      callbackUrl = callbackUrl(IdpSupport.firstNonBlank(response.getCallbackUrl(),
                                                         config.getProcessing().getDefaultCallbackUrl()), mode);
    }
    IdpSupport.ActionRef actionRef = IdpSupport.action(connection, config, action);
    String resolvedVersion = IdpSupport.version(connection, actionRef, version);

    // 3. Submit.
    String executionsUrl = connection.runtimeUrl("/actions/" + urlEncode(actionRef.getActionId()) + "/versions/"
        + urlEncode(resolvedVersion) + "/executions");
    LOGGER.info("{} submitting '{}' ({} KB, {} pages, {}, from {}) to action {} v{} [mode={}{}]", tag,
                prepared.getFileName(), Math.round(prepared.getBytes().length / 1024d),
                report == null ? "?" : report.getPageCount(), prepared.getType(), prepared.getSource(),
                actionRef.getActionId(), resolvedVersion, mode,
                callbackUrl == null ? "" : ", callback=" + hostOf(callbackUrl));
    JsonElement submitted = submit(connection, executionsUrl, prepared, callbackUrl, response.getSalesforceOrgId());
    String executionId = Json.firstString(submitted, "id", "executionId");
    if (executionId == null) {
      throw new SmartIdpException("IDP accepted the document but returned no execution id: " + submitted,
                                  SmartIdpError.SERVER_ERROR);
    }
    String resultUrl = executionsUrl + "/" + executionId;
    LOGGER.info("{} IDP accepted '{}' as execution {}", tag, prepared.getFileName(), executionId);

    JsonObject output = envelope(executionId, "SUBMITTED", false, mode, actionRef.getActionId(), resolvedVersion,
                                 prepared.getFileName(), resultUrl, callbackUrl, report);

    // 4. CALLBACK mode returns straight away.
    if (mode == ResponseMode.CALLBACK) {
      output.addProperty("message", "Submitted. IDP will call the callback URL when processing finishes.");
      return finish(output, started);
    }

    // 5. Wait for the result.
    int maxWait = response.getMaxWaitSeconds() != null ? response.getMaxWaitSeconds()
        : config.getProcessing().getMaxWaitSeconds();
    TimeoutBehavior onTimeout = response.getTimeoutBehavior() != null ? response.getTimeoutBehavior()
        : config.getProcessing().getTimeoutBehavior();
    JsonElement result = waitForResult(connection, tag, resultUrl, executionId, response.isIncludeConfidenceDetails(),
                                       maxWait, config.getProcessing().getInitialPollSeconds());
    return complete(output, result, executionId, mode, maxWait, onTimeout, response.isRaiseErrorOnFailure(), tag, started);
  }

  /**
   * Returns the status and result of an execution; optionally waits for it to finish.
   */
  @DisplayName("Get Execution Result")
  @Summary("Returns the status and extracted data of an execution, e.g. after a callback or a pending response. "
      + "Set Max Wait to wait for it to finish.")
  @MediaType(value = APPLICATION_JSON, strict = false)
  @Throws(SmartIdpErrorProvider.class)
  public Result<InputStream, Void> getExecutionResult(@Config SmartIdpConfiguration config,
                                                      @Connection SmartIdpConnection connection,
                                                      @DisplayName("Execution")
                                                      @Summary("Execution id, or the resultUrl returned by Process Document.")
                                                      @Placement(order = 1) String execution,
                                                      @Optional @OfValues(ActionValueProvider.class)
                                                      @DisplayName("Document Action")
                                                      @Summary("Not needed when Execution is a result URL.")
                                                      @Placement(order = 2) String action,
                                                      @Optional @OfValues(VersionValueProvider.class) @DisplayName("Version")
                                                      @Summary("Not needed when Execution is a result URL. Must be the "
                                                          + "version the document was processed with.")
                                                      @Placement(order = 3) String version,
                                                      @Optional(defaultValue = "0") @DisplayName("Max Wait (seconds)")
                                                      @Summary("0 returns the current status immediately.")
                                                      @Placement(order = 4) int maxWaitSeconds,
                                                      @Optional(defaultValue = "false")
                                                      @DisplayName("Include Confidence And Geometry")
                                                      @Placement(order = 5) boolean includeConfidenceDetails,
                                                      @Optional(defaultValue = "true")
                                                      @DisplayName("Raise Error When Execution Fails")
                                                      @Placement(order = 6) boolean raiseErrorOnFailure,
                                                      CorrelationInfo correlationInfo)
      throws SmartIdpException {
    long started = System.currentTimeMillis();
    String tag = tag(correlationInfo);
    String executionId = IdpSupport.executionId(execution);
    IdpTarget fromUrl = IdpUrlResolver.isUuid(execution) ? null : IdpUrlResolver.resolve(execution);
    IdpSupport.ActionRef actionRef = IdpSupport.action(connection, config,
                                                       IdpSupport.firstNonBlank(action, fromUrl == null ? null : fromUrl.getActionId()));
    String resolvedVersion = IdpSupport.firstNonBlank(version, fromUrl == null ? null : fromUrl.getVersion());
    if (resolvedVersion == null || resolvedVersion.equalsIgnoreCase(IdpSupport.LATEST)) {
      resolvedVersion = IdpSupport.version(connection, actionRef, null);
      LOGGER.info("{} no version given for execution {}; using {}. Pass the resultUrl to be exact.", tag, executionId,
                  resolvedVersion);
    }
    String resultUrl = connection.runtimeUrl("/actions/" + urlEncode(actionRef.getActionId()) + "/versions/"
        + urlEncode(resolvedVersion) + "/executions/" + executionId);
    JsonObject output = envelope(executionId, "UNKNOWN", false, null, actionRef.getActionId(), resolvedVersion, null,
                                 resultUrl, null, null);
    JsonElement result = waitForResult(connection, tag, resultUrl, executionId, includeConfidenceDetails,
                                       Math.max(0, maxWaitSeconds), config.getProcessing().getInitialPollSeconds());
    return complete(output, result, executionId, null, maxWaitSeconds, TimeoutBehavior.RETURN_PENDING,
                    raiseErrorOnFailure, tag, started);
  }

  /**
   * Checks a document against the limits without sending it anywhere.
   */
  @DisplayName("Validate Document")
  @Summary("Checks file type, size, page count and readability against the IDP limits. Nothing is sent to IDP.")
  @Throws(SmartIdpErrorProvider.class)
  public DocumentValidationReport validateDocument(@Config SmartIdpConfiguration config,
                                                   @Optional(defaultValue = "#[payload]") @Content
                                                   @DisplayName("Document")
                                                   @Summary("The file to process, in any form: binary, Base64, or a JSON / XML / multipart / map value that contains it.")
                                                   TypedValue<Object> document,
                                                   @ParameterGroup(name = "Document Details") DocumentOptions documentOptions,
                                                   @ParameterGroup(name = "Document Limits (optional overrides)")
                                                   ValidationOverrides limits,
                                                   @Optional(defaultValue = "false") @DisplayName("Raise Error When Invalid")
                                                   @Summary("Raise SMART-IDP:FILE_TOO_LARGE, TOO_MANY_PAGES, "
                                                       + "UNSUPPORTED_FILE_TYPE or INVALID_DOCUMENT instead of returning the report.")
                                                   boolean raiseErrorWhenInvalid,
                                                   CorrelationInfo correlationInfo)
      throws SmartIdpException {
    PreparedDocument prepared = prepare(document, documentOptions);
    DocumentValidationReport report = DocumentInspector.validate(prepared, ValidationRules.of(config.getValidation(), limits));
    LOGGER.info("{} validated '{}' (from {}): valid={}, {} KB, {} pages, type {}{}", tag(correlationInfo),
                report.getFileName(), report.getContentSource(), report.isValid(), Math.round(report.getFileSizeKb()),
                report.getPageCount(), report.getDetectedFileType(),
                report.isValid() ? "" : ", violations " + report.getViolations());
    if (raiseErrorWhenInvalid) {
      DocumentInspector.enforce(report);
    }
    return report;
  }

  // ---------------------------------------------------------------------------------------------------------------

  private static JsonElement submit(SmartIdpConnection connection, String url, PreparedDocument prepared,
                                    String callbackUrl, String salesforceOrgId)
      throws SmartIdpException {
    String boundary = "----SmartIdp" + UUID.randomUUID().toString().replace("-", "");
    byte[] body = multipartBody(boundary, prepared, callbackUrl);
    Map<String, String> headers = new LinkedHashMap<>();
    headers.put("Accept", "application/json");
    headers.put("Content-Type", "multipart/form-data; boundary=" + boundary);
    if (!IdpSupport.isBlank(salesforceOrgId)) {
      headers.put("x-sfdc-core-tenant-id", salesforceOrgId.trim());
    }
    return connection.sendOrFail("POST", url, null, headers, () -> new ByteArrayHttpEntity(body)).json();
  }

  /**
   * Polls until IDP reports a final status or the wait time is used up. Returns the last result seen (which may still
   * be in progress). Transient errors are retried a few times instead of failing the whole call.
   */
  private static JsonElement waitForResult(SmartIdpConnection connection, String tag, String resultUrl,
                                           String executionId, boolean includeDetails, int maxWaitSeconds,
                                           int initialPollSeconds)
      throws SmartIdpException {
    Map<String, String> query = new LinkedHashMap<>();
    query.put("valueOnly", String.valueOf(!includeDetails));
    long deadline = System.currentTimeMillis() + maxWaitSeconds * 1000L;
    long delay = Math.max(1, initialPollSeconds) * 1000L;
    String lastStatus = null;
    int consecutiveErrors = 0;
    int checks = 0;

    if (maxWaitSeconds > 0) {
      sleep(Math.min(delay, Math.max(0, deadline - System.currentTimeMillis())));
    }
    while (true) {
      JsonElement result;
      try {
        checks++;
        result = connection.getJson(resultUrl, query).json();
        consecutiveErrors = 0;
      } catch (SmartIdpException e) {
        boolean transientError = e.getType() instanceof SmartIdpError && TRANSIENT_ERRORS.contains(e.getType());
        if (!transientError || ++consecutiveErrors > MAX_CONSECUTIVE_POLL_ERRORS
            || System.currentTimeMillis() >= deadline) {
          throw e;
        }
        LOGGER.warn("{} status check {} for execution {} failed ({}), retrying", tag, checks, executionId, e.getType());
        result = null;
      }

      if (result != null) {
        String status = ExecutionStatus.normalise(Json.firstString(result, "status"));
        if (!status.equals(lastStatus)) {
          LOGGER.info("{} execution {} status: {}", tag, executionId, status);
          lastStatus = status;
        }
        if (!ExecutionStatus.isInProgress(status)) {
          return result;
        }
      }
      long remaining = deadline - System.currentTimeMillis();
      if (remaining <= 0) {
        LOGGER.debug("{} stopped waiting for execution {} after {} status checks", tag, executionId, checks);
        return result;
      }
      delay = ExecutionStatus.nextDelayMillis(delay, remaining);
      sleep(delay);
    }
  }

  private static Result<InputStream, Void> complete(JsonObject output, JsonElement result, String executionId,
                                                    ResponseMode mode, int maxWait, TimeoutBehavior onTimeout,
                                                    boolean raiseErrorOnFailure, String tag, long started)
      throws SmartIdpException {
    String status = ExecutionStatus.normalise(result == null ? null : Json.firstString(result, "status"));
    boolean completed = result != null && !ExecutionStatus.isInProgress(status);
    output.addProperty("status", status);
    output.addProperty("completed", completed);
    if (result != null && result.isJsonObject() && output.get("documentName").isJsonNull()) {
      output.addProperty("documentName", Json.firstString(result, "documentName"));
    }
    output.add("result", result);
    long elapsed = System.currentTimeMillis() - started;

    if (!completed) {
      String message = mode == ResponseMode.WAIT_WITH_CALLBACK_FALLBACK
          ? "Still processing after " + maxWait + "s. IDP will call the callback URL when it finishes."
          : "Still processing after " + maxWait + "s. Collect it later with Get Execution Result using resultUrl.";
      output.addProperty("message", message);
      if (onTimeout == TimeoutBehavior.RAISE_ERROR) {
        LOGGER.warn("{} execution {} not finished after {}s (status {}), raising TIMEOUT", tag, executionId, maxWait, status);
        throw new SmartIdpException("IDP execution " + executionId + " was still " + status + " after " + maxWait
            + " seconds. " + Json.toPrettyString(output), SmartIdpError.TIMEOUT);
      }
      LOGGER.info("{} execution {} still {} after {}s; returning pending response", tag, executionId, status, maxWait);
      return finish(output, started);
    }

    if (ExecutionStatus.isFailed(status)) {
      LOGGER.error("{} execution {} finished with status {} after {} ms", tag, executionId, status, elapsed);
      if (raiseErrorOnFailure) {
        throw new SmartIdpException("IDP execution " + executionId + " failed: " + Json.toPrettyString(output),
                                    SmartIdpError.EXECUTION_FAILED);
      }
    } else {
      LOGGER.info("{} execution {} finished with status {} in {} ms", tag, executionId, status, elapsed);
    }
    output.addProperty("message", messageFor(status));
    return finish(output, started);
  }

  static String messageFor(String status) {
    switch (status) {
      case "SUCCEEDED":
        return "Document processed successfully.";
      case "PARTIAL_SUCCESS":
        return "Document processed; some values could not be extracted.";
      case "MANUAL_VALIDATION_REQUIRED":
        return "Document processed but needs human review in IDP before the final values are available.";
      case "FAILED":
        return "IDP could not process the document.";
      default:
        return "IDP returned status " + status + ".";
    }
  }

  static JsonObject envelope(String executionId, String status, boolean completed, ResponseMode mode, String actionId,
                             String version, String documentName, String resultUrl, String callbackUrl,
                             DocumentValidationReport report) {
    JsonObject output = new JsonObject();
    output.addProperty("executionId", executionId);
    output.addProperty("status", status);
    output.addProperty("completed", completed);
    output.addProperty("message", (String) null);
    output.addProperty("responseMode", mode == null ? null : mode.name());
    JsonObject actionJson = new JsonObject();
    actionJson.addProperty("id", actionId);
    actionJson.addProperty("version", version);
    output.add("action", actionJson);
    output.addProperty("documentName", documentName);
    output.addProperty("resultUrl", resultUrl);
    output.addProperty("callbackUrl", callbackUrl);
    output.addProperty("elapsedMs", 0);
    output.add("validation", report == null ? null : Json.toTree(report));
    output.add("result", null);
    return output;
  }

  private static Result<InputStream, Void> finish(JsonObject output, long started) {
    output.addProperty("elapsedMs", System.currentTimeMillis() - started);
    return Json.result(output);
  }

  /** IDP only calls HTTPS callback URLs. */
  static String callbackUrl(String url, ResponseMode mode) throws SmartIdpException {
    if (IdpSupport.isBlank(url)) {
      throw new SmartIdpException("Response mode " + mode + " needs a Callback URL (on the operation or as "
          + "'Default Callback URL' on the configuration).", SmartIdpError.INVALID_INPUT);
    }
    String value = url.trim();
    try {
      URI uri = URI.create(value);
      if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
        throw new SmartIdpException("Callback URL must be an absolute HTTPS URL (IDP only calls HTTPS): " + value,
                                    SmartIdpError.INVALID_INPUT);
      }
    } catch (IllegalArgumentException e) {
      throw new SmartIdpException("Callback URL is not a valid URL: " + value, SmartIdpError.INVALID_INPUT, e);
    }
    return value;
  }

  static PreparedDocument prepare(TypedValue<Object> content, DocumentOptions options) throws SmartIdpException {
    ContentEncoding encoding = options.getContentEncoding() == null ? ContentEncoding.AUTO : options.getContentEncoding();
    if (encoding != ContentEncoding.AUTO) {
      Object raw = DocumentLocator.unwrap(content);
      if (raw instanceof String) {
        raw = ((String) raw).getBytes(StandardCharsets.UTF_8);
      }
      if (!(raw instanceof byte[])) {
        throw new SmartIdpException("Content Encoding " + encoding + " needs binary or text content, but the Document is "
            + (raw == null ? "empty" : "a " + raw.getClass().getSimpleName()) + ". Use AUTO to find the file inside it.",
                                    SmartIdpError.INVALID_DOCUMENT);
      }
      return DocumentInspector.prepare((byte[]) raw, options.getFileName(), encoding);
    }
    DocumentLocator.Located located = DocumentLocator.locate(content, null, options.getDocumentField());
    return DocumentInspector.prepare(located, options.getFileName());
  }

  static byte[] multipartBody(String boundary, PreparedDocument document, String callbackUrl) {
    ByteArrayOutputStream out = new ByteArrayOutputStream(document.getBytes().length + 1024);
    String fileName = document.getFileName().replace("\"", "'");
    write(out, "--" + boundary + "\r\n"
        + "Content-Disposition: form-data; name=\"file\"; filename=\"" + fileName + "\"\r\n"
        + "Content-Type: " + document.getType().getMimeType() + "\r\n\r\n");
    out.write(document.getBytes(), 0, document.getBytes().length);
    write(out, "\r\n");
    if (!IdpSupport.isBlank(callbackUrl)) {
      JsonObject callback = new JsonObject();
      callback.addProperty("noAuthUrl", callbackUrl.trim());
      write(out, "--" + boundary + "\r\n"
          + "Content-Disposition: form-data; name=\"callback\"\r\n"
          + "Content-Type: application/json\r\n\r\n");
      byte[] callbackBytes = Json.toBytes(callback);
      out.write(callbackBytes, 0, callbackBytes.length);
      write(out, "\r\n");
    }
    write(out, "--" + boundary + "--\r\n");
    return out.toByteArray();
  }

  private static void write(ByteArrayOutputStream out, String text) {
    byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
    out.write(bytes, 0, bytes.length);
  }

  private static String tag(CorrelationInfo correlationInfo) {
    return "Smart IDP [" + (correlationInfo == null ? "-" : correlationInfo.getCorrelationId()) + "]";
  }

  private static String hostOf(String url) {
    String host = IdpUrlResolver.hostOf(url);
    return host == null ? "?" : host.toLowerCase(Locale.ROOT);
  }

  private static void sleep(long millis) throws SmartIdpException {
    if (millis <= 0) {
      return;
    }
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new SmartIdpException("Interrupted while waiting for IDP", SmartIdpError.TIMEOUT, e);
    }
  }
}
