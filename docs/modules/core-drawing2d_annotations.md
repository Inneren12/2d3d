# core-drawing2d: AnnotationV1 — Metadata Layer

> **NotebookLM RAG target.** Do not read this file during PR tasks.

## Purpose

Annotations form the metadata layer over drawing entities — labels, measurements,
categorical tags, and logical groupings. They are parallel to `EntityV1` in the
sealed-class hierarchy but carry descriptive information rather than geometry.

## Architecture

`AnnotationV1` is a sealed class hierarchy. Each subclass uses a `@SerialName`
discriminator for polymorphic JSON serialization, mirroring the `EntityV1` pattern.

```
AnnotationV1 (sealed)
├── Text        — free-form labels, targetId nullable
├── Dimension   — measured values with units, targetId required
├── Tag         — categorical metadata, targetId required
└── Group       — logical entity groupings, targetId nullable
```

## Why targetId is nullable for some types

| Subclass    | `targetId` | Reason |
|-------------|-----------|--------|
| `Text`      | `String?` | Labels may float independently or reference an entity |
| `Dimension` | `String`  | A measurement must always reference a specific entity |
| `Tag`       | `String`  | A tag is meaningless without an entity to categorize |
| `Group`     | `String?` | Groups may be standalone or nested under a parent entity |

## Key Constraints

- **Dimension value >= 0**: Negative measurements are physically invalid.
  Message: `"Dimension value must be >= 0, but was $value"`
- **Group memberIds non-empty**: A group with zero members is meaningless.
  Message: `"Group must have at least 1 member"`
- **All numeric fields use Double** per ARCH-MATH-001 (no Float).

## Implementation Details

### AnnotationV1.Text
Free-form text labels positioned in drawing space. `fontSize` and `rotation`
are `Double`. No validation on `fontSize` or `rotation` — deferred to renderer.

### AnnotationV1.Dimension
Measured values with `Units` enum (metric + imperial). Always references a
drawing entity via non-nullable `targetId`. Validates `value >= 0` in `init`.

### AnnotationV1.Tag
Categorical metadata (e.g., "structural", "electrical"). `category` is optional
(`String?`). Always references an entity via non-nullable `targetId`.

### AnnotationV1.Group
Logical grouping of entities. `memberIds: List<String>` must be non-empty.
`targetId` is nullable — groups may be standalone or associated with a parent.

## Units Enum

```kotlin
enum class Units { MM, CM, M, INCHES, FEET }
```

Serialized as plain enum name strings. Supports metric (MM, CM, M) and
imperial (INCHES, FEET) measurements.

## Serialization

- All types annotated with `@Serializable`
- Subclasses use `@SerialName` discriminators: `"text"`, `"dimension"`, `"tag"`, `"group"`
- JSON discriminator field: `"type"` (kotlinx.serialization default)
- `targetId = null` serializes as `"targetId": null` (never omitted — no default value)

## Usage Examples

```kotlin
// Text label (standalone)
AnnotationV1.Text(
    id = "label1",
    targetId = null,
    position = Point2D(100.0, 200.0),
    content = "Main entrance",
    fontSize = 12.0,
    rotation = 0.0,
)

// Dimension measurement
AnnotationV1.Dimension(
    id = "dim1",
    targetId = "wall1",  // references entity
    value = 2500.0,
    units = Units.MM,
    position = Point2D(150.0, 300.0),
)

// Tag for categorization
AnnotationV1.Tag(
    id = "tag1",
    targetId = "beam1",
    label = "load-bearing",
    category = "structural",
)

// Group for organization
AnnotationV1.Group(
    id = "group1",
    targetId = null,
    name = "First floor walls",
    memberIds = listOf("wall1", "wall2", "wall3"),
)
```

## Common Errors

- `"Dimension value must be >= 0, but was $value"` → Check measurement value is non-negative
- `"Group must have at least 1 member"` → Ensure `memberIds` list is not empty

## Validation

`init` blocks enforce constraints at construction time. Invalid objects cannot
be created — exceptions are thrown before the object is ever returned.

## File Locations

| Symbol | File |
|--------|------|
| `AnnotationV1` (sealed base) | `core/drawing2d/src/main/kotlin/com/yourapp/drawing2d/model/Annotation.kt` |
| `AnnotationV1.Text` | `core/drawing2d/src/main/kotlin/com/yourapp/drawing2d/model/Annotation.kt` |
| `AnnotationV1.Dimension` | `core/drawing2d/src/main/kotlin/com/yourapp/drawing2d/model/Annotation.kt` |
| `AnnotationV1.Tag` | `core/drawing2d/src/main/kotlin/com/yourapp/drawing2d/model/Annotation.kt` |
| `AnnotationV1.Group` | `core/drawing2d/src/main/kotlin/com/yourapp/drawing2d/model/Annotation.kt` |
| `Units` (enum) | `core/drawing2d/src/main/kotlin/com/yourapp/drawing2d/model/Annotation.kt` |

| Test Class | File |
|------------|------|
| `AnnotationTest` | `core/drawing2d/src/test/kotlin/com/yourapp/drawing2d/model/AnnotationTest.kt` |
