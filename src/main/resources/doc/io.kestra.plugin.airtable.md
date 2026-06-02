# How to use the Airtable plugin

Read and write records in Airtable from Kestra flows.

## Authentication

Set `apiKey` to your Airtable personal access token. Store it in a [secret](https://kestra.io/docs/concepts/secret) and apply it globally with [plugin defaults](https://kestra.io/docs/workflow-components/plugin-defaults).

## Tasks

All tasks require `baseId` and `tableId` to identify the target table.

`records.List` returns records from a table — filter with `filterByFormula`, scope columns with `fields`, cap results with `maxRecords`, or target a specific `view`. Set `enableAutoPagination: true` to fetch all pages automatically. Control result handling with `fetchType` (default `FETCH`).

`records.Create` creates one or more records — pass a single record via `fields` (a map of column name to value) or a batch via `records` (a list of field maps). Set `typecast: true` to let Airtable coerce string values to the appropriate field type.

`records.Get` retrieves a single record by `recordId`. Scope returned columns with `fields`. Set `failOnMissing: true` to error if the record does not exist.

`records.Update` updates a record by `recordId` — pass new values via `fields`.

`records.Delete` removes a record by `recordId`.
