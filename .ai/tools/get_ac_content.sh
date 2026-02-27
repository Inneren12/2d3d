#!/bin/bash
# Usage: get_ac_content.sh  [task_file]

AC_ID=$1
TASK_FILE=${2:-""}
SPRINT_FILE=$(grep -l "<ac-block id=\"$AC_ID\">" specs/sprints/*.md | head -n 1)

if [ -z "$SPRINT_FILE" ]; then
  echo "ERROR: AC block $AC_ID not found in any sprint file"
  exit 1
fi

# Extract AC block by ID
AC_CONTENT=$(sed -n "/<ac-block id=\"$AC_ID\">/,/<\/ac-block>/p" "$SPRINT_FILE" | \
  sed '1d;$d')  # Remove opening/closing tags

if [ -z "$AC_CONTENT" ]; then
  echo "ERROR: AC block $AC_ID not found"
  exit 1
fi

# Calculate SHA256 (strip CR before hashing for cross-platform CRLF compatibility)
ACTUAL_HASH=$(echo "$AC_CONTENT" | tr -d '\r' | sha256sum | cut -d' ' -f1)

# If task file provided, verify hash
if [ -n "$TASK_FILE" ]; then
  EXPECTED_HASH=$(jq -r '.expected_hash' "$TASK_FILE")

  if [ "$ACTUAL_HASH" != "$EXPECTED_HASH" ]; then
    echo "ERROR: STALE_CONTEXT - AC block has been modified"
    echo "Expected hash: $EXPECTED_HASH"
    echo "Actual hash: $ACTUAL_HASH"
    echo "ACTION REQUIRED: Update task file or review AC changes"
    exit 2
  fi
fi

# Return AC content
echo "$AC_CONTENT"
