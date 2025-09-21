<p align="center">
  <a href="https://www.kestra.io">
    <img src="https://kestra.io/banner.png"  alt="Kestra workflow orchestrator" />
  </a>
</p>

<h1 align="center" style="border-bottom: none">
    Event-Driven Declarative Orchestrator
</h1>

<div align="center">
 <a href="https://github.com/kestra-io/kestra/releases"><img src="https://img.shields.io/github/tag-pre/kestra-io/kestra.svg?color=blueviolet" alt="Last Version" /></a>
  <a href="https://github.com/kestra-io/kestra/blob/develop/LICENSE"><img src="https://img.shields.io/github/license/kestra-io/kestra?color=blueviolet" alt="License" /></a>
  <a href="https://github.com/kestra-io/kestra/stargazers"><img src="https://img.shields.io/github/stars/kestra-io/kestra?color=blueviolet&logo=github" alt="Github star" /></a> <br>
<a href="https://kestra.io"><img src="https://img.shields.io/badge/Website-kestra.io-192A4E?color=blueviolet" alt="Kestra infinitely scalable orchestration and scheduling platform"></a>
<a href="https://kestra.io/slack"><img src="https://img.shields.io/badge/Slack-Join%20Community-blueviolet?logo=slack" alt="Slack"></a>
</div>

<br />

<p align="center">
    <a href="https://twitter.com/kestra_io"><img height="25" src="https://kestra.io/twitter.svg" alt="twitter" /></a> &nbsp;
    <a href="https://www.linkedin.com/company/kestra/"><img height="25" src="https://kestra.io/linkedin.svg" alt="linkedin" /></a> &nbsp;
<a href="https://www.youtube.com/@kestra-io"><img height="25" src="https://kestra.io/youtube.svg" alt="youtube" /></a> &nbsp;
</p>

<br />
<p align="center">
    <a href="https://go.kestra.io/video/product-overview" target="_blank">
        <img src="https://kestra.io/startvideo.png" alt="Get started in 4 minutes with Kestra" width="640px" />
    </a>
</p>
<p align="center" style="color:grey;"><i>Get started with Kestra in 4 minutes.</i></p>


# Kestra Airtable Plugin

This plugin provides integration with Airtable bases and tables via REST API for data orchestration and workflow automation.

## Features

- **Complete CRUD Operations**: List, Get, Create, Update, and Delete records
- **Advanced Filtering**: Support for Airtable's `filterByFormula` with complex queries
- **Auto-pagination**: Automatically fetch all pages of results when needed
- **Field Selection**: Choose specific fields to retrieve or update
- **Multiple Output Formats**: FETCH, FETCH_ONE, STORE, and NONE options
- **AI Agent Ready**: Designed for seamless integration with Kestra v1.0 AI Agents
- **Type Safety**: Full support for dynamic property rendering and validation

## Supported Operations

| Operation | Description | Required Parameters |
|-----------|-------------|-------------------|
| `List` | List records with filtering and pagination | `baseId`, `tableId`, `apiKey`, optional: `filterByFormula`, `fields`, `maxRecords` |
| `Get` | Get single record by ID | `baseId`, `tableId`, `recordId`, `apiKey` |
| `Create` | Create new record(s) | `baseId`, `tableId`, `apiKey`, `fields` or `records` |
| `Update` | Update existing record | `baseId`, `tableId`, `recordId`, `apiKey`, `fields` |
| `Delete` | Delete record by ID | `baseId`, `tableId`, `recordId`, `apiKey` |

## Quick Start

### Basic Configuration

All tasks require these basic connection parameters:

```yaml
tasks:
  - id: airtable_task
    type: io.kestra.plugin.airtable.records.List
    baseId: appXXXXXXXXXXXXXX           # Airtable base ID
    tableId: Tasks                      # Table name or ID
    apiKey: "{{ secret('AIRTABLE_API_KEY') }}"  # API key
```

### Example: List High-Priority Tasks

