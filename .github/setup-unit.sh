#!/bin/bash

# Test script to verify Airtable API setup
echo "🔍 Verifying Airtable API Setup..."
echo

# Check environment variables
if [ -z "$AIRTABLE_API_KEY" ]; then
    echo "❌ AIRTABLE_API_KEY not set"
    echo "   Set it with: export AIRTABLE_API_KEY='your_token'"
    exit 1
fi

if [ -z "$AIRTABLE_BASE_ID" ]; then
    echo "❌ AIRTABLE_BASE_ID not set"
    echo "   Set it with: export AIRTABLE_BASE_ID='your_base_id'"
    exit 1
fi

echo "✅ Environment variables set"
echo "   API Key: ${AIRTABLE_API_KEY:0:20}..."
echo "   Base ID: $AIRTABLE_BASE_ID"
echo

# Test API access
echo "🌐 Testing API access..."
TABLE_NAME="${AIRTABLE_TABLE_ID:-Table1}"

# Test 1: List base schema (requires schema.bases:read)
echo "📋 Testing base schema access..."
SCHEMA_RESPONSE=$(curl -s -w "%{http_code}" -H "Authorization: Bearer $AIRTABLE_API_KEY" \
    "https://api.airtable.com/v0/meta/bases/$AIRTABLE_BASE_ID/tables")

HTTP_CODE="${SCHEMA_RESPONSE: -3}"
RESPONSE_BODY="${SCHEMA_RESPONSE%???}"

if [ "$HTTP_CODE" = "200" ]; then
    echo "   ✅ Schema access: SUCCESS"
    echo "   📊 Available tables:"
    echo "$RESPONSE_BODY" | jq -r '.tables[]? | "      - " + .name' 2>/dev/null || echo "      (JSON parsing failed - check manually)"
else
    echo "   ❌ Schema access: FAILED (HTTP $HTTP_CODE)"
    echo "   Response: $RESPONSE_BODY"
fi

echo

# Test 2: List records (requires data.records:read)
echo "📖 Testing record read access on table '$TABLE_NAME'..."
RECORDS_RESPONSE=$(curl -s -w "%{http_code}" -H "Authorization: Bearer $AIRTABLE_API_KEY" \
    "https://api.airtable.com/v0/$AIRTABLE_BASE_ID/$TABLE_NAME?maxRecords=1")

HTTP_CODE="${RECORDS_RESPONSE: -3}"
RESPONSE_BODY="${RECORDS_RESPONSE%???}"

if [ "$HTTP_CODE" = "200" ]; then
    echo "   ✅ Record read: SUCCESS"
    RECORD_COUNT=$(echo "$RESPONSE_BODY" | jq -r '.records | length' 2>/dev/null || echo "unknown")
    echo "   📊 Records found: $RECORD_COUNT"
elif [ "$HTTP_CODE" = "404" ]; then
    echo "   ❌ Table '$TABLE_NAME' not found"
    echo "   💡 Either rename your table to '$TABLE_NAME' or set AIRTABLE_TABLE_ID to your actual table name"
else
    echo "   ❌ Record read: FAILED (HTTP $HTTP_CODE)"
    echo "   Response: $RESPONSE_BODY"
fi

echo
echo "🧪 Run integration tests with:"
echo "   export AIRTABLE_INTEGRATION_TESTS=true"
echo "   ./gradlew test"