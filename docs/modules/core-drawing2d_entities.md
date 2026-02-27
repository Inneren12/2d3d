# Knowledge Base: `core/drawing2d` — Entity Types

> **Audience**: External RAG system (NotebookLM). This document is written for semantic search and question-answering. It contains detailed explanations of design decisions.

---

## Overview

Sprint 1, PR3 introduced the core entity type hierarchy for the 2-D drawing engine. All drawing objects (lines, circles, polylines, arcs) are represented as concrete subclasses of the `EntityV1` sealed class, located in:

```
core/drawing2d/src/main/kotlin/com/yourapp/drawing2d/model/Entity.kt
```

---

## `LineStyle` — Stroke / Fill Properties

```kotlin
@Serializable
data class LineStyle(
    val color: String,
    val width: Double,
    val dashPattern: List<Double>? = null
)
```

`LineStyle` is a plain immutable data class that describes how an entity is rendered.

- `color` is a CSS hex string (e.g. `"#FF0000"`). Validation is intentionally deferred to the rendering layer so the domain model stays pure.
- `width` is `Double` per **ARCH-MATH-001** — `Float` is forbidden in domain code to prevent coordinate precision loss on large drawings (>250 m).
- `dashPattern` is `null` for a solid stroke; a non-null list alternates between "drawn" and "gap" segment lengths (following the SVG dash-array convention).

---

## `EntityV1` — Sealed Class Hierarchy

```kotlin
@Serializable
sealed class EntityV1 {
    abstract val id: String
    abstract val layer: String?
    // subclasses: Line, Circle, Polyline, Arc
}
```

### Why a Sealed Class?

A `sealed class` was chosen over an `interface` or `abstract class` for two reasons:

1. **Exhaustive `when` expressions.** Consumers (e.g. renderers, serializers, merge engines) can `when (entity)` without an `else` branch and the compiler will warn on missed cases when new subclasses are added.
2. **Value semantics via `data class` subclasses.** Each subclass is a `data class`, providing structural equality, copy, and `toString` for free — critical for deterministic diffing and three-way merge.

### Common Abstract Fields

- `id: String` — a UUID or user-assigned stable identifier. Used as the key in merge operations (see ARCH-PERF-001 for O(N) merge rules).
- `layer: String?` — nullable layer name. `null` means the entity is on the default/unnamed layer.

---

## `EntityV1.Line`

```kotlin
@Serializable
@SerialName("line")
data class Line(
    override val id: String,
    override val layer: String? = null,
    val start: Point2D,
    val end: Point2D,
    val style: LineStyle
) : EntityV1()
```

A straight line segment from `start` to `end`. Both endpoints are `Point2D` (Double-precision, see `Primitives.kt`). There is no `init` validation on `Line` because any two distinct (or even identical) points form a degenerate but geometrically representable line segment — degeneracy is handled by the renderer.

---

## `EntityV1.Circle`

```kotlin
@Serializable
@SerialName("circle")
data class Circle(
    override val id: String,
    override val layer: String? = null,
    val center: Point2D,
    val radius: Double,
    val style: LineStyle
) : EntityV1() {
    init {
        require(radius > 0 && radius.isFinite()) {
            "Circle radius must be finite and > 0, but was $radius"
        }
    }
}
```

### Why `init` Block for Validation?

The `init` block is used instead of factory functions or external validators because:

- It fires on **every construction path**, including deserialization via kotlinx.serialization. This means a corrupted JSON document cannot produce an invalid `Circle` object in memory.
- It keeps the invariant co-located with the data — no separate validator to forget.
- It throws `IllegalArgumentException` (via `require`), which is the Kotlin convention for precondition failures, distinct from `IllegalStateException` (postcondition failures).

### Geometric Constraints

| Condition | Reason |
|-----------|--------|
| `radius > 0` | A zero or negative radius has no geometric meaning for a circle |
| `radius.isFinite()` | `Double.POSITIVE_INFINITY`, `Double.NEGATIVE_INFINITY`, and `Double.NaN` would produce silent rendering errors or crashes in downstream math (e.g. bounding box calculation) |

---

## `EntityV1.Polyline`

```kotlin
@Serializable
@SerialName("polyline")
data class Polyline(
    override val id: String,
    override val layer: String? = null,
    val points: List<Point2D>,
    val closed: Boolean = false,
    val style: LineStyle
) : EntityV1() {
    init {
        require(points.size >= 2) {
            "Polyline must have at least 2 points"
        }
    }
}
```

### Geometric Constraints

A single-point "polyline" is a degenerate case with no line segments to draw. Requiring `size >= 2` ensures downstream iterators (e.g. `points.zipWithNext { a, b -> drawSegment(a, b) }`) always produce at least one segment.