```yaml
id: list_high_priority_tasks
namespace: company.workflows

tasks:
  - id: get_high_priority_tasks
    type: io.kestra.plugin.airtable.records.List
    baseId: "{{ secret('AIRTABLE_BASE_ID') }}"
    tableId: "Tasks"
    apiKey: "{{ secret('AIRTABLE_API_KEY') }}"
    filterByFormula: "AND({Status} != 'Done', {Priority} = 'High')"
    fields: ["Task Name", "Status", "Priority", "Due Date"]
    maxRecords: 50
    enableAutoPagination: true
    fetchType: FETCH
```

### Example: Get Specific Task

```yaml
id: get_task_details
namespace: company.workflows

tasks:
  - id: get_task
    type: io.kestra.plugin.airtable.records.Get
    baseId: "{{ secret('AIRTABLE_BASE_ID') }}"
    tableId: "Tasks"
    recordId: "recXXXXXXXXXXXXXX"
    apiKey: "{{ secret('AIRTABLE_API_KEY') }}"
    fields: ["Task Name", "Status", "Priority", "Assignee"]
```

### Example: Create New Task

```yaml
id: create_new_task
namespace: company.workflows

tasks:
  - id: create_task
    type: io.kestra.plugin.airtable.records.Create
    baseId: "{{ secret('AIRTABLE_BASE_ID') }}"
    tableId: "Tasks"
    apiKey: "{{ secret('AIRTABLE_API_KEY') }}"
    fields:
      "Task Name": "Implement new feature"
      "Status": "Todo"
      "Priority": "High"
      "Due Date": "{{ now() | dateAdd(7, 'DAYS') | date('yyyy-MM-dd') }}"
    typecast: true
```

**Output includes created record ID:**
```json
{
  "recordIds": ["recNEWRECORDIDXXX"],
  "records": [{
    "id": "recNEWRECORDIDXXX",
    "createdTime": "2024-01-15T10:30:00.000Z",
    "fields": {
      "Task Name": "Implement new feature",
      "Status": "Todo",
      "Priority": "High"
    }
  }]
}
```

### Example: Update Task Status

```yaml
id: update_task_status
namespace: company.workflows

tasks:
  - id: update_task
    type: io.kestra.plugin.airtable.records.Update
    baseId: "{{ secret('AIRTABLE_BASE_ID') }}"
    tableId: "Tasks"
    recordId: "recXXXXXXXXXXXXXX"
    apiKey: "{{ secret('AIRTABLE_API_KEY') }}"
    fields:
      "Status": "In Progress"
      "Progress": 50
      "Last Updated": "{{ now() }}"
    typecast: true
```

### Example: Complete CRUD Workflow

This example demonstrates chaining operations using record IDs:

```yaml
id: airtable_crud_workflow
namespace: company.workflows

tasks:
  # Step 1: Create a new customer record
  - id: create_customer
    type: io.kestra.plugin.airtable.records.Create
    baseId: "{{ secret('AIRTABLE_BASE_ID') }}"
    tableId: "Customers"
    apiKey: "{{ secret('AIRTABLE_API_KEY') }}"
    fields:
      "Name": "Acme Corporation"
      "Email": "contact@acme.com"
      "Status": "Prospect"
      "Created Date": "{{ now() | date('yyyy-MM-dd') }}"
    typecast: true

  # Step 2: Update the created customer
  - id: update_customer
    type: io.kestra.plugin.airtable.records.Update
    baseId: "{{ secret('AIRTABLE_BASE_ID') }}"
    tableId: "Customers"
    recordId: "{{ outputs.create_customer.recordIds[0] }}"
    apiKey: "{{ secret('AIRTABLE_API_KEY') }}"
    fields:
      "Phone": "+1-555-0123"
      "Status": "Active"
      "Last Contact": "{{ now() }}"

  # Step 3: Read the updated customer to verify changes
  - id: verify_customer
    type: io.kestra.plugin.airtable.records.Get
    baseId: "{{ secret('AIRTABLE_BASE_ID') }}"
    tableId: "Customers"
    recordId: "{{ outputs.create_customer.recordIds[0] }}"
    apiKey: "{{ secret('AIRTABLE_API_KEY') }}"
    fields: ["Name", "Email", "Phone", "Status", "Last Contact"]

  # Step 4: Clean up - delete the test customer (optional)
  - id: cleanup_customer
    type: io.kestra.plugin.airtable.records.Delete
    baseId: "{{ secret('AIRTABLE_BASE_ID') }}"
    tableId: "Customers"
    recordId: "{{ outputs.create_customer.recordIds[0] }}"
    apiKey: "{{ secret('AIRTABLE_API_KEY') }}"
    runIf: "{{ inputs.cleanup | default(false) }}"
```

