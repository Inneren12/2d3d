# Sprint 1: Foundation - Drawing2D Model + Storage + Validation

**Status:** Ready
**Dates:** 2026-02-26 — 2026-03-19
**Duration:** 3 weeks
**Team:** 2 backend/domain developers
**Goal:** Establish pure JVM core modules with deterministic serialization, atomic storage, and typed validation. Zero Android dependencies.

**Key Deliverables:**
- `core-drawing2d` module (Pure JVM/Kotlin)
- `core-storage` module (Atomic file operations)
- `core-validation` module (Typed error reporting)
- Documentation: `DRAWING2D_V1.md`, `NUMERIC_PRECISION.md`, `SCHEMA_EVOLUTION.md`
- 50+ unit tests, 10+ golden fixtures
- Code coverage >80%

---

## Pack A: Core Module Setup

### PR-1: Module `core-drawing2d` (Gradle Setup)

**Context (Human):** Set up the foundational pure JVM module. This is critical - NO Android dependencies allowed. This module must compile and test on a server without Android SDK.

<ac-block id="S1-PR1-AC1">
**Acceptance Criteria for PR1 (Module Setup)**:
- [ ] Create module `core-drawing2d` with `build.gradle.kts`
- [ ] Module compiles successfully: `./gradlew :core-drawing2d:build`
- [ ] Empty test suite runs: `./gradlew :core-drawing2d:test`
- [ ] NO `android.*` imports anywhere in module (verified by grep)
- [ ] Dependencies: `kotlinx-serialization-json`, `junit5`, `kotest`
- [ ] Create `README.md` explaining module purpose
- [ ] Module structure: `/src/main/kotlin/` and `/src/test/kotlin/`
</ac-block>

---

### PR-1.5: MathUtils with Deterministic Rounding

**Context (Human):** We need deterministic math so coordinates don't drift on different processors (ARM vs x86). This is absolutely critical for golden test stability. Use Double only, never Float. The `roundToInt()` function causes Integer Overflow for values >250m, so we use `toLong()` instead.

**Architectural Rules Applied:**
- ARCH-MATH-001: Double only (Float forbidden until rendering)
- ARCH-SAFE-001: Use `toLong()` not `roundToInt()` to prevent overflow

<ac-block id="S1-PR1.5-AC1">
**Acceptance Criteria for PR1.5 (MathUtils)**:
- [ ] Implement `fun round(value: Double, decimals: Int): Double` using `(value * factor).toLong().toDouble() / factor`
- [ ] Implement `fun roundSafe(value: Double, decimals: Int): Double` that returns original value on error (never throws)
- [ ] Guard: `require(decimals in 0..10)` with clear error message
- [ ] Guard: `require(abs(value) < 1e9)` to prevent overflow with message "Value too large for safe rounding: {value} (max: 1e9)"
- [ ] NO `Float` types used anywhere (ARCH-MATH-001 compliance)
- [ ] NO `roundToInt()` usage (ARCH-SAFE-001 compliance)
- [ ] Test: `round(1.123456789, 4) === 1.1235` (exact match)
- [ ] Test: `round(250000.0, 4) === 250000.0` (large value handled)
- [ ] Test: `round(1e10, 4)` throws `IllegalArgumentException` with clear message
- [ ] Test: `roundSafe(1e10, 4) === 1e10` (returns original, no throw)
- [ ] Test: Determinism - calling `round()` twice with same input produces identical output
- [ ] Code coverage >95% for `MathUtils.kt`
- [ ] File location: `core/drawing2d/src/main/kotlin/com/yourapp/drawing2d/math/MathUtils.kt`
</ac-block>

---

### PR-2: Basic Types (Point2D, Vector2D) - Double Precision

**Context (Human):** Create fundamental 2D geometry types. These use Double internally (ARCH-MATH-001) and will be the building blocks for all drawing operations. The `toJsonSafe()` method ensures coordinates are rounded deterministically before serialization.

