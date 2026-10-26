# Smart IDP Connector 2.0

A Mule 4 connector for MuleSoft Intelligent Document Processing, by Vijay Kumar Kothapalli.

- **Process Document**: validate, submit, and either wait for the extracted data, let IDP call your HTTPS callback, or both.
- Max Wait defaults to 60 s. When it runs out you either get a pending response or a TIMEOUT error.
- Pick lists for document actions and versions, read from Exchange with a Connected App. `latest` always runs the newest version.
- IDP API limits are checked before upload: PDF/PNG/JPG/TIFF, 15 MB, 150 pages. All limits are configurable.
- Logs are tied to the flow's correlation id. No secrets are logged.
