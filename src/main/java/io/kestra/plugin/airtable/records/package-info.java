@PluginSubGroup(
    title = "Airtable Record",
    description = "Tasks that read and write Airtable records: list rows with filters, views, and pagination, fetch a record by ID, create single or batched records (up to 10) with optional typecasting, patch updates, and irreversible deletes. Use API key authentication and choose fetch/store outputs depending on whether you want data in-flow or stored.",
        categories = { PluginSubGroup.PluginCategory.BUSINESS }
)
package io.kestra.plugin.airtable.records;

import io.kestra.core.models.annotations.PluginSubGroup;