<ac-block id="S1-PR2-AC1">
**Acceptance Criteria for PR2 (Basic Types)**:
- [ ] Create `data class Point2D(val x: Double, val y: Double)` with serialization
- [ ] Create `data class Vector2D(val x: Double, val y: Double)` (if needed for Sprint 1)
- [ ] Implement `Point2D.distanceTo(other: Point2D): Double` using Pythagorean theorem
- [ ] Implement `Point2D.toJsonSafe(): Point2D` that returns copy with coordinates rounded to 4 decimals via `MathUtils.round()`
- [ ] Implement operator `Point2D.plus(other: Point2D): Point2D`
- [ ] Implement operator `Point2D.minus(other: Point2D): Point2D`
- [ ] Implement operator `Point2D.times(scalar: Double): Point2D`
- [ ] ALL coordinate fields use `Double` type (NOT `Float`) - ARCH-MATH-001
- [ ] Test: `Point2D(0.0, 0.0).distanceTo(Point2D(3.0, 4.0)) === 5.0`
- [ ] Test: `Point2D(1.123456789, 2.987654321).toJsonSafe()` produces `Point2D(1.1235, 2.9877)`
- [ ] Test: Arithmetic operations work correctly
- [ ] Code coverage >90%
- [ ] File location: `core/drawing2d/src/main/kotlin/com/yourapp/drawing2d/model/Primitives.kt`
</ac-block>

---

### PR-3: Entity Types (Line, Circle, Polyline, Arc)

**Context (Human):** Define geometric primitives for the drawing. Each entity must validate itself (positive radius, minimum points, etc.). All coordinates use Double and get rounded via MathUtils when serialized.

<ac-block id="S1-PR3-AC1">
**Acceptance Criteria for PR3 (Entity Types)**:
- [ ] Create sealed class `EntityV1` with abstract fields: `id: String`, `layer: String?`
- [ ] Create `EntityV1.Line(id, layer?, start: Point2D, end: Point2D, style: LineStyle)`
- [ ] Create `EntityV1.Circle(id, layer?, center: Point2D, radius: Double, style: LineStyle)` with `init` block requiring `radius > 0 && radius.isFinite()`
- [ ] Create `EntityV1.Polyline(id, layer?, points: List<Point2D>, closed: Boolean, style: LineStyle)` with `init` block requiring `points.size >= 2`
- [ ] Create `EntityV1.Arc(id, layer?, center: Point2D, radius: Double, startAngle: Double, endAngle: Double, style: LineStyle)` with validation
- [ ] Create `data class LineStyle(color: String, width: Double, dashPattern: List<Double>?)`
- [ ] All entities serialize/deserialize via kotlinx.serialization
- [ ] Test: Creating Circle with `radius = -5.0` throws `IllegalArgumentException`
- [ ] Test: Creating Circle with `radius = Double.POSITIVE_INFINITY` throws `IllegalArgumentException`
- [ ] Test: Creating Polyline with 1 point throws `IllegalArgumentException` with message "Polyline must have at least 2 points"
- [ ] Test: Entity serialization round-trip preserves all data
- [ ] Code coverage >85%
- [ ] File location: `core/drawing2d/src/main/kotlin/com/yourapp/drawing2d/model/Entity.kt`
</ac-block>

---

### PR-4: Annotation Types (Text, Dimension, Tag, Group)

**Context (Human):** Metadata entities that reference other entities. These don't affect geometry but store information about the drawing (labels, measurements, organization).

<ac-block id="S1-PR4-AC1">
**Acceptance Criteria for PR4 (Annotations)**:
- [ ] Create sealed class `AnnotationV1` with abstract fields: `id: String`, `targetId: String?`
- [ ] Create `AnnotationV1.Text(id, targetId?, position: Point2D, content: String, fontSize: Double, rotation: Double)`
- [ ] Create `AnnotationV1.Dimension(id, targetId: String, value: Double, units: Units, position: Point2D)` with `init` requiring `value >= 0`
- [ ] Create `AnnotationV1.Tag(id, targetId: String, label: String, category: String?)`
- [ ] Create `AnnotationV1.Group(id, targetId?, name: String, memberIds: List<String>)` with `init` requiring `memberIds.isNotEmpty()`
- [ ] Test: Creating Dimension with negative value throws `IllegalArgumentException`
- [ ] Test: Creating Group with empty memberIds list throws `IllegalArgumentException` with message "Group must have at least 1 member"
- [ ] Test: Annotation serialization preserves targetId correctly
- [ ] Code coverage >80%
- [ ] File location: `core/drawing2d/src/main/kotlin/com/yourapp/drawing2d/model/Annotation.kt`
</ac-block>

