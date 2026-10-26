# Smart IDP Connector 2.0 for Mule 4

<img src="icon/icon.svg" width="64" alt="Smart IDP logo">

A Mule 4 connector for **MuleSoft Intelligent Document Processing (IDP)**. One operation, **Process Document**, takes a file
and either returns the extracted data or hands it to your callback URL. It works with a plain **Connected App** and with
any project: pick the document action from a drop-down, and `latest` always runs the newest published version.

**Author:** Vijay Kumar Kothapalli · vijaykumarstar111@gmail.com · [LinkedIn](https://www.linkedin.com/in/vijay-kumar-kothapalli)
**License:** Apache 2.0

---

## What it does

| | |
|---|---|
| **Process Document** | Validates the file, submits it, and returns the result. Choose how the result is delivered (see response modes below). |
| **Action and version pick lists** | Lists your org's published document actions and their versions from Exchange using only the Connected App. The version defaults to `latest`. |
| **IDP limits built in** | Before upload it checks file type (PDF, PNG, JPG, TIFF), size (**15 MB**) and pages (**150**), the current IDP API limits. You can change them per project or per call. |
| **Any input format** | Leave Document at `#[payload]` and the connector finds the file in raw binary, Base64, data URIs, JSON, XML, multipart uploads, and Email / Salesforce / Database / S3 outputs, picking up the file name next to it. See [INPUT-FORMATS.md](INPUT-FORMATS.md). |
| **Reliable waiting** | Polling backs off from 2 s up to 10 s. Short network blips are retried. It stops exactly at your max wait. |
| **One response shape** | Every call returns the same JSON envelope, so flows don't need to branch on the mode. |
| **Logging** | Each line carries the flow's correlation id: submission, every status change, the final status and the timing. Secrets, tokens and document content are never logged. |

## Operations

| Operation | Use it to |
|---|---|
| **Process Document** | Send a document and get the result back, or have IDP call your callback. |
| **Get Execution Result** | Collect a result later, for example after a pending response or a callback. Accepts the execution id or the `resultUrl`, and can optionally wait. |
| **Validate Document** | Check a file against the limits without sending it. |

### Response modes

| Mode | Behaviour | Use when |
|---|---|---|
| `WAIT_FOR_RESULT` (default) | Waits up to **Max Wait** and returns the extracted data. | APIs and flows that need the answer now. |
| `CALLBACK` | Returns at once with `executionId`. IDP calls your HTTPS **Callback URL** when it finishes. | Large documents, batch jobs, event-driven designs. |
| `WAIT_WITH_CALLBACK_FALLBACK` | Waits up to Max Wait. Fast documents return directly; slow ones return pending, and IDP still calls the callback. | You want the result now when possible without losing slow ones. |

**Max Wait defaults to 60 seconds.** In testing on a live tenant, a 2-page document took about **4–13 s** from submit to
final status. 30 s leaves too little headroom for multi-page files or a busy tenant, while 60 s covers typical documents
without holding a flow thread for long. For large files, raise it or use a callback mode.

When Max Wait is reached, `RETURN_PENDING` (default) returns `completed: false` plus `resultUrl`, and `RAISE_ERROR`
raises `SMART-IDP:TIMEOUT`.

IDP calls the callback when the execution ends as `SUCCEEDED`, `FAILED` or `MANUAL_VALIDATION_REQUIRED`. The callback
URL **must be HTTPS** and reachable from IDP. The connector rejects anything else before submitting.

### Response envelope

```json
{
  "executionId": "4f47ebcd-ebf3-473c-b12a-dbd83e87f258",
  "status": "SUCCEEDED",
  "completed": true,
  "message": "Document processed successfully.",
  "responseMode": "WAIT_FOR_RESULT",
  "action": { "id": "4613bf35-20f9-4d94-b79f-c576ca5a076b", "version": "1.1.0" },
  "documentName": "invoice.pdf",
  "resultUrl": "https://idp-rt.us-east-1.anypoint.mulesoft.com/api/v1/organizations/.../executions/4f47ebcd-...",
  "callbackUrl": null,
  "elapsedMs": 6596,
  "validation": { "valid": true, "pageCount": 2, "fileSizeMb": 0.01, "...": "..." },
  "result": { "id": "...", "status": "SUCCEEDED", "fields": { "Grand_Total": { "value": "1250.00" } }, "tables": {} }
}
```

`status` is one of the IDP statuses (`SUCCEEDED`, `PARTIAL_SUCCESS`, `MANUAL_VALIDATION_REQUIRED`, `FAILED`, or while
still running `ACKNOWLEDGED` / `IN_PROGRESS` / `RESULTS_PENDING`), or `SUBMITTED` in CALLBACK mode. Set
**Include Confidence And Geometry** to get confidence scores and positions with each value.

## Setup

### 1. Connected App

In Anypoint **Access Management → Connected Apps**, create an app that acts on its own behalf (client credentials) with:

- **Document Actions → Execute Published Actions**: required to process documents.
- **Exchange → Exchange Viewer**: for the action and version pick lists and `latest` resolution.

A client-credentials app can't manage actions, prompts or review tasks. In IDP those are user permissions, not app scopes.

### 2. Add the dependency

The connector is published on **Maven Central**. Add it to your Mule application's `pom.xml` and Maven downloads it
automatically, with no extra repository settings. In Anypoint Studio / Code Builder, **Smart IDP** then appears in
the palette.

```xml
<dependency>
  <groupId>io.github.vijaykumarkothapalli-vk</groupId>
  <artifactId>smart-idp-connector</artifactId>
  <version>2.0.0</version>
  <classifier>mule-plugin</classifier>
</dependency>
```

To build it from source instead: `mvn clean install` in this repository.

### 3. Configure

```xml
<smart-idp:config name="Smart_IDP_Config" maxWaitSeconds="60">
  <smart-idp:connected-app-connection
      idpUrl="${idp.url}"
      clientId="${secure::idp.clientId}"
      clientSecret="${secure::idp.clientSecret}"/>
</smart-idp:config>
```

For `idp.url`, paste your document action's **execution URL** (from the action's publish page). The region, organization
and default action come from it. `US` or `EU` also works if you always pick the action on the operation.

