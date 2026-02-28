# Drawing2D Container

**File:** `core/drawing2d/src/main/kotlin/com/yourapp/drawing2d/model/Drawing2D.kt`
**Test:** `core/drawing2d/src/test/kotlin/com/yourapp/drawing2d/model/Drawing2DTest.kt`
**Status:** Step 1/3 — minimal version (no Page, Layer, or sync fields yet)

---

## Purpose

`Drawing2D` is the top-level container for all data in a 2-D drawing. It holds
entities (geometry), annotations (metadata overlays), and free-form metadata.

## Fields (Step 1/3)

| Field | Type | Default | Notes |
|-------|------|---------|-------|
| `schemaVersion` | `Int` | `1` | Bumped on breaking schema changes |
| `id` | `String` | required | Unique drawing identifier |
| `name` | `String` | required | Human-readable name |
| `entities` | `List<EntityV1>` | `emptyList()` | Geometry (lines, circles, …) |
| `annotations` | `List<AnnotationV1>` | `emptyList()` | Labels, dimensions, tags, groups |
| `metadata` | `Map<String, String>` | `emptyMap()` | Arbitrary key-value pairs |

**Missing (Step 2):** `page: Page`, `layers: List<Layer>`
**Missing (Step 3):** `syncId: String?`, `syncStatus: String`, `updatedAt: Long`, `version: Int`

## Deterministic Serialization — `toJsonStable()`

### Strategy

1. **Sort collections** by `id` lexicographically before encoding
   — `entities`, `annotations`
2. **Pretty-print** with 2-space indent (`prettyPrint = true`, `prettyPrintIndent = "  "`)
3. **Map key order** is stable by default in `kotlinx.serialization`

### SHA256 Stability Guarantees

- Same `Drawing2D` data → same `toJsonStable()` output → same SHA256 hash
- Input collection order is irrelevant — sorting normalises it
- `@OptIn(ExperimentalSerializationApi::class)` required for `prettyPrintIndent`

### Example

```kotlin
val drawing = Drawing2D(id = "d1", name = "Sketch")
val hash = drawing.toJsonStable().sha256()   // stable across JVM restarts
```

## Architecture Compliance

| Rule | Status |
|------|--------|
| ARCH-MATH-001: No `Float` | ✓ All numeric fields delegate to `EntityV1`/`AnnotationV1` which use `Double` |