---

### PR-5: Drawing2D Container + Stable Serialization

**Context (Human):** The main container holding all drawing data. Critical: serialization must be deterministic (same data → same JSON → same SHA256). This means sorted collections and stable map key ordering.

<ac-block id="S1-PR5-AC1">
**Acceptance Criteria for PR5 (Drawing2D)**:
- [ ] Create `data class Drawing2D` with fields: `schemaVersion: Int = 1`, `id: String`, `name: String`, `page: Page`, `layers: List<Layer>`, `entities: List<EntityV1>`, `annotations: List<AnnotationV1>`, `metadata: Map<String, String>`, `syncId: String?`, `syncStatus: String`, `updatedAt: Long`, `version: Int`
- [ ] Implement `Drawing2D.toJsonStable(): String` that:
  - Sorts `entities` by `id` lexicographically
  - Sorts `annotations` by `id` lexicographically
  - Sorts `layers` by `id` lexicographically
  - Uses pretty print with 2-space indent
  - Ensures stable map key ordering (alphabetically)
- [ ] Test: Calling `toJsonStable()` twice produces identical strings (byte-for-byte)
- [ ] Test: SHA256 hash of `toJsonStable()` output is identical across multiple calls
- [ ] Test: Round-trip `Drawing2D → JSON → Drawing2D` preserves all data
- [ ] Test: Collections order doesn't affect SHA256 (entities added in different order produce same hash after `toJsonStable()`)
- [ ] Code coverage >85%
- [ ] File location: `core/drawing2d/src/main/kotlin/com/yourapp/drawing2d/model/Drawing2D.kt`
</ac-block>

---

### PR-6: DrawingPatchEvent (Delta Operations with inverse())

**Context (Human):** Event Sourcing foundation for undo/redo. Each operation must have an inverse that perfectly reverses it. This allows us to store operation deltas instead of full snapshots, saving massive amounts of memory.

<ac-block id="S1-PR6-AC1">
**Acceptance Criteria for PR6 (Patch Operations)**:
- [ ] Create sealed class `PatchOpV1` with abstract function `fun inverse(): PatchOpV1`
- [ ] Implement `PatchOpV1.AddNode(nodeId: String, position: Point2D)` with `inverse()` returning `DeleteNode(nodeId, position)`
- [ ] Implement `PatchOpV1.DeleteNode(nodeId: String, deletedPosition: Point2D)` with `inverse()` returning `AddNode(nodeId, deletedPosition)`
- [ ] Implement `PatchOpV1.MoveNode(nodeId: String, oldPosition: Point2D, newPosition: Point2D)` with `inverse()` returning `MoveNode(nodeId, newPosition, oldPosition)`
- [ ] Implement `PatchOpV1.AddMember(memberId, startNodeId, endNodeId, profileRef?)` with inverse
- [ ] Implement `PatchOpV1.DeleteMember(memberId, deletedStartNodeId, deletedEndNodeId, deletedProfileRef?)` with inverse
- [ ] Implement `PatchOpV1.UpdateMemberProfile(memberId, oldProfileRef?, newProfileRef?)` with inverse swapping old/new
- [ ] Test: `addOp.inverse()` produces correct `DeleteNode` with same position
- [ ] Test: `moveOp.inverse().inverse()` equals original `moveOp` (double inverse is identity)
- [ ] Test: All operations serialize/deserialize correctly
- [ ] Memory test: Single operation < 1KB in memory
- [ ] Code coverage >90%
- [ ] File location: `core/drawing2d/src/main/kotlin/com/yourapp/drawing2d/events/PatchOpV1.kt`
- [ ] NOTE: Full `Drawing2D.apply(PatchOpV1)` implementation deferred to Sprint 3
</ac-block>

---

## Pack B: Validation Framework

### PR-7: Violation Model (Typed Errors)

**Context (Human):** Instead of throwing exceptions with string messages, we return structured errors that can be serialized, localized, and analyzed programmatically.

