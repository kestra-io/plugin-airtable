# Kestra Airtable Plugin

## What

description = 'Airtable plugin for Kestra Exposes 5 plugin components (tasks, triggers, and/or conditions).

## Why

Enables Kestra workflows to interact with Airtable, allowing orchestration of Airtable-based operations as part of data pipelines and automation workflows.

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

### Important Commands

```bash
# Build the plugin
./gradlew shadowJar

# Run tests
./gradlew test

# Build without tests
./gradlew shadowJar -x test
```

### Configuration

All tasks and triggers accept standard Kestra plugin properties. Credentials should use
`{{ secret('SECRET_NAME') }}` — never hardcode real values.

## Agents

**IMPORTANT:** This is a Kestra plugin repository (prefixed by `plugin-`, `storage-`, or `secret-`). You **MUST** delegate all coding tasks to the `kestra-plugin-developer` agent. Do NOT implement code changes directly — always use this agent.
