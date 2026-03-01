# Validation — Violation Model

**File:** `core/validation/src/main/kotlin/com/yourapp/validation/Violation.kt`
**Test:** `core/validation/src/test/kotlin/com/yourapp/validation/ViolationTest.kt`
**Status:** COMPLETE

---

## Purpose

Structured error types for validation failures. Instead of throwing
exceptions with string messages, we return typed `Violation` objects
which can be serialized, analyzed programmatically, and presented
with clear, actionable messages.

## Severity Levels

| Level | Meaning | Action Required |
|-------|---------|-----------------|
| `ERROR` | Fatal - operation cannot proceed | Must fix |
| `WARNING` | Potential problem | Review recommended |
| `INFO` | Informational note | No action needed |

## Violation Types

### MissingField

A required field is missing.

```kotlin
Violation.MissingField(
    path = "drawing.entities[0]",
    fieldName = "id"
)
// Message: "Missing required field: id"
```

### InvalidValue

A field has an invalid value.

```kotlin
Violation.InvalidValue(
    path = "drawing.entities[0]",
    fieldName = "radius",
    value = "-5.0",
    constraint = "must be positive"
)
// Message: "Invalid value for radius: '-5.0' (must be positive)"
```

### BrokenReference

A reference to another entity doesn't exist.

```kotlin
Violation.BrokenReference(
    path = "drawing.annotations[0]",
    referenceId = "entity-123",
    targetType = "Entity"
)
// Message: "Reference entity-123 to Entity not found"
```

### Custom

Domain-specific validation rule.

```kotlin
Violation.Custom(
    path = "drawing",
    severity = Severity.WARNING,
    message = "Drawing has no scale information"
)
```

## Example Usage

```kotlin
fun validateCircle(circle: EntityV1.Circle): List<Violation> {
    val violations = mutableListOf<Violation>()

    if (circle.radius <= 0) {
        violations.add(
            Violation.InvalidValue(
                path = "entities[${circle.id}].radius",
                fieldName = "radius",
                value = circle.radius.toString(),
                constraint = "must be positive"
            )
        )
    }

    if (!circle.radius.isFinite()) {
        violations.add(
            Violation.InvalidValue(
                path = "entities[${circle.id}].radius",
                fieldName = "radius",
                value = circle.radius.toString(),
                constraint = "must be finite"
            )
        )
    }

    return violations
}
```

## Serialization

All violations are serializable to JSON with clean discriminators:

```json
{
  "type": "missing_field",
  "path": "drawing",
  "fieldName": "id"
}
```

**Discriminator values** (via `@SerialName`):
- `missing_field` → MissingField
- `invalid_value` → InvalidValue
- `broken_reference` → BrokenReference
- `custom` → Custom

This ensures:
- Clean external API (no FQCN like `com.yourapp.validation.Violation.MissingField`)
- Stable JSON format across refactoring
- Language-agnostic discriminators

```kotlin
val violations = listOf(
    Violation.MissingField("drawing", "id"),
    Violation.InvalidValue("circle", "radius", "-5", "must be positive")
)

val json = Json.encodeToString(violations)
// [
//   {"type":"missing_field","path":"drawing","fieldName":"id"},
//   {"type":"invalid_value","path":"circle","fieldName":"radius","value":"-5","constraint":"must be positive"}
// ]
```

## JSON Stability

Using `@SerialName` protects against breaking changes:

**Without @SerialName (BAD):**
```json
{
  "type": "com.yourapp.validation.Violation.MissingField",
  ...
}
```
- Breaks when package renamed
- Ugly for external APIs
- Tightly coupled to Kotlin implementation

**With @SerialName (GOOD):**
```json
{
  "type": "missing_field",
  ...
}
```
- Stable across refactoring
- Clean external API
- Language-agnostic

## Architecture Compliance

| Rule | Status |
|------|--------|
| No Android dependencies | ✓ Pure JVM module |
| Serializable | ✓ All types use @Serializable |
| Coverage >90% (line) | ✓ 100% line coverage verified by tests |