<ac-block id="S1-PR7-AC1">
**Acceptance Criteria for PR7 (Violation Types)**:
- [ ] Create `enum class Severity { ERROR, WARNING, INFO }`
- [ ] Create sealed class `Violation` with abstract fields: `path: String`, `severity: Severity`, `message: String`
- [ ] Implement `Violation.MissingField(path, fieldName)` with severity=ERROR and message "Missing required field: {fieldName}"
- [ ] Implement `Violation.InvalidValue(path, fieldName, value, constraint)` with severity=ERROR
- [ ] Implement `Violation.BrokenReference(path, referenceId, targetType)` with severity=ERROR and message "Reference {referenceId} to {targetType} not found"
- [ ] Implement `Violation.Custom(path, severity, message)`
- [ ] Test: All violation types serialize to JSON correctly
- [ ] Test: Violation messages are clear and actionable
- [ ] Code coverage >90%
- [ ] File location: `core/validation/src/main/kotlin/com/yourapp/validation/Violation.kt`
</ac-block>

---

### PR-8: DrawingValidator (Core Validation)

**Context (Human):** Validates Drawing2D structure. CRITICAL: Never throw on bad JSON - always return Result.Failure with violations. This prevents crashes and gives better error messages.

<ac-block id="S1-PR8-AC1">
**Acceptance Criteria for PR8 (Validator)**:
- [ ] Create `class DrawingValidator` with `fun validate(drawing: Drawing2D): List<Violation>`
- [ ] Check `schemaVersion == 1`, return `Violation.Custom` with ERROR if not
- [ ] Hard limits: Reject if `entities.size > 100_000` with violation "Too many entities: {count} (max: 100000)"
- [ ] Hard limits: Reject if `annotations.size > 100_000`
- [ ] Validate entity IDs are non-blank
- [ ] Validate Circle `radius > 0 && radius.isFinite()`, return `InvalidValue` if not
- [ ] Validate Polyline has `points.size >= 2`, return `InvalidValue` if not
- [ ] Validate annotation `targetId` references exist in `entities`, return `BrokenReference` if not
- [ ] Implement `fun validateSafe(json: String): Result<Drawing2D>` that:
  - Catches JSON parse errors and returns `Failure(ValidationException)`
  - NEVER throws exceptions on bad JSON
  - Returns `Success(drawing)` only if 0 ERROR-severity violations
- [ ] Test: Valid drawing returns empty violation list
- [ ] Test: Circle with `radius = -5.0` returns `InvalidValue` violation
- [ ] Test: Annotation with non-existent `targetId` returns `BrokenReference` violation
- [ ] Test: `validateSafe("{invalid json}")` returns `Failure` without throwing
- [ ] Test: Drawing with 150,000 entities returns violation about exceeding limit
- [ ] Code coverage >85%
- [ ] File location: `core/validation/src/main/kotlin/com/yourapp/validation/DrawingValidator.kt`
</ac-block>

---

### PR-9: Geometry Validation (NaN/Infinity Checks)

**Context (Human):** Extend validator with geometric sanity checks. Catch NaN and Infinity early before they poison calculations.

<ac-block id="S1-PR9-AC1">
**Acceptance Criteria for PR9 (Geometry Checks)**:
- [ ] Add validation method `validateGeometry(entity: EntityV1, index: Int): List<Violation>` to DrawingValidator
- [ ] Check all coordinate values (x, y) are finite: `isFinite()` returns true
- [ ] Check Circle/Arc radius values are finite
- [ ] Check angles in Arc are finite
- [ ] Return `InvalidValue` violation for non-finite values with message "Coordinates must be finite"
- [ ] Detect degenerate Line (zero length < 1e-6) and return WARNING violation with message "Line has zero length"
- [ ] Test: Line with `start = Point2D(Double.NaN, 0.0)` returns ERROR violation
- [ ] Test: Circle with `radius = Double.POSITIVE_INFINITY` returns ERROR violation
- [ ] Test: Line with identical start/end points returns WARNING violation
- [ ] Code coverage >80%
- [ ] File location: Extend existing `DrawingValidator.kt`
</ac-block>

---

### PR-10: Unit Tests + Golden Fixtures

**Context (Human):** Create comprehensive test coverage with both example-based and property-based tests. Golden fixtures are reference JSON files that never change - they protect against accidental serialization changes.

