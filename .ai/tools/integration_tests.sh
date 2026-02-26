#!/bin/bash
set -e
echo "=== Running Integration Tests ==="
FAILURES=0

# Setup local test directory
mkdir -p .ai/test_data

# Test 1: Verify resolve_scope.sh handles invalid relative paths
cat > .ai/test_data/test1.json << 'EOF'
{"touches": {"include": ["../../some_outside_folder/file.txt"]}}
EOF

OUTPUT=$(./.ai/tools/resolve_scope.sh .ai/test_data/test1.json 2>&1) || true
if echo "$OUTPUT" | grep -q -i "path\|absolute\|invalid"; then
    echo "PASS: Invalid path patterns are rejected correctly."
else
    echo "FAIL: Invalid path patterns were not caught."
    FAILURES=$((FAILURES + 1))
fi

# Test 2: Verify JSON Schema validation catches missing fields
cat > .ai/test_data/test2.json << 'EOF'
{"schema_version": 1, "domain": "core", "rules": [{"rule_id": "BAD", "constraint": "x", "rationale": "y"}]}
EOF

OUTPUT=$(python3 -c "import json, jsonschema; jsonschema.validate(json.load(open('.ai/test_data/test2.json')), json.load(open('specs/arch/schema_rules.json')))" 2>&1) || EXIT_CODE=$?

if [ "${EXIT_CODE:-0}" -ne 0 ]; then
    echo "PASS: Schema validation caught the missing title field."
else
    echo "FAIL: Schema validation let bad data through."
    FAILURES=$((FAILURES + 1))
fi

# Cleanup
rm -rf .ai/test_data

if [ $FAILURES -eq 0 ]; then
    echo "ALL TESTS PASSED"
    exit 0
else
    echo "$FAILURES TEST(S) FAILED"
    exit 1
fi
