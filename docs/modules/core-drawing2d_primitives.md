# Module: core:drawing2d — Primitives & Math Utilities

This document describes the foundational types and math utilities introduced in
Sprint 1 (PR-1.5 and PR-2) of the `core:drawing2d` module.

---

## MathUtils (`com.yourapp.drawing2d.math.MathUtils`)

`MathUtils` is a Kotlin `object` (singleton) that provides deterministic,
overflow-safe rounding for `Double` values.

### Architectural constraints

| Rule | Requirement |
|------|-------------|
| ARCH-MATH-001 | All numeric coordinates must be `Double`; `Float` is forbidden. |
| ARCH-SAFE-001 | Rounding must use `.toLong()` scaling, not `roundToInt()`, to avoid integer overflow on large coordinate values. |

### `round(value: Double, decimals: Int): Double`

Rounds `value` to `decimals` decimal places (default: `Precision.DEFAULT_DECIMALS`).

**Implementation approach** — Instead of calling `Math.round()` or
`roundToInt()` (which operate in `Int` range and overflow above ±2 147 483 647),
`round` multiplies by the appropriate power of ten, adds ±0.5, converts to
`Long`, and divides back:

```
(value * factor + sign).toLong().toDouble() / factor
```

This is safe for `abs(value) < 1e9`, which is enforced by a precondition.

**Guards / throws `IllegalArgumentException` when:**
- `decimals` is outside `0..10`
- `abs(value) >= 1e9`

The power-of-ten factor is computed via a `when` lookup table (`tenPow`) rather
than `Math.pow()`, avoiding floating-point inaccuracies in the scaling factor
for the valid range.

### `roundSafe(value: Double, decimals: Int): Double`

A non-throwing wrapper around `round`. If either guard condition fails it
returns the original `value` unchanged, making it suitable for best-effort
display formatting where overflow would be unexpected but not fatal.

---

## Point2D (`com.yourapp.drawing2d.model.Point2D`)

An immutable, `@Serializable` data class representing a 2-D coordinate.

```kotlin
data class Point2D(val x: Double, val y: Double)
```

Both fields are `Double` per ARCH-MATH-001.

### Operators

| Operator | Behaviour |
|----------|-----------|
| `+` (Point2D) | Component-wise addition |
| `-` (Point2D) | Component-wise subtraction |
| `*` (Double scalar) | Uniform scaling |

### `distanceTo(other: Point2D): Double`

Returns the Euclidean distance `sqrt((x2-x1)² + (y2-y1)²)`.

### `toJsonSafe(): Point2D`

Returns a copy of the point with both coordinates rounded to **4 decimal
places** via `MathUtils.round(_, 4)`.

**Note:** This method is automatically called during serialization via
a custom `KSerializer`. You don't need to call it manually — all
serialized Point2D instances are automatically rounded to 4 decimals.

Example:
```kotlin
val point = Point2D(1.123456789, 2.987654321)
val json = Json.encodeToString(point)
// JSON: {"x": 1.1235, "y": 2.9877}  ← Automatically rounded
```

**Why 4 decimals?** Serialization formats such as JSON do not guarantee
round-trip fidelity for arbitrary `Double` values. By rounding to a fixed
precision before serialisation, two `Point2D` instances that are geometrically
equivalent (within sub-micron tolerance for typical canvas coordinates) will
always produce identical JSON output, making file diffs and hash comparisons
deterministic.

### Custom Serializer

Point2D uses a custom `KSerializer` (`Point2DSerializer`) that ensures:
1. **Automatic rounding:** Coordinates rounded to 4 decimals during serialization
2. **Deterministic JSON:** Same Point2D always produces same JSON
3. **Size optimization:** No excessive decimal places in JSON
4. **Cross-platform:** Consistent output on ARM/x86/JVM/Android

This is transparent to users — just call `Json.encodeToString(point)`.

---

## Vector2D (`com.yourapp.drawing2d.model.Vector2D`)

An immutable, `@Serializable` data class representing a 2-D
direction/displacement.

```kotlin
data class Vector2D(val x: Double, val y: Double)
```

Both fields are `Double` per ARCH-MATH-001.

### Operators

| Operator | Behaviour |
|----------|-----------|
| `+` (Vector2D) | Component-wise addition |
| `-` (Vector2D) | Component-wise subtraction |
| `*` (Double scalar) | Uniform scaling |

### `length(): Double`

Returns the Euclidean magnitude `sqrt(x² + y²)`.

### `toJsonSafe(): Vector2D`

Same rationale as `Point2D.toJsonSafe()`: returns a copy with both components
rounded to 4 decimal places via `MathUtils.round(_, 4)` to guarantee
deterministic serialization.

---

## Source locations

| Artifact | Path |
|----------|------|
| `MathUtils` | `core/drawing2d/src/main/kotlin/com/yourapp/drawing2d/math/MathUtils.kt` |
| `Point2D`, `Vector2D` | `core/drawing2d/src/main/kotlin/com/yourapp/drawing2d/model/Primitives.kt` |
| `Precision` constants | `core/domain/src/main/kotlin/com/yourapp/domain/Precision.kt` |
| Unit tests | `core/drawing2d/src/test/kotlin/com/yourapp/drawing2d/math/MathUtilsTest.kt` |
| | `core/drawing2d/src/test/kotlin/com/yourapp/drawing2d/model/PrimitivesTest.kt` |