<ac-block id="S1-PR10-AC1">
**Acceptance Criteria for PR10 (Test Suite)**:
- [ ] Create directory `core/drawing2d/src/test/resources/fixtures/`
- [ ] Create `fixtures/valid_simple.json` - minimal valid drawing (1 line entity)
- [ ] Create `fixtures/valid_complex.json` - drawing with all entity types
- [ ] Create `fixtures/invalid_negative_radius.json` - Circle with negative radius
- [ ] Create `fixtures/invalid_broken_reference.json` - Annotation referencing non-existent entity
- [ ] Create at least 10 total fixtures (5 valid, 5 invalid)
- [ ] Implement golden test: Load each fixture, verify parser doesn't crash
- [ ] Implement golden test: Valid fixtures pass validation (0 ERROR violations)
- [ ] Implement golden test: Invalid fixtures fail validation with expected violations
- [ ] Property-based test (if using Kotest): Generate random Drawing2D, serialize, deserialize, verify equality
- [ ] Property-based test: `Drawing2D → toJsonStable() → SHA256` is deterministic (call 100 times, all hashes identical)
- [ ] Overall test coverage for `core-drawing2d` module >80%
- [ ] All tests pass: `./gradlew :core-drawing2d:test`
</ac-block>

---

## Pack C: Artifact Storage

### PR-11: ArtifactEntry + Manifest Models

**Context (Human):** Define how we track files on disk. Every file gets a manifest entry with SHA256 hash for integrity verification.

<ac-block id="S1-PR11-AC1">
**Acceptance Criteria for PR11 (Artifact Models)**:
- [ ] Create `enum class ArtifactKind { RAW_IMAGE, RECTIFIED_IMAGE, DRAWING_JSON, MODEL_JSON, PREVIEW_IMAGE, METADATA }`
- [ ] Create `data class ArtifactEntryV1(kind: ArtifactKind, relativePath: String, fileSha256: String, sizeBytes: Long, createdAt: Long)`
- [ ] Create `data class ManifestV1(schemaVersion: Int = 1, projectId: String, entries: List<ArtifactEntryV1>, createdAt: Long, updatedAt: Long)`
- [ ] All classes use `kotlinx.serialization.Serializable` annotation
- [ ] Test: Serialize and deserialize manifest preserves all fields
- [ ] Code coverage >90%
- [ ] File location: `core/storage/src/main/kotlin/com/yourapp/storage/Artifact.kt`
</ac-block>

---

### PR-12: SHA256 Utility (Streaming)

**Context (Human):** Compute file hashes without loading entire file into memory. Critical for large images (48MP photos can be 150MB+).

