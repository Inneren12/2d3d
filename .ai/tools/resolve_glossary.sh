#!/usr/bin/env bash
# Managed by AI Architecture. Resolves glossary scopes and prevents conflicts.
if [ "$#" -eq 0 ]; then
  echo "Usage: resolve_glossary.sh <scope1> <scope2> ..."
  exit 1
fi

python -c "
import json, sys, os
scopes = sys.argv[1:]
if 'global' not in scopes:
    scopes.insert(0, 'global')

merged_terms = {}
active_markdown = '# Active Glossary\n\n'

for scope in scopes:
    filepath = f'specs/glossary/{scope}.json'
    if not os.path.exists(filepath):
        print(f'Error: Scope file {filepath} not found.', file=sys.stderr)
        sys.exit(1)
    with open(filepath, 'r') as f:
        data = json.load(f)
    for term, details in data.get('terms', {}).items():
        # Check for explicit conflicts
        conflicts = details.get('conflicts_with', [])
        for conflict in conflicts:
            conflict_scope, conflict_term = conflict.split('.')
            if conflict_scope in scopes and conflict_term in merged_terms:
                print(f'CRITICAL ERROR: Terminology conflict detected between {scope}.{term} and {conflict}. Aborting.', file=sys.stderr)
                sys.exit(1)
        merged_terms[term] = details
        active_markdown += f'## {term} ({scope})\n{details.get(\"definition\", \"\")}\n\n'

with open('.ai/ACTIVE_GLOSSARY.md', 'w') as f:
    f.write(active_markdown)
print('SUCCESS: .ai/ACTIVE_GLOSSARY.md generated.')
" "$@"
exit $?
