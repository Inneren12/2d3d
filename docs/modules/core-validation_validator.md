# Validation — DrawingValidator

**File:** `core/validation/src/main/kotlin/com/yourapp/validation/DrawingValidator.kt`
**Test:** `core/validation/src/test/kotlin/com/yourapp/validation/DrawingValidatorTest.kt`
**Status:** COMPLETE

---

## Purpose

Validates Drawing2D structure and returns structured violations.

**CRITICAL:** This validator NEVER throws exceptions. All errors
(parse errors, validation errors) are returned as structured
Violation objects.

## Validation Rules

### Schema Version

- Must be exactly `1`
- Returns `Violation.Custom` with ERROR if not

### Hard Limits

| Limit | Max Count | Violation |
|-------|-----------|-----------|
| Entities | 100,000 | "Too many entities: {count} (max: 100000)" |
| Annotations | 100,000 | "Too many annotations: {count} (max: 100000)" |

### Entity Validation

**All entities:**
- ID must not be blank

**Circle:**
- `radius > 0` (must be positive)
- `radius.isFinite()` (not NaN or Infinity)

**Arc:**
- `radius > 0` (must be positive)
- `radius.isFinite()` (not NaN or Infinity)

**Polyline:**
- `points.size >= 2` (minimum 2 points)

> Note: `EntityV1.Circle`, `EntityV1.Arc`, and `EntityV1.Polyline` all enforce
> their geometric constraints via `init` blocks, so invalid objects cannot be
> constructed at runtime. The validator duplicates these checks for
> defense-in-depth and to catch cases where objects may arrive via
> deserialization with custom `Json` configurations.

### Annotation Validation

**All annotations:**
- ID must not be blank

**targetId reference:**
- If not null, must reference existing entity
- Returns `Violation.BrokenReference` if not found

## Methods

### validate(drawing: Drawing2D): List\<Violation\>

Validates a Drawing2D and returns list of violations.

```kotlin
val drawing = Drawing2D(...)
val violations = validator.validate(drawing)

if (violations.isEmpty()) {
    println("Valid!")
} else {
    violations.forEach { println(it.message) }
}
```

### validateSafe(json: String): Result\<Drawing2D\>

Safely parses and validates JSON string.

**CRITICAL:** NEVER throws exceptions. All errors returned as Failure.

Returns `Success` only if:
- JSON parses successfully
- Drawing has zero ERROR-severity violations

```kotlin
val result = validator.validateSafe(jsonString)

result.fold(
    onSuccess = { drawing ->
        println("Valid drawing: ${drawing.id}")
    },
    onFailure = { exception ->
        if (exception is ValidationException) {
            exception.violations.forEach { println(it.message) }
        }
    }
)
```

## ValidationException

Thrown (wrapped in `Result.failure`) when validation fails.

```kotlin
class ValidationException(
    message: String,
    val violations: List<Violation>,
) : Exception(message)
```

## Example Usage

### Validate Existing Drawing

```kotlin
val validator = DrawingValidator()
val violations = validator.validate(drawing)

val errors = violations.filter { it.severity == Severity.ERROR }
if (errors.isNotEmpty()) {
    throw IllegalStateException("Drawing is invalid")
}
```

### Validate JSON from API

```kotlin
fun loadDrawing(json: String): Drawing2D {
    return validator.validateSafe(json).getOrElse {
        if (it is ValidationException) {
            // Show user-friendly errors
            showErrors(it.violations)
        }
        throw it
    }
}
```

### Filter by Severity

```kotlin
val violations = validator.validate(drawing)

val errors = violations.filter { it.severity == Severity.ERROR }
val warnings = violations.filter { it.severity == Severity.WARNING }

println("${errors.size} errors, ${warnings.size} warnings")
```

## Architecture Compliance

| Rule | Status |
|------|--------|
| Never throw on invalid input | ✓ validateSafe() never throws |
| Structured errors | ✓ All violations are typed |
| Coverage >85% | ✓ Verified by tests |
