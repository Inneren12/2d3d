# Project Development Workflow

This project strictly follows Spec-Driven Development. Please adhere to the following workflow for all tasks.

## 1. Context Gathering (Required First Steps)

Upon receiving a new task file (e.g., a .json file), please use your terminal to run:

```
./.ai/tools/resolve_scope.sh <TASK_FILE>
./.ai/tools/get_ac_content.sh <AC_ID> <TASK_FILE>
cat specs/arch/domain_core.json
```

## 2. Status Check Pause

After gathering the context, output the following to the chat:

- "Allowed Scope: [List of files]"
- "AC Hash Status: [Valid / Stale]"

Then, please pause and wait for the user to reply "PROCEED" before writing any Kotlin code or modifying project files.

## 3. Workflow Rules

- If `get_ac_content.sh` returns a `STALE_CONTEXT` error, stop and ask the user to resolve the hash mismatch.
- If a task requires editing a file outside the allowed scope, stop and request a scope expansion.
- Follow architectural rules strictly (e.g., use `Double` not `Float` per ARCH-MATH-001; use `toLong()` per ARCH-SAFE-001).