| Configuration | Default | |
|---|---|---|
| Max Wait (seconds) | 60 | See response modes. |
| When Max Wait Is Reached | RETURN_PENDING | Or RAISE_ERROR. |
| Default Callback URL | – | Used by the callback modes when the operation sets none. |
| Default Document Action | – | Action id or execution URL. Not needed when `idpUrl` is an execution URL. |
| Validate Before Submit | true | |
| Max File Size (MB) / Max Pages | 15 / 150 | IDP API limits; `0` disables a check. |
| Allowed File Types | PDF,PNG,JPEG,TIFF | `*` disables the check. |

## Examples

```xml
<!-- Wait for the result (the incoming payload is the file) -->
<smart-idp:process-document config-ref="Smart_IDP_Config" fileName="#[attributes.fileName]"/>

<!-- Callback: return immediately, IDP posts the outcome to your HTTPS endpoint -->
<smart-idp:process-document config-ref="Smart_IDP_Config" responseMode="CALLBACK"
                            callbackUrl="https://my-app.example.com/api/idp/callback"/>

<!-- Best of both: wait up to 30s, otherwise rely on the callback -->
<smart-idp:process-document config-ref="Smart_IDP_Config" responseMode="WAIT_WITH_CALLBACK_FALLBACK"
                            maxWaitSeconds="30" callbackUrl="${idp.callbackUrl}"/>

<!-- Collect a pending result later -->
<smart-idp:get-execution-result config-ref="Smart_IDP_Config" execution="#[vars.idp.resultUrl]" maxWaitSeconds="30"/>

<!-- Stricter limits for one flow -->
<smart-idp:process-document config-ref="Smart_IDP_Config" maxFileSizeMb="5" maxPages="20" allowedFileTypes="PDF"/>
```

## Errors

| Error | When |
|---|---|
| `SMART-IDP:FILE_TOO_LARGE`, `TOO_MANY_PAGES`, `UNSUPPORTED_FILE_TYPE`, `INVALID_DOCUMENT` | The file fails the limits, or is empty, corrupt or password protected. Nothing is uploaded. |
| `SMART-IDP:INVALID_INPUT` | Missing or non-HTTPS callback URL, unknown action reference, missing credentials. |
| `SMART-IDP:UNAUTHORIZED` / `FORBIDDEN` | Wrong credentials / missing Connected App scope. The message names the scope. |
| `SMART-IDP:NOT_FOUND` | Unknown action or version. |
| `SMART-IDP:TIMEOUT` | Max Wait reached with `RAISE_ERROR`. |
| `SMART-IDP:EXECUTION_FAILED` | IDP reported `FAILED` (unless *Raise Error When Execution Fails* is off). |
| `SMART-IDP:CONNECTIVITY`, `SERVER_ERROR`, `RATE_LIMITED` | Network or IDP problems. Status checks retry these automatically. |

## Logging

Logger: `io.github.vijaykumarkothapalli.idp`. At `INFO` you get one line per step:

```
Smart IDP [<correlationId>] submitting 'invoice.pdf' (482 KB, 3 pages, PDF) to action 4613bf35-... v1.1.0 [mode=WAIT_FOR_RESULT]
Smart IDP [<correlationId>] IDP accepted 'invoice.pdf' as execution 4f47ebcd-...
Smart IDP [<correlationId>] execution 4f47ebcd-... status: IN_PROGRESS
Smart IDP [<correlationId>] execution 4f47ebcd-... finished with status SUCCEEDED in 6596 ms
```

`WARN` covers rejected documents, retried status checks and timeouts; `ERROR` covers failed executions and authentication
failures. `DEBUG` adds every HTTP call with its status and duration.
