# Kestra Airtable Plugin

## What

- Provides plugin components under `io.kestra.plugin.airtable`.
- Includes classes such as `AirtableException`, `AirtableRecord`, `AirtableClient`, `AirtableListResponse`.

## Why

- This plugin integrates Kestra with Airtable.
- It provides tasks that connect to Airtable bases and tables over the REST API for workflow automation.

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
