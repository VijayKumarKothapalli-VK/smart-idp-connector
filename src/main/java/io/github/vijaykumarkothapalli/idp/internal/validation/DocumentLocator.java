/*
 * Smart IDP Connector
 * Copyright (c) 2026 Vijay Kumar Kothapalli
 * Licensed under the Apache License, Version 2.0
 */
package io.github.vijaykumarkothapalli.idp.internal.validation;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import io.github.vijaykumarkothapalli.idp.internal.error.SmartIdpError;
import io.github.vijaykumarkothapalli.idp.internal.error.SmartIdpException;
import org.apache.commons.io.IOUtils;
import org.mule.runtime.api.metadata.TypedValue;
import org.mule.runtime.api.streaming.bytes.CursorStream;
import org.mule.runtime.api.streaming.bytes.CursorStreamProvider;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Array;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds the document inside whatever the flow passes in, so users do not have to know whether to write
 * {@code #[payload]}, {@code #[payload.content]} or {@code #[payload.parts.file.content]}.
 * <p>
 * Understands raw binary, Base64 text and data URIs, JSON and XML wrappers, multipart/form-data uploads, Java maps and
 * lists produced by other connectors (Email attachments, Salesforce ContentVersion, Database BLOBs...), nested values,
 * Mule streams and typed values. A file name next to the content (fileName, name, Title + FileExtension, the multipart
 * filename or an attachment key) is picked up as well.
 */
public final class DocumentLocator {

  /** IDP accepts at most 15 MB; anything far bigger is refused before it is fully buffered. */
  static final long MAX_READ_BYTES = 100L * 1024 * 1024;
  private static final int MAX_DEPTH = 12;
  private static final int MIN_BASE64_LENGTH = 16;

  /** Field names that usually hold the file, best first. */
  private static final List<String> CONTENT_KEYS = Arrays.asList(
      "content", "file", "filecontent", "filedata", "document", "documentcontent", "documentdata", "data", "body",
      "base64", "base64content", "base64data", "contentbase64", "bytes", "binary", "attachment", "versiondata",
      "blob", "pdf", "image", "payload", "value");
  /** Field names that usually hold the file name, best first. */
  private static final List<String> NAME_KEYS = Arrays.asList(
      "filename", "file_name", "documentname", "document_name", "originalfilename", "pathonclient", "name", "title");
  private static final Pattern BOUNDARY = Pattern.compile("boundary=\"?([^\";,]+)\"?", Pattern.CASE_INSENSITIVE);
  private static final Pattern DISPOSITION_NAME = Pattern.compile("[; ]name=\"([^\"]*)\"", Pattern.CASE_INSENSITIVE);
  private static final Pattern DISPOSITION_FILENAME = Pattern.compile("filename=\"([^\"]*)\"", Pattern.CASE_INSENSITIVE);

  private DocumentLocator() {}

  /** The document that was found and where it came from. */
  public static final class Located {

    private final byte[] bytes;
    private final String fileName;
    private final String source;
    private final boolean base64Decoded;
    private final String mediaType;

    Located(byte[] bytes, String fileName, String source, boolean base64Decoded, String mediaType) {
      this.bytes = bytes;
      this.fileName = fileName;
      this.source = source;
      this.base64Decoded = base64Decoded;
      this.mediaType = mediaType;
    }

    public byte[] getBytes() {
      return bytes;
    }

    /** File name found next to the content, or null. */
    public String getFileName() {
      return fileName;
    }

    /** Human readable location, e.g. "JSON field content (Base64)". */
    public String getSource() {
      return source;
    }

    public boolean isBase64Decoded() {
      return base64Decoded;
    }

    /** Media type declared for the content, or null. */
    public String getMediaType() {
      return mediaType;
    }
  }

  private static final class Candidate {

    final String path;
    final byte[] bytes;
    final boolean base64;
    final DocumentType type;
    final String fileName;
    final int keyRank;
    final int depth;
    final String mediaType;

    Candidate(String path, byte[] bytes, boolean base64, String fileName, int keyRank, int depth, String mediaType) {
      this.path = path;
      this.bytes = bytes;
      this.base64 = base64;
      this.type = DocumentType.fromContent(bytes);
      this.fileName = fileName;
      this.keyRank = keyRank;
      this.depth = depth;
      this.mediaType = mediaType;
    }

    boolean recognised() {
      return type != DocumentType.UNKNOWN;
    }
  }

  /**
   * Locates the document.
   *
   * @param value     the operation's Document value (any Java type Mule hands over)
   * @param mediaType media type Mule declared for the value, may be null
   * @param fieldHint optional path to the field holding the file, e.g. "content" or "data.file"; overrides detection
   */
  public static Located locate(Object value, String mediaType, String fieldHint) throws SmartIdpException {
    Object root = unwrap(value);
    String declaredType = mediaType;
    if (value instanceof TypedValue && ((TypedValue<?>) value).getDataType() != null
        && ((TypedValue<?>) value).getDataType().getMediaType() != null) {
      declaredType = ((TypedValue<?>) value).getDataType().getMediaType().toRfcString();
    }
    if (root == null) {
      throw new SmartIdpException("No document was given: the Document value (default #[payload]) is empty.",
                                  SmartIdpError.INVALID_DOCUMENT);
    }

    // Raw bytes and text are inspected first: most flows pass the file itself.
    Object structured = root;
    if (root instanceof byte[] || root instanceof String) {
      byte[] bytes = root instanceof byte[] ? (byte[]) root : ((String) root).getBytes(StandardCharsets.UTF_8);
      Located direct = fromBytes(bytes, declaredType);
      if (direct != null) {
        return direct;
      }
      structured = parseStructured(bytes, declaredType);
      if (structured == null) {
        return new Located(bytes, null, describeRaw(declaredType), false, mimeOnly(declaredType));
      }
    }

    if (fieldHint != null && !fieldHint.trim().isEmpty()) {
      return fromHint(structured, fieldHint.trim());
    }

    List<Candidate> candidates = new ArrayList<>();
    walk(structured, "", null, 0, null, -1, candidates);
    if (candidates.isEmpty()) {
      throw noDocument(structured);
    }
    candidates.sort(Comparator.comparing((Candidate c) -> !c.recognised())
        .thenComparingInt(c -> c.keyRank < 0 ? Integer.MAX_VALUE : c.keyRank)
        .thenComparingInt(c -> c.depth));
    Candidate best = candidates.get(0);
    String source = describe(best);
    long recognised = candidates.stream().filter(Candidate::recognised).count();
    if (recognised > 1) {
      List<String> others = new ArrayList<>();
      candidates.stream().filter(Candidate::recognised).skip(1).limit(5).forEach(c -> others.add(c.path));
      source += "; " + (recognised - 1) + " other document(s) ignored (" + String.join(", ", others)
          + "), IDP takes one file per request - set Document Field to choose";
    }
    return new Located(best.bytes, best.fileName, source, best.base64, best.mediaType);
  }

  // ---------------------------------------------------------------------------------------------------------------
  // Raw content
  // ---------------------------------------------------------------------------------------------------------------

  /** Returns the document when the bytes are the file itself (binary, Base64, data URI, multipart), else null. */
  private static Located fromBytes(byte[] bytes, String mediaType) throws SmartIdpException {
    if (bytes.length == 0) {
      throw new SmartIdpException("The document is empty.", SmartIdpError.INVALID_DOCUMENT);
    }
    if (DocumentType.fromContent(bytes) != DocumentType.UNKNOWN) {
      return new Located(bytes, null, "binary content", false, mimeOnly(mediaType));
    }
    if (isMultipart(bytes, mediaType)) {
      return null;
    }
    String text = asciiPrefix(bytes, 64).trim();
    if (text.startsWith("{") || text.startsWith("[") || text.startsWith("<")) {
      return null;
    }
    byte[] decoded = DocumentInspector.decodeBase64(bytes);
    if (decoded != null && DocumentType.fromContent(decoded) != DocumentType.UNKNOWN) {
      boolean dataUri = text.regionMatches(true, 0, "data:", 0, 5);
      return new Located(decoded, null, dataUri ? "data URI (Base64)" : "Base64 text", true, dataUriMime(bytes));
    }
    return null;
  }

  private static Object parseStructured(byte[] bytes, String mediaType) throws SmartIdpException {
    if (isMultipart(bytes, mediaType)) {
      return parseMultipart(bytes, mediaType);
    }
    String text = new String(bytes, StandardCharsets.UTF_8).trim();
    if (text.startsWith("{") || text.startsWith("[")) {
      try {
        return new Gson().fromJson(text, Object.class);
      } catch (JsonParseException e) {
        throw new SmartIdpException("The payload looks like JSON but could not be parsed: " + e.getMessage(),
                                    SmartIdpError.INVALID_DOCUMENT, e);
      }
    }
    if (text.startsWith("<")) {
      return parseXml(bytes);
    }
    return null;
  }

  // ---------------------------------------------------------------------------------------------------------------
  // Walking structured values
  // ---------------------------------------------------------------------------------------------------------------

  private static void walk(Object node, String path, String key, int depth, Map<?, ?> parent, int keyRank,
                           List<Candidate> found)
      throws SmartIdpException {
    if (node == null || depth > MAX_DEPTH) {
      return;
    }
    Object value = unwrap(node);
    String mediaType = node instanceof TypedValue && ((TypedValue<?>) node).getDataType().getMediaType() != null
        ? mimeOnly(((TypedValue<?>) node).getDataType().getMediaType().toRfcString())
        : null;

    if (value instanceof byte[]) {
      byte[] bytes = (byte[]) value;
      if (bytes.length == 0) {
        return;
      }
      if (DocumentType.fromContent(bytes) != DocumentType.UNKNOWN) {
        found.add(new Candidate(path, bytes, false, fileNameFor(parent, key), keyRank, depth, mediaType));
        return;
      }
      byte[] decoded = DocumentInspector.decodeBase64(bytes);
      if (decoded != null && DocumentType.fromContent(decoded) != DocumentType.UNKNOWN) {
        found.add(new Candidate(path, decoded, true, fileNameFor(parent, key), keyRank, depth, mediaType));
        return;
      }
      Object nested = parseStructured(bytes, mediaType);
      if (nested != null) {
        walk(nested, path, key, depth + 1, parent, keyRank, found);
      } else if (keyRank >= 0) {
        // A binary value in a field named like content: keep it so validation can explain what it is.
        found.add(new Candidate(path, bytes, false, fileNameFor(parent, key), keyRank, depth, mediaType));
      }
      return;
    }
    if (value instanceof String) {
      String text = ((String) value).trim();
      if (text.length() >= MIN_BASE64_LENGTH || (keyRank >= 0 && text.length() >= 8)) {
        byte[] decoded = DocumentInspector.decodeBase64(text.getBytes(StandardCharsets.US_ASCII));
        // Recognised files anywhere; other Base64 only in content-like fields, so validation can name the type.
        if (decoded != null && (DocumentType.fromContent(decoded) != DocumentType.UNKNOWN || keyRank >= 0)) {
          found.add(new Candidate(path, decoded, true, fileNameFor(parent, key), keyRank, depth,
                                  dataUriMime(text.getBytes(StandardCharsets.US_ASCII))));
        } else if ((text.startsWith("{") || text.startsWith("[")) && text.length() < MAX_READ_BYTES) {
          // JSON inside a string, e.g. a message body field
          try {
            walk(new Gson().fromJson(text, Object.class), path, key, depth + 1, parent, keyRank, found);
          } catch (JsonParseException e) {
            // plain text that only starts like JSON
          }
        }
      }
      return;
    }
    if (value instanceof Map) {
      Map<?, ?> map = (Map<?, ?>) value;
      for (Map.Entry<?, ?> entry : map.entrySet()) {
        String childKey = String.valueOf(entry.getKey());
        walk(entry.getValue(), path.isEmpty() ? childKey : path + "." + childKey, childKey, depth + 1, map,
             rank(childKey), found);
      }
      return;
    }
    if (value instanceof Collection || (value != null && value.getClass().isArray())) {
      List<Object> items = toList(value);
      for (int i = 0; i < items.size(); i++) {
        walk(items.get(i), path + "[" + i + "]", key, depth + 1, parent, keyRank, found);
      }
    }
  }

  private static Located fromHint(Object structured, String hint) throws SmartIdpException {
    // Accepts content, data.file, [1].content, 1.content, items[0].file, payload.content and #[payload.content].
    String path = hint.replaceAll("^#\\[(.*)\\]$", "$1").trim();
    path = path.replaceAll("^payload(\\.|(?=\\[)|$)", "");
    path = path.replaceAll("\\[(\\d+)]", ".$1").replaceAll("^\\.", "");
    Object current = structured;
    Map<?, ?> parent = null;
    String[] segments = path.split("\\.");
    for (int i = 0; i < segments.length; i++) {
      Object unwrapped = unwrap(current);
      if ((unwrapped instanceof Collection || (unwrapped != null && unwrapped.getClass().isArray()
          && !(unwrapped instanceof byte[]))) && segments[i].matches("\\d+")) {
        List<Object> items = toList(unwrapped);
        int index = Integer.parseInt(segments[i]);
        if (index >= items.size()) {
          throw new SmartIdpException("Document Field '" + hint + "' not found: the list has only " + items.size()
              + " item(s) (indexes start at 0).", SmartIdpError.INVALID_INPUT);
        }
        current = items.get(index);
        continue;
      }
      if (!(unwrapped instanceof Map)) {
        throw new SmartIdpException("Document Field '" + hint + "' not found: '" + String.join(".",
            Arrays.copyOf(segments, i)) + "' is not an object" + (unwrapped instanceof Collection
                ? " but a list - use an index such as [0]" : "") + ".", SmartIdpError.INVALID_INPUT);
      }
      Map<?, ?> map = (Map<?, ?>) unwrapped;
      // Keys may themselves contain dots (e.g. attachment names like invoice.pdf).
      String rest = String.join(".", Arrays.copyOfRange(segments, i, segments.length));
      if (map.containsKey(rest)) {
        parent = map;
        current = map.get(rest);
        break;
      }
      if (!map.containsKey(segments[i])) {
        throw new SmartIdpException("Document Field '" + hint + "' not found. Available fields at that level: "
            + map.keySet(), SmartIdpError.INVALID_INPUT);
      }
      parent = map;
      current = map.get(segments[i]);
    }
    List<Candidate> found = new ArrayList<>();
    walk(current, path, segmentsKey(path), 0, parent, 0, found);
    if (found.isEmpty()) {
      throw new SmartIdpException("Document Field '" + hint + "' does not contain a file (expected binary or Base64).",
                                  SmartIdpError.INVALID_DOCUMENT);
    }
    found.sort(Comparator.comparing((Candidate c) -> !c.recognised()).thenComparingInt(c -> c.depth));
    Candidate best = found.get(0);
    return new Located(best.bytes, best.fileName, describe(best), best.base64, best.mediaType);
  }

  // ---------------------------------------------------------------------------------------------------------------
  // Multipart
  // ---------------------------------------------------------------------------------------------------------------

  private static boolean isMultipart(byte[] bytes, String mediaType) {
    if (mediaType != null && mediaType.toLowerCase(Locale.ROOT).startsWith("multipart/")) {
      return true;
    }
    String start = asciiPrefix(bytes, 400);
    return start.startsWith("--") && start.toLowerCase(Locale.ROOT).contains("content-disposition:");
  }

  /** Parses multipart/form-data into a map of part name to bytes (file parts become {fileName, content, contentType}). */
  static Map<String, Object> parseMultipart(byte[] body, String mediaType) throws SmartIdpException {
    String boundary = null;
    if (mediaType != null) {
      Matcher matcher = BOUNDARY.matcher(mediaType);
      if (matcher.find()) {
        boundary = matcher.group(1);
      }
    }
    if (boundary == null) {
      String first = asciiPrefix(body, 200);
      int end = first.indexOf("\r\n");
      if (first.startsWith("--") && end > 2) {
        boundary = first.substring(2, end).trim();
      }
    }
    if (boundary == null) {
      throw new SmartIdpException("Multipart content without a boundary.", SmartIdpError.INVALID_DOCUMENT);
    }
    byte[] delimiter = ("--" + boundary).getBytes(StandardCharsets.ISO_8859_1);
    Map<String, Object> parts = new LinkedHashMap<>();
    int position = indexOf(body, delimiter, 0);
    int index = 0;
    while (position >= 0) {
      int partStart = position + delimiter.length;
      if (partStart + 1 < body.length && body[partStart] == '-' && body[partStart + 1] == '-') {
        break;
      }
      partStart = skipLineBreak(body, partStart);
      int next = indexOf(body, delimiter, partStart);
      if (next < 0) {
        break;
      }
      int headerEnd = indexOf(body, "\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1), partStart);
      if (headerEnd < 0 || headerEnd > next) {
        position = next;
        continue;
      }
      String headers = new String(body, partStart, headerEnd - partStart, StandardCharsets.ISO_8859_1);
      int contentEnd = next;
      if (contentEnd >= 2 && body[contentEnd - 2] == '\r' && body[contentEnd - 1] == '\n') {
        contentEnd -= 2;
      }
      byte[] content = Arrays.copyOfRange(body, headerEnd + 4, Math.max(headerEnd + 4, contentEnd));
      String name = group(DISPOSITION_NAME, headers);
      String fileName = group(DISPOSITION_FILENAME, headers);
      String contentType = null;
      for (String line : headers.split("\r\n")) {
        if (line.toLowerCase(Locale.ROOT).startsWith("content-type:")) {
          contentType = line.substring("content-type:".length()).trim();
        }
      }
      String key = name == null || name.isEmpty() ? "part" + index : name;
      if (fileName != null || DocumentType.fromContent(content) != DocumentType.UNKNOWN) {
        Map<String, Object> filePart = new LinkedHashMap<>();
        filePart.put("fileName", fileName);
        filePart.put("contentType", contentType);
        filePart.put("content", content);
        parts.put(key, filePart);
      } else {
        parts.put(key, new String(content, StandardCharsets.UTF_8));
      }
      index++;
      position = next;
    }
    return parts;
  }

  private static int skipLineBreak(byte[] body, int position) {
    if (position + 1 < body.length && body[position] == '\r' && body[position + 1] == '\n') {
      return position + 2;
    }
    return position;
  }

  private static int indexOf(byte[] data, byte[] pattern, int from) {
    outer:
    for (int i = Math.max(0, from); i <= data.length - pattern.length; i++) {
      for (int j = 0; j < pattern.length; j++) {
        if (data[i + j] != pattern[j]) {
          continue outer;
        }
      }
      return i;
    }
    return -1;
  }

  // ---------------------------------------------------------------------------------------------------------------
  // XML
  // ---------------------------------------------------------------------------------------------------------------

  private static Object parseXml(byte[] bytes) throws SmartIdpException {
    try {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      // Hardened against XXE: no DTDs, no external entities.
      factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
      factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
      factory.setXIncludeAware(false);
      factory.setExpandEntityReferences(false);
      factory.setNamespaceAware(false);
      Element root = factory.newDocumentBuilder().parse(new ByteArrayInputStream(bytes)).getDocumentElement();
      Map<String, Object> wrapper = new LinkedHashMap<>();
      wrapper.put(root.getNodeName(), xmlToObject(root));
      return wrapper;
    } catch (Exception e) {
      throw new SmartIdpException("The payload looks like XML but could not be parsed: " + e.getMessage(),
                                  SmartIdpError.INVALID_DOCUMENT, e);
    }
  }

  private static Object xmlToObject(Element element) {
    Map<String, Object> map = new LinkedHashMap<>();
    for (int i = 0; i < element.getAttributes().getLength(); i++) {
      Node attribute = element.getAttributes().item(i);
      map.put(attribute.getNodeName(), attribute.getNodeValue());
    }
    NodeList children = element.getChildNodes();
    boolean hasElements = false;
    for (int i = 0; i < children.getLength(); i++) {
      if (children.item(i) instanceof Element) {
        hasElements = true;
        Element child = (Element) children.item(i);
        Object value = xmlToObject(child);
        Object existing = map.get(child.getNodeName());
        if (existing == null) {
          map.put(child.getNodeName(), value);
        } else if (existing instanceof List) {
          @SuppressWarnings("unchecked")
          List<Object> list = (List<Object>) existing;
          list.add(value);
        } else {
          List<Object> list = new ArrayList<>();
          list.add(existing);
          list.add(value);
          map.put(child.getNodeName(), list);
        }
      }
    }
    if (!hasElements && map.isEmpty()) {
      return element.getTextContent();
    }
    if (!hasElements) {
      map.put("#text", element.getTextContent());
    }
    return map;
  }

  // ---------------------------------------------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------------------------------------------

  /** Turns Mule wrappers and streams into plain Java values (byte[] for binary content). */
  public static Object unwrap(Object value) throws SmartIdpException {
    Object current = value;
    for (int i = 0; i < 5 && current instanceof TypedValue; i++) {
      current = ((TypedValue<?>) current).getValue();
    }
    if (current instanceof CursorStreamProvider) {
      try (CursorStream stream = ((CursorStreamProvider) current).openCursor()) {
        return read(stream);
      } catch (IOException e) {
        throw new SmartIdpException("Could not read the document stream: " + e.getMessage(), SmartIdpError.INVALID_DOCUMENT, e);
      }
    }
    if (current instanceof InputStream) {
      try (InputStream stream = (InputStream) current) {
        return read(stream);
      } catch (IOException e) {
        throw new SmartIdpException("Could not read the document stream: " + e.getMessage(), SmartIdpError.INVALID_DOCUMENT, e);
      }
    }
    if (current instanceof Byte[]) {
      Byte[] boxed = (Byte[]) current;
      byte[] bytes = new byte[boxed.length];
      for (int i = 0; i < boxed.length; i++) {
        bytes[i] = boxed[i];
      }
      return bytes;
    }
    if (current instanceof java.nio.ByteBuffer) {
      java.nio.ByteBuffer buffer = ((java.nio.ByteBuffer) current).duplicate();
      byte[] bytes = new byte[buffer.remaining()];
      buffer.get(bytes);
      return bytes;
    }
    if (current instanceof CharSequence && !(current instanceof String)) {
      return current.toString();
    }
    return current;
  }

  private static byte[] read(InputStream stream) throws IOException, SmartIdpException {
    byte[] bytes = IOUtils.toByteArray(new BoundedInputStream(stream, MAX_READ_BYTES + 1));
    if (bytes.length > MAX_READ_BYTES) {
      throw new SmartIdpException("The document is larger than " + (MAX_READ_BYTES / (1024 * 1024))
          + " MB and was not read. IDP accepts at most 15 MB per file.", SmartIdpError.FILE_TOO_LARGE);
    }
    return bytes;
  }

  private static List<Object> toList(Object value) {
    List<Object> items = new ArrayList<>();
    if (value instanceof Collection) {
      items.addAll((Collection<?>) value);
    } else if (value instanceof byte[]) {
      items.add(value);
    } else {
      for (int i = 0; i < Array.getLength(value); i++) {
        items.add(Array.get(value, i));
      }
    }
    return items;
  }

  private static int rank(String key) {
    String normalised = key.toLowerCase(Locale.ROOT).replace("-", "").replace(" ", "");
    int index = CONTENT_KEYS.indexOf(normalised);
    if (index >= 0) {
      return index;
    }
    // Attachment maps use the file name as key, e.g. {"invoice.pdf": <binary>}.
    return DocumentType.fromFileName(key) != DocumentType.UNKNOWN ? 0 : -1;
  }

  /** File name for a value: a sibling name field, Title + FileExtension, or the key itself when it is a file name. */
  private static String fileNameFor(Map<?, ?> parent, String key) {
    if (key != null && DocumentType.fromFileName(key) != DocumentType.UNKNOWN) {
      return key;
    }
    if (parent == null) {
      return null;
    }
    Map<String, Object> byKey = new LinkedHashMap<>();
    for (Map.Entry<?, ?> entry : parent.entrySet()) {
      byKey.put(String.valueOf(entry.getKey()).toLowerCase(Locale.ROOT), entry.getValue());
    }
    for (String nameKey : NAME_KEYS) {
      Object candidate = byKey.get(nameKey);
      if (candidate instanceof TypedValue) {
        candidate = ((TypedValue<?>) candidate).getValue();
      }
      if (candidate instanceof String && !((String) candidate).trim().isEmpty() && ((String) candidate).length() < 260) {
        String name = ((String) candidate).trim();
        Object extension = byKey.get("fileextension");
        if (DocumentType.fromFileName(name) == DocumentType.UNKNOWN && extension instanceof String) {
          name = name + "." + extension;
        }
        return name.replaceAll(".*[/\\\\]", "");
      }
    }
    // Any other field whose value looks like a document file name, e.g. DOC_NAME = "po-123.pdf".
    for (Object candidate : byKey.values()) {
      if (candidate instanceof TypedValue) {
        candidate = ((TypedValue<?>) candidate).getValue();
      }
      if (candidate instanceof String && ((String) candidate).length() < 260
          && DocumentType.fromFileName(((String) candidate).trim()) != DocumentType.UNKNOWN) {
        return ((String) candidate).trim().replaceAll(".*[/\\\\]", "");
      }
    }
    return null;
  }

  private static String segmentsKey(String path) {
    return path.contains(".") ? path.substring(path.lastIndexOf('.') + 1) : path;
  }

  private static SmartIdpException noDocument(Object structured) {
    Object value = structured;
    String shape;
    if (value instanceof Map) {
      Map<?, ?> map = (Map<?, ?>) value;
      if (map.containsKey("executionId") && map.containsKey("status")) {
        return new SmartIdpException("The payload is the JSON response of an earlier Process Document call, not a "
            + "document. Set Document to the original file (for example save it in a variable before the first call).",
                                     SmartIdpError.INVALID_DOCUMENT);
      }
      shape = "an object with fields " + map.keySet();
    } else if (value instanceof Collection) {
      shape = "a list of " + ((Collection<?>) value).size() + " item(s)";
    } else {
      shape = "a value of type " + (value == null ? "null" : value.getClass().getSimpleName());
    }
    return new SmartIdpException("No document found in the payload. It is " + shape + ", but none of its values is a "
        + "PDF, PNG, JPG or TIFF file (binary or Base64). Pass the file itself, or set 'Document Field' to the field "
        + "that holds it (for example 'content' or 'data.file').", SmartIdpError.INVALID_DOCUMENT);
  }

  private static String describe(Candidate candidate) {
    String where = candidate.path.isEmpty() ? "payload" : "field '" + candidate.path + "'";
    return where + (candidate.base64 ? " (Base64)" : " (binary)");
  }

  private static String describeRaw(String mediaType) {
    return "raw content" + (mediaType == null ? "" : " (" + mimeOnly(mediaType) + ")");
  }

  private static String mimeOnly(String mediaType) {
    if (mediaType == null) {
      return null;
    }
    int semicolon = mediaType.indexOf(';');
    return (semicolon < 0 ? mediaType : mediaType.substring(0, semicolon)).trim();
  }

  private static String dataUriMime(byte[] bytes) {
    String start = asciiPrefix(bytes, 100);
    if (start.regionMatches(true, 0, "data:", 0, 5) && start.indexOf(';') > 5) {
      return start.substring(5, start.indexOf(';'));
    }
    return null;
  }

  private static String asciiPrefix(byte[] bytes, int length) {
    return new String(bytes, 0, Math.min(length, bytes.length), StandardCharsets.ISO_8859_1);
  }

  private static String group(Pattern pattern, String text) {
    Matcher matcher = pattern.matcher(text);
    return matcher.find() ? matcher.group(1) : null;
  }

  /** Stops reading after a limit so a huge stream is not fully buffered. */
  private static final class BoundedInputStream extends InputStream {

    private final InputStream delegate;
    private long remaining;

    BoundedInputStream(InputStream delegate, long limit) {
      this.delegate = delegate;
      this.remaining = limit;
    }

    @Override
    public int read() throws IOException {
      if (remaining <= 0) {
        return -1;
      }
      int value = delegate.read();
      if (value >= 0) {
        remaining--;
      }
      return value;
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
      if (remaining <= 0) {
        return -1;
      }
      int count = delegate.read(buffer, offset, (int) Math.min(length, remaining));
      if (count > 0) {
        remaining -= count;
      }
      return count;
    }
  }
}
