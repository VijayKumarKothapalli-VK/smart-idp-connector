# Smart IDP: sending documents from any source

**Short version:** leave **Document** at its default `#[payload]` and the connector finds the file itself. You don't need
to write `payload.content`, `payload.parts.file.content` or `payload.attachments...`.

IDP takes **one file per request**: PDF, PNG, JPG or TIFF, up to 15 MB and 150 pages.

---

## 1. How the connector finds the document

When Content Encoding is `AUTO` (the default), the connector looks at the Document value in this order:

1. **Is it the file itself?** It checks the first bytes (`%PDF`, PNG, JPEG, TIFF signatures), so the declared
   Content-Type doesn't matter.
2. **Is it Base64 text or a data URI?** (`JVBERi0…`, `data:application/pdf;base64,…`, with or without line breaks) If
   so, it decodes it.
3. **Is it a multipart upload?** It reads the parts and takes the file part, with its `filename`.
4. **Is it JSON or XML text?** It parses it and searches every field.
5. **Is it a Java map or list** from another connector (Salesforce, Email, Database, ...)? It searches every value,
   including streams and typed values nested inside.

**While searching it:**
- prefers values that really are a PDF/PNG/JPG/TIFF;
- then prefers fields named `content`, `file`, `fileContent`, `document`, `data`, `body`, `base64`, `bytes`,
  `attachment`, `VersionData`, `blob`, ... (case-insensitive);
- takes the **file name** from a neighbouring field: `fileName`, `filename`, `documentName`, `originalFileName`,
  `PathOnClient`, `name`, `Title` (+ `FileExtension`), any field whose value looks like `something.pdf`, the multipart
  `filename`, or an attachment key such as `invoice.pdf`;
- if it finds **several documents**, uses the first and says so in `contentSource` and the log. Set **Document Field**
  to pick a specific one.

Every response tells you what it used:

```json
"validation": {
  "fileName": "nested-scan.pdf",
  "contentSource": "field 'request.attachment.blobValue' (Base64)",
  "detectedFileType": "PDF", "pageCount": 2, "valid": true
}
```

and the log line says the same: `submitting 'nested-scan.pdf' (… from field 'request.attachment.blobValue' (Base64))`.

---

## 2. Where the data comes from, and how to configure each source

In almost every case: **Document = `#[payload]` (default)**. **File Name** is optional; set it when the source keeps
the name in attributes, which the connector can't see.

| Source | What the payload looks like | Document | File Name | Notes |
|---|---|---|---|---|
| **HTTP Listener, raw body** (`Content-Type: application/pdf`, `image/png`, `application/octet-stream`) | Binary stream | default | `#[attributes.queryParams.fileName]` or a header | The Content-Type can even be wrong; detection uses the bytes. |
| **HTTP Listener, form upload** (`multipart/form-data`, browser or Postman "form-data") | Multipart body | default | taken from the part's `filename` | Other form fields are ignored. With several file parts, set Document Field to the part name, e.g. `upload.content`. |
| **HTTP Listener, JSON body** `{"fileName":"a.pdf","content":"<base64>"}` | JSON text | default | taken from `fileName` | Any field names and any nesting work: `{"request":{"attachment":{"data":"..."}}}`. |
| **HTTP Listener, JSON with a data URI** `{"file":"data:application/pdf;base64,..."}` | JSON text | default | taken from `name`/`fileName` if present | |
| **HTTP Listener, XML body** `<doc><fileName>a.pdf</fileName><content>base64</content></doc>` | XML text | default | taken from `<fileName>` | DTDs and external entities are refused (XXE-safe). |
| **HTTP Listener, plain Base64 text** | Text | default | set it, otherwise `document.pdf` | |
| **HTTP Request** (downloading a file from another system) | Binary stream | default | from the URL or a header | Download it first with HTTP Request; the connector doesn't fetch URLs itself. |
| **File / FTP / SFTP: Read, On New or Updated File** | Binary stream | default | `#[attributes.fileName]` | |
| **Amazon S3: Get Object**, **Azure Blob**, **Google Drive**, **SharePoint / OneDrive** download | Binary stream | default | the object key / file name from attributes | |
| **Email: IMAP/POP3 listener or List** | Object with `attachments` map (`{"invoice.pdf": <binary>, ...}`) | default | taken from the attachment name | Text attachments are skipped. With several PDFs, set Document Field to `attachments.invoice.pdf`. |
| **Salesforce: ContentVersion query** (`SELECT Title, FileExtension, VersionData ...`) | List of maps, `VersionData` binary | default | `Title` + `FileExtension` | Also works for `Attachment.Body` and `Document.Body`. |
| **Database: SELECT with a BLOB column** | List of rows | default | from a column whose value looks like `x.pdf` (e.g. `DOC_NAME`) | With several rows, the first document is used. Filter in SQL or set Document Field, e.g. `[2].DOC_BLOB`. |
| **Anypoint MQ / JMS / Kafka / VM** message carrying JSON with Base64 | JSON text | default | from the JSON | |
| **A previous Transform Message** producing `output application/java` with a binary field | Java map | default | from a name field | |
| **A variable**, e.g. you stored the file earlier | anything above | `#[vars.originalFile]` | as needed | Use this after the payload has been replaced. |

