# Kestra Airtable Plugin

## What

- Provides plugin components under `io.kestra.plugin.airtable`.
- Includes classes such as `AirtableException`, `AirtableRecord`, `AirtableClient`, `AirtableListResponse`.

## Why

- What user problem does this solve? Teams need to connect to Airtable bases and tables over the REST API for workflow automation from orchestrated workflows instead of relying on manual console work, ad hoc scripts, or disconnected schedulers.
- Why would a team adopt this plugin in a workflow? It keeps Airtable steps in the same Kestra flow as upstream preparation, approvals, retries, notifications, and downstream systems.
- What operational/business outcome does it enable? It reduces manual handoffs and fragmented tooling while improving reliability, traceability, and delivery speed for processes that depend on Airtable.

## How

### Architecture

Single-module plugin. Source packages under `io.kestra.plugin`:

- `airtable`

Infrastructure dependencies (Docker Compose services):

- `app`

### Key Plugin Classes

- `io.kestra.plugin.airtable.records.Create`
- `io.kestra.plugin.airtable.records.Delete`
- `io.kestra.plugin.airtable.records.Get`
- `io.kestra.plugin.airtable.records.List`
- `io.kestra.plugin.airtable.records.Update`

### Project Structure

```
plugin-airtable/
├── src/main/java/io/kestra/plugin/airtable/records/
├── src/test/java/io/kestra/plugin/airtable/records/
├── build.gradle
└── README.md
```

## References

- https://kestra.io/docs/plugin-developer-guide
- https://kestra.io/docs/plugin-developer-guide/contribution-guidelines
