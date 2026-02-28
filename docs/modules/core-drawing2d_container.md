# Drawing2D Container

**File:** `core/drawing2d/src/main/kotlin/com/yourapp/drawing2d/model/Drawing2D.kt`
**Test:** `core/drawing2d/src/test/kotlin/com/yourapp/drawing2d/model/Drawing2DTest.kt`
**Status:** COMPLETE — all fields implemented

---

## Purpose

`Drawing2D` is the top-level container for all data in a 2-D drawing. It holds
a page definition, layers for organization, entities (geometry), annotations
(metadata overlays), and free-form metadata.

## Page

`Page` defines the canvas dimensions and unit system.

| Field | Type | Default | Notes |
|-------|------|---------|-------|
| `width` | `Double` | required | Canvas width (ARCH-MATH-001: Double, not Float) |
| `height` | `Double` | required | Canvas height (ARCH-MATH-001: Double, not Float) |
| `units` | `Units` | `Units.MM` | Unit system enum (`MM`, `CM`, `M`, `INCHES`, `FEET`) |

Example: A4 landscape page → `Page(width = 297.0, height = 210.0, units = Units.MM)`

## Layer

`Layer` groups entities for organization and visibility control.

| Field | Type | Default | Notes |
|-------|------|---------|-------|
| `id` | `String` | required | Unique layer identifier |
| `name` | `String` | required | Human-readable layer name |
| `visible` | `Boolean` | `true` | Controls layer visibility |

## Fields (Complete — 12 fields)

| Field | Type | Default | Notes |
|-------|------|---------|-------|
| `schemaVersion` | `Int` | `1` | Bumped on breaking schema changes |
| `id` | `String` | required | Unique drawing identifier |
| `name` | `String` | required | Human-readable name |
| `page` | `Page` | required | Canvas dimensions and unit system |
| `layers` | `List<Layer>` | `emptyList()` | Layer definitions for entity grouping |
| `entities` | `List<EntityV1>` | `emptyList()` | Geometry (lines, circles, …) |
| `annotations` | `List<AnnotationV1>` | `emptyList()` | Labels, dimensions, tags, groups |
| `metadata` | `Map<String, String>` | `emptyMap()` | Arbitrary key-value pairs |
| `syncId` | `String?` | `null` | Cloud sync identifier |
| `syncStatus` | `SyncStatus` | `SyncStatus.LOCAL` | Sync status enum (`LOCAL`, `SYNCING`, `SYNCED`) |
| `updatedAt` | `Long` | `0L` | Timestamp in milliseconds since epoch |
| `version` | `Int` | `1` | Version number for conflict resolution |

## Sync Fields (for Sprint 4.5)

| Field | Type | Default | Notes |
|-------|------|---------|-------|
| `syncId` | `String?` | `null` | Cloud sync identifier |
| `syncStatus` | `SyncStatus` | `SyncStatus.LOCAL` | Sync status enum (`LOCAL`, `SYNCING`, `SYNCED`) |
| `updatedAt` | `Long` | `0L` | Timestamp in milliseconds since epoch |
| `version` | `Int` | `1` | Version number for conflict resolution |

## Deterministic Serialization — `toJsonStable()`

### Strategy

1. **Sort collections** by `id` lexicographically before encoding
   — `layers`, `entities`, `annotations`
2. **Sort metadata map** by key via `toSortedMap()` before encoding
   — guarantees key order regardless of insertion order
3. **Pretty-print** with 2-space indent (`prettyPrint = true`, `prettyPrintIndent = "  "`)

### SHA256 Stability Guarantees

- Same `Drawing2D` data → same `toJsonStable()` output → same SHA256 hash
- Input collection order is irrelevant — sorting normalises it
- `@OptIn(ExperimentalSerializationApi::class)` required for `prettyPrintIndent`

### Example

```kotlin
val drawing = Drawing2D(
    id = "d1",
    name = "Sketch",
    page = Page(width = 297.0, height = 210.0),
)
val hash = drawing.toJsonStable().sha256()   // stable across JVM restarts
```

## Architecture Compliance

| Rule | Status |
|------|--------|
| ARCH-MATH-001: No `Float` | ✓ `Page` uses `Double`; all numeric fields use `Double` |
