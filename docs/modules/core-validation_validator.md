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

## Geometry Validation

Validates geometric properties of entities to catch NaN and Infinity
before they poison downstream calculations.

### Coordinate Checks

**All entity types:**
- All x, y coordinates must be finite (not NaN, Infinity, -Infinity)
- Returns `InvalidValue` violation for non-finite coordinates

**Circle:**
- `center.x` and `center.y` must be finite
- `radius` must be finite (already checked in entity validation)

**Arc:**
- `center.x` and `center.y` must be finite
- `startAngle` must be finite
- `endAngle` must be finite
- `radius` must be finite (already checked in entity validation)

**Polyline:**
- All `points[i].x` and `points[i].y` must be finite

### Degenerate Geometry Detection

**Line:**
- If length < 1e-6 (1 micrometer), returns WARNING violation
- Message: "Line has zero length (length=X, min=1e-6)"
- Severity: WARNING (not ERROR, as it may be intentional)

**Why check for NaN/Infinity?**

Non-finite values cause silent failures in downstream code:
- Bounding box calculations return NaN/Infinity
- Collision detection fails
- Rendering produces blank output
- Distance calculations propagate NaN

Catching these early prevents debugging nightmares.

### Example: Geometry Validation

```kotlin
val drawing = Drawing2D(
    id = "d1",
    name = "Test",
    page = Page(width = 1000.0, height = 800.0),
    entities = listOf(
        EntityV1.Line(
            id = "line1",
            layer = null,
            start = Point2D(0.0, 0.0),
            end = Point2D(Double.NaN, 100.0),
            style = LineStyle("#000", 1.0, null),
        )
    )
)

val violations = validator.validate(drawing)
// Returns: InvalidValue(path="drawing.entities[0].end.x", constraint="must be finite")
```

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