## Configuration

### Authentication

All tasks require an Airtable API key for authentication. You can obtain this from your [Airtable account settings](https://airtable.com/create/tokens).

```yaml
apiKey: "{{ secret('AIRTABLE_API_KEY') }}"
```

### Base and Table Identification

- **baseId**: The ID of your Airtable base (starts with `app`)
- **tableId**: The name or ID of the table within the base

You can find these in your Airtable URL: `https://airtable.com/{baseId}/{tableId}/...`

### Filtering

Use Airtable's formula syntax for filtering:

```yaml
filterByFormula: "AND({Status} != 'Done', {Priority} = 'High')"
filterByFormula: "OR({Type} = 'Bug', {Type} = 'Enhancement')"
filterByFormula: "{Due Date} < TODAY()"
```

## AI Agent Integration

This plugin is designed to work seamlessly with Kestra v1.0 AI Agents. Tasks can be used as tools by AI agents to:

- Query Airtable data for decision making
- Create records based on agent analysis
- Update records with processing results
- Delete temporary or outdated records

Future versions may support using Airtable as a knowledge base for AI agents.

## Installation

Add this plugin to your Kestra instance:

```bash
./kestra plugins install io.kestra.plugin:plugin-airtable:LATEST
```

## Development

### Prerequisites
- Java 21+
- Airtable API key for testing

### Building

```bash
./gradlew build
```

### Testing

```bash
# Unit tests
./gradlew test

# Integration tests (requires environment variables)
AIRTABLE_API_KEY=your_key AIRTABLE_BASE_ID=your_base ./gradlew test
```

### Environment Variables Required for Integration Testing

```bash
AIRTABLE_INTEGRATION_TESTS=true
AIRTABLE_API_KEY=your_api_key
AIRTABLE_BASE_ID=your_base_id
AIRTABLE_TABLE_ID=your_table_id  # Optional, defaults to "Table1"
```

The implementation successfully addresses @fdelbrayelle's feedback about using proper RunContext to actually run the tasks, while maintaining security by never committing credentials to the repository. The tests follow the same patterns established in the Odoo plugin and provide comprehensive coverage for all CRUD operations.

### Test Architecture

- **Unit Tests (13 tests)**: Property validation and task building - always run
- **Integration Tests (26 tests)**: Real API calls with credentials - conditional execution
- **CRUD Cycle Tests**: Complete workflow validation with cleanup

### Running tests
```bash
./gradlew check --parallel
```

### Local Development

**VSCode**: Follow the README.md within the `.devcontainer` folder for development setup.

**Other IDEs**:
```bash
./gradlew shadowJar && docker build -t kestra-airtable . && docker run --rm -p 8080:8080 kestra-airtable server local
```

Visit http://localhost:8080, the Airtable plugin tasks will be available to use.

## Documentation
* Full documentation can be found under: [kestra.io/docs](https://kestra.io/docs)
* Documentation for developing a plugin is included in the [Plugin Developer Guide](https://kestra.io/docs/plugin-developer-guide/)

## Contributing

Contributions are welcome! Please ensure:

1. All tests pass
2. Code follows project conventions
3. New features include comprehensive tests
4. Documentation is updated accordingly

## License
Apache 2.0 © [Kestra Technologies](https://kestra.io)

## Stay up to date

We release new versions every month. Give the [main repository](https://github.com/kestra-io/kestra) a star to stay up to date with the latest releases and get notified about future updates.