### Content Encoding

| Value | When to use |
|---|---|
| `AUTO` (default) | Always, unless you have a very unusual input. |
| `BINARY` | Send the bytes exactly as received, with no detection. Only for binary or text payloads. |
| `BASE64` | The payload is Base64 text and must be decoded, with no other detection. |

### Document Field (only when needed)

Use it when the input contains **more than one file** and you want a specific one. Accepted forms:

```
content            data.file            attachments.invoice.pdf
[1].content        1.content            payload[1].content         #[payload.items[0].file]
```

If the field doesn't exist, the error lists the fields available at that level.

---

## 3. Examples

```xml
<!-- HTTP upload: raw body, form-data, JSON or XML all work with the same configuration -->
<smart-idp:process-document config-ref="Smart_IDP_Config"/>

<!-- File / SFTP: keep the original name -->
<smart-idp:process-document config-ref="Smart_IDP_Config" fileName="#[attributes.fileName]"/>

<!-- Salesforce ContentVersion: the name comes from Title + FileExtension -->
<salesforce:query config-ref="Salesforce_Config">
  <salesforce:salesforce-query>SELECT Title, FileExtension, VersionData FROM ContentVersion WHERE Id = ':id'</salesforce:salesforce-query>
</salesforce:query>
<smart-idp:process-document config-ref="Smart_IDP_Config"/>

<!-- Email with several attachments: choose one -->
<smart-idp:process-document config-ref="Smart_IDP_Config" documentField="attachments.invoice.pdf"/>

<!-- The payload was replaced earlier: keep the file in a variable -->
<set-variable variableName="originalFile" value="#[payload]"/>
...
<smart-idp:process-document config-ref="Smart_IDP_Config">
  <smart-idp:document>#[vars.originalFile]</smart-idp:document>
</smart-idp:process-document>
```

---

## 4. What is NOT done automatically, on purpose

| Input | Why | Do this instead |
|---|---|---|
| A **file path** string (`C:/docs/a.pdf`) | Reading server files from request data is a security risk. | Read it with the File connector first. |
| A **URL** to the document | Fetching arbitrary URLs is a security risk (SSRF). | Download it with HTTP Request first. |
| **Several files in one call** | IDP processes one file per request. | Use a For Each over the files and call Process Document for each. |
| **Word, Excel, ZIP, TXT** | IDP doesn't accept them. | Convert to PDF first. The connector reports e.g. `File type DOCX is not allowed`. |
| Inputs over **100 MB** | They are refused before being fully read (IDP's limit is 15 MB anyway). | |

---

## 5. Troubleshooting by error message

| Error | Meaning | Fix |
|---|---|---|
| `INVALID_DOCUMENT: No document found in the payload. It is an object with fields [orderId, customer]...` | The payload has no file in it. | Pass the message that contains the file, or set Document to where the file is. |
| `INVALID_DOCUMENT: The payload is the JSON response of an earlier Process Document call` | A Process Document (or a Transform) replaced the file before this call. | Save the file in a variable first and use `#[vars.…]`. |
| `UNSUPPORTED_FILE_TYPE: File type DOCX is not allowed` | A file was found, but IDP doesn't accept that type. | Convert it, or change Allowed File Types if IDP supports it. |
| `INVALID_INPUT: Document Field 'x' not found. Available fields at that level: [...]` | Typo in Document Field. | Use one of the listed names. |
| `INVALID_DOCUMENT: The PDF is password protected` | Encrypted PDF. | Remove the password first. |
| `TOO_MANY_PAGES` / `FILE_TOO_LARGE` | The file is over the limits (default 150 pages / 15 MB). | Split it, or lower or raise the limits on the config or operation. |

`contentSource` in the response and the `from …` part of the log line always show which value was used. Check them
first when the result isn't what you expected.