<ac-block id="S1-PR12-AC1">
**Acceptance Criteria for PR12 (SHA256)**:
- [ ] Create `object Sha256` with methods:
  - `fun compute(file: File): String` - streaming hash (doesn't load full file to memory)
  - `fun compute(input: InputStream): String` - streaming from stream
  - `fun compute(data: ByteArray): String` - direct byte array hash
- [ ] Use `MessageDigest.getInstance("SHA-256")` with 8KB buffer for streaming
- [ ] Return hash as lowercase hex string (64 characters)
- [ ] Test: SHA256 of empty ByteArray equals `"e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"`
- [ ] Test: SHA256 of `"hello world"` UTF-8 bytes equals `"b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9"`
- [ ] Test: Can compute hash of 100MB file without OOM (create temp file, verify)
- [ ] Code coverage >90%
- [ ] File location: `core/storage/src/main/kotlin/com/yourapp/storage/Sha256.kt`
</ac-block>

---

### PR-13: AtomicFileWriter (fsync + ENOSPC Handling)

**Context (Human):** The most critical piece of storage code. Without fsync(), data can be lost if app crashes during write. This implements write-to-temp-then-rename pattern with disk sync.

**Architectural Rule Applied:**
- Must call `FileDescriptor.sync()` BEFORE `renameTo()` to prevent 0-byte corruption

<ac-block id="S1-PR13-AC1">
**Acceptance Criteria for PR13 (Atomic Writes)**:
- [ ] Create `object AtomicFileWriter` with `fun write(file: File, data: ByteArray)`
- [ ] Implementation:
  1. Check `file.parentFile?.usableSpace >= data.size * 2L` before writing
  2. Write to `File(parent, "${file.name}.tmp")`
  3. Call `fileOutputStream.fd.sync()` to flush OS buffers to disk (CRITICAL)
  4. Call `tempFile.renameTo(file)` for atomic replacement
  5. Handle `ENOSPC` (disk full) errors with clear user message
  6. Cleanup temp file in `finally` block ALWAYS
- [ ] Create custom exception `class InsufficientStorageException(message: String, cause: Throwable?): IOException`
- [ ] Test: Write succeeds with valid data, file contains expected bytes
- [ ] Test: Temp file is cleaned up even if write fails
- [ ] Test: Cannot verify actual crash-safety in unit test, but verify `fd.sync()` is called
- [ ] Test: If usableSpace < required, throws `InsufficientStorageException` with message "Not enough space. Need {X}KB, have {Y}KB"
- [ ] Code coverage >85%
- [ ] File location: `core/storage/src/main/kotlin/com/yourapp/storage/AtomicFileWriter.kt`
</ac-block>

---

### PR-14: Image Integrity (File SHA256 Strategy)

**Context (Human):** We use file-level SHA256, not pixel-level. This is faster and sufficient for detecting corruption. Pixel-level hashing was removed due to instability across platforms.

<ac-block id="S1-PR14-AC1">
**Acceptance Criteria for PR14 (Image Integrity)**:
- [ ] Create `object ImageIntegrity` with `fun verify(file: File, expectedSha256: String): Boolean`
- [ ] Implementation computes SHA256 of file bytes and compares case-insensitive
- [ ] Add placeholder method `fun computeSSIM(image1: File, image2: File): Double` that throws `NotImplementedError("SSIM will be implemented in Sprint 2")`
- [ ] Remove any `pixelSha256` fields from `ArtifactEntryV1` (use `fileSha256` only)
- [ ] Test: Two identical PNG files have same `fileSha256`
- [ ] Test: Calling `verify()` with matching hash returns true
- [ ] Test: Calling `verify()` with mismatched hash returns false
- [ ] Documentation comment explaining why file hash (not pixel hash)
- [ ] Code coverage >85%
- [ ] File location: `core/storage/src/main/kotlin/com/yourapp/storage/ImageIntegrity.kt`
</ac-block>

---

### PR-15: Storage Layout Constants

**Context (Human):** Single source of truth for all file paths. Using enum prevents typos and makes refactoring safe.

<ac-block id="S1-PR15-AC1">
**Acceptance Criteria for PR15 (Storage Layout)**:
- [ ] Create `enum class ArtifactPath(val relativePath: String)` with values:
  - `RAW_IMAGE("raw.jpg")`
  - `RECTIFIED_IMAGE("rectified.png")`
  - `DRAWING_JSON("drawing2d.json")`
  - `MODEL_JSON("model.json")`
  - `PREVIEW_IMAGE("preview.png")`
  - `CAPTURE_META("capture_meta.json")`
  - `MANIFEST("manifest.json")`
  - `CORNERS_DEBUG("debug/corners.png")`
- [ ] Create `object StorageLayout` with helper functions:
  - `fun getProjectDir(baseDir: File, projectId: String): File`
  - `fun getArtifactFile(projectDir: File, artifact: ArtifactPath): File`
- [ ] Test: Path construction produces expected results
- [ ] Test: Using enum prevents typos (compile-time safety)
- [ ] Code coverage >90%
- [ ] File location: `core/storage/src/main/kotlin/com/yourapp/storage/StorageLayout.kt`
</ac-block>

---

## Pack D: Documentation

### PR-16: Drawing2D Format Specification

**Context (Human):** The authoritative document describing the JSON format. This is what external tools will implement to read/write our files.

<ac-block id="S1-PR16-AC1">
**Acceptance Criteria for PR16 (Format Spec)**:
- [ ] Create `/docs/drawing/DRAWING2D_V1.md`
- [ ] Document must include:
  - Current schema version (1)
  - Top-level structure (all fields explained)
  - Coordinate system (millimeters, 4 decimal precision, Double type)
  - All Entity types with JSON examples
  - All Annotation types with JSON examples
  - Validation rules (numbered list)
  - Complete example JSON file
- [ ] Include section on JSONPath notation for violation paths
- [ ] Reference fixtures in `/test/resources/fixtures/` as examples
- [ ] Markdown must be valid (no broken links)
- [ ] File location: `/docs/drawing/DRAWING2D_V1.md`
</ac-block>

---

### PR-17: Schema Evolution Policy

**Context (Human):** How we handle future versions (v2, v3, etc.) without breaking existing files. Critical for long-term maintenance.

<ac-block id="S1-PR17-AC1">
**Acceptance Criteria for PR17 (Evolution Policy)**:
- [ ] Create `/docs/drawing/SCHEMA_EVOLUTION.md`
- [ ] Document versioning strategy (schemaVersion field)
- [ ] Define backwards compatibility rules:
  - New optional fields: OK (use defaults)
  - New required fields: BREAKING (requires migration)
  - Rename fields: BREAKING
  - Remove fields: BREAKING
- [ ] Provide example migration path (v1 → v2 pseudocode)
- [ ] Include example `DrawingMigrator` class structure
- [ ] Define deprecation timeline policy
- [ ] Markdown must be valid
- [ ] File location: `/docs/drawing/SCHEMA_EVOLUTION.md`
</ac-block>

---

## Pack E: Sprint Closeout

### PR-18: Definition of Done Validation

**Context (Human):** Final verification that all sprint objectives are met before moving to Sprint 2.

<ac-block id="S1-PR18-AC1">
**Acceptance Criteria for PR18 (Sprint Closeout)**:
- [ ] Code coverage check: All `core-*` modules >80% (run `./gradlew jacocoTestReport`)
- [ ] Ktlint check passes: `./gradlew ktlintCheck` with 0 errors
- [ ] NO Android dependencies: `grep -r "import android" core-*/src/main/` returns nothing
- [ ] All tests pass: `./gradlew test` with 0 failures
- [ ] Documentation complete: All 3 docs files exist and are valid Markdown
- [ ] Create `/docs/sprint-1/FILE_OVERVIEW.md` listing all modules and their purposes
- [ ] Update root `README.md` with Sprint 1 deliverables section
- [ ] No TODO comments in production code: `grep -r "TODO" core-*/src/main/` returns nothing (test TODOs are OK)
- [ ] All PRs merged to `main` branch
- [ ] Git tag created: `git tag sprint-1-complete`
</ac-block>

---

## Sprint 1 Success Metrics

**Definition of Done (Sprint Level):**
- ✅ All 18 PRs completed and merged
- ✅ 50+ unit tests written
- ✅ 10+ golden fixtures created
- ✅ Code coverage >80% across all `core-*` modules
- ✅ Zero Android dependencies in core modules
- ✅ All architectural rules (ARCH-MATH-001, ARCH-SAFE-001) followed
- ✅ Documentation complete (3 docs minimum)
- ✅ Ktlint clean
- ✅ Ready for Sprint 2 (capture pipeline)

**Key Deliverables:**
1. Pure JVM modules: `core-drawing2d`, `core-storage`, `core-validation`
2. Deterministic math utilities (Double precision)
3. Complete 2D geometry model with validation
4. Event sourcing foundation (delta patches)
5. Atomic file operations with crash safety
6. Comprehensive test suite

**Technical Debt:** None expected at this stage. This sprint establishes foundation with high quality standards.

---

## Notes for Developers

**Critical Architectural Decisions:**
- ARCH-MATH-001: Double for math, Float only at rendering boundaries
- ARCH-SAFE-001: Use `toLong()` not `roundToInt()` to prevent overflow
- File-level SHA256 (not pixel-level) for image integrity
- Event Sourcing with inverse operations for memory-efficient undo/redo
- Atomic writes with fsync() to prevent corruption

**Dependencies Between PRs:**
- PR-2 depends on PR-1.5 (needs MathUtils)
- PR-3 depends on PR-2 (needs Point2D)
- PR-5 depends on PR-3, PR-4 (needs Entity and Annotation types)
- PR-8 depends on PR-7 (needs Violation types)
- PR-9 depends on PR-8 (extends DrawingValidator)
- PR-10 depends on PR-1 through PR-9 (tests everything)
- PR-13 depends on PR-12 (uses Sha256)
- PR-18 depends on ALL previous PRs (closeout verification)

**Parallel Work Opportunities:**
- Pack A (PR-1 to PR-6) and Pack B (PR-7 to PR-10) can run in parallel with 2 developers
- Pack C (PR-11 to PR-15) can start after Pack A completes
- Pack D (PR-16 to PR-17) can be done anytime by tech writer or senior dev

---

*Last updated: 2026-02-26*
*Sprint status: Ready for implementation*
*Next sprint: Sprint 2 - Capture & CV Pipeline*