### `closed` Flag

When `closed = true`, the renderer is expected to draw an implicit final segment from `points.last()` back to `points.first()`, completing the polygon. This is not stored as an extra point to avoid duplication in the data model.

---

## `EntityV1.Arc`

```kotlin
@Serializable
@SerialName("arc")
data class Arc(
    override val id: String,
    override val layer: String? = null,
    val center: Point2D,
    val radius: Double,
    val startAngle: Double,
    val endAngle: Double,
    val style: LineStyle
) : EntityV1() {
    init {
        require(radius > 0 && radius.isFinite()) { "Arc radius must be finite and > 0, but was $radius" }
        require(startAngle.isFinite()) { "Arc startAngle must be finite, but was $startAngle" }
        require(endAngle.isFinite()) { "Arc endAngle must be finite, but was $endAngle" }
    }
}
```

### Angle Convention

Angles are stored in **degrees**, measured counter-clockwise from the positive X axis. This matches CAD and SVG conventions and avoids confusion with the radians convention used internally by `kotlin.math`. Conversion to radians happens at the rendering layer only.

### Geometric Constraints

- `radius` carries the same `> 0 && isFinite()` constraint as `Circle.radius`.
- `startAngle` and `endAngle` must both be finite. `NaN` or `Infinity` would cause silent rendering errors in arc parametric calculations.
- Note: `startAngle == endAngle` is valid and represents a full circle drawn as an arc — this is intentional and used by some import formats.

---

## Polymorphic Serialization with kotlinx.serialization

### How It Works

Each subclass is annotated with `@SerialName("line")`, `@SerialName("circle")`, etc. The sealed class is annotated with `@Serializable`. kotlinx.serialization automatically uses the `type` discriminator field (default name `"type"`) to encode/decode the correct subclass.

**Example JSON for a Circle:**
```json
{
  "type": "circle",
  "id": "c1",
  "layer": "foundation",
  "center": { "x": 0.0, "y": 0.0 },
  "radius": 5.0,
  "style": { "color": "#000000", "width": 1.0, "dashPattern": null }
}
```

### Why `@SerialName` Instead of Class Name?

Using short, stable discriminator strings (`"line"`, `"circle"`) instead of fully-qualified class names (`"com.yourapp.drawing2d.model.EntityV1.Line"`) means:
- JSON documents remain stable if the package or class is renamed during refactoring.
- Serialized data on-disk or in the database does not break after code moves.

### Deserialization and `init` Block Interaction

kotlinx.serialization constructs objects via their primary constructor. The `init` block runs as part of construction. This means **invalid data in a JSON document will throw `IllegalArgumentException` during `Json.decodeFromString<EntityV1>(...)`**, not silently produce a corrupt object. This is the desired behavior.

---

## Test Coverage

File: `core/drawing2d/src/test/kotlin/com/yourapp/drawing2d/model/EntityTest.kt`

The test suite uses **Kotest FunSpec style** (consistent with `PrimitivesTest`) and covers:

| Category | Count |
|----------|-------|
| `LineStyle` construction, equality, serialization | 4 |
| `EntityV1.Line` construction, layer, serialization, discriminator | 5 |
| `EntityV1.Circle` invalid radius (negative, zero, Inf, -Inf, NaN) | 5 |
| `EntityV1.Circle` valid construction, layer, serialization, discriminator | 4 |
| `EntityV1.Polyline` invalid (1 pt, 0 pts) with exact error message | 2 |
| `EntityV1.Polyline` valid (2 pts, 3+ pts, closed flag, layer, serialization, discriminator) | 7 |
| `EntityV1.Arc` invalid (neg radius, zero, Inf, NaN, non-finite angles) | 7 |
| `EntityV1.Arc` valid (construction, layer, full-circle, serialization, discriminator) | 5 |
| Polymorphic mixed-list round-trip | 1 |
| **Total** | **39** |

AC-mandated tests explicitly verified:
- Circle `radius = -5.0` → `IllegalArgumentException` containing `"-5.0"` ✅
- Circle `radius = Double.POSITIVE_INFINITY` → `IllegalArgumentException` containing `"Infinity"` ✅
- Polyline with 1 point → `IllegalArgumentException` with message `"Polyline must have at least 2 points"` ✅
- Entity serialization round-trip preserves all data ✅

---

## Architectural Compliance

| Rule | Status |
|------|--------|
| ARCH-MATH-001: All coordinates and widths are `Double`, no `Float` | ✅ Compliant |
| ARCH-SAFE-001: No `roundToInt()` used | ✅ N/A to this file |
| Serialization: `@Serializable` + `@SerialName` on all subclasses | ✅ |
| Validation: `init` blocks prevent invalid object construction | ✅ |
