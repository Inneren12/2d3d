# Sprint 4: Converter - Drawing2D → StructuralModel (Deterministic)

**Status:** Ready
**Dates:** 2026-06-11 — 2026-07-02
**Duration:** 3 weeks
**Team:** 1-2 JVM/Kotlin developers (algorithm-focused)
**Goal:** Convert 2D drawing with nodes and members into 3D structural model with deterministic node merging, validation, and geometry processing. Pure JVM module (no Android dependencies) with comprehensive golden tests.

**Key Deliverables:**
- `core-converter` module (Pure JVM/Kotlin)
- Drawing2D → StructuralModel conversion pipeline
- Spatial clustering for node merging (deterministic)
- Epsilon-based distance comparison (ARM/x86 stable)
- Degenerate geometry detection (hard fail on zero-length members)
- 6-phase pipeline with fixed order
- CRLF normalization for JSON output
- Golden tests with byte-for-byte determinism
- Validation with typed errors
- 100+ unit tests

**Critical Architecture Applied:**
- ARCH-MATH-001: Double precision, round before epsilon comparison
- ARCH-PERF-001: O(N) node merging via spatial clustering (not pairwise O(N²))
- Pure JVM (no Android dependencies)
- Deterministic output (same input → same output, always)

---

## Pack A: Converter Module Foundation

### PR-01: Module `core-converter` Setup

**Context (Human):** Pure JVM module for conversion logic. Must run on server, CI, desktop - anywhere with JVM. Zero Android dependencies.

<ac-block id="S4-PR01-AC1">
**Acceptance Criteria for PR01 (Module Setup)**:
- [ ] Create module `core-converter` in `/core/converter/` directory
- [ ] Module path in settings.gradle.kts: `include(":core:converter")`
- [ ] Plugin: `kotlin("jvm")` (NOT Android plugin)
- [ ] Dependencies: `:core:drawing2d`, `kotlinx-serialization`, JUnit5, Kotest
- [ ] Module compiles: `./gradlew :core:converter:build`
- [ ] Empty test suite runs: `./gradlew :core:converter:test`
- [ ] NO `android.*` imports (verified by grep)
- [ ] Create README.md explaining module purpose
- [ ] Module structure: `/src/main/kotlin/` and `/src/test/kotlin/`
- [ ] Test: `grep -r "import android" core/converter/src/main/` returns nothing (exit code 1)
- [ ] File location: `/core/converter/build.gradle.kts`
</ac-block>

---

### PR-02: StructuralModel Data Classes

**Context (Human):** Define output model for 3D structural representation. Nodes have 3D coordinates, members connect nodes with profiles.

<ac-block id="S4-PR02-AC1">
**Acceptance Criteria for PR02 (StructuralModel)**:
- [ ] Create `data class Node3D(val id: String, val x: Double, val y: Double, val z: Double)`
- [ ] Create `data class Member3D(val id: String, val startNodeId: String, val endNodeId: String, val profileRef: String?, val length: Double)`
- [ ] Create `data class StructuralModel(val schemaVersion: Int = 1, val nodes: List<Node3D>, val members: List<Member3D>, val metadata: Map<String, String>)`
- [ ] All coordinates use `Double` type (ARCH-MATH-001)
- [ ] `length` field in Member3D calculated via Euclidean distance
- [ ] Model is serializable via `kotlinx.serialization`
- [ ] Test: Serialization/deserialization preserves all data
- [ ] Test: All Double values serialize with proper precision (4 decimals)
- [ ] Code coverage >90%
- [ ] File location: `core/converter/src/main/kotlin/.../model/StructuralModel.kt`
</ac-block>

---

### PR-03: ConversionError Typed Errors

**Context (Human):** Structured error types for conversion failures. Each error has specific reason and context for debugging.

<ac-block id="S4-PR03-AC1">
**Acceptance Criteria for PR03 (Error Types)**:
- [ ] Create sealed class `ConversionError` with subtypes:
  - `NoScale(message: String)` - Drawing has no scale information
  - `InvalidScale(value: Double, reason: String)` - Scale is invalid (zero, negative, NaN)
  - `BrokenReference(memberId: String, nodeId: String)` - Member references non-existent node
  - `DegenerateGeometry(memberId: String, length: Double, threshold: Double)` - Member length below threshold
  - `DuplicateNode(nodeId: String, position: Point2D)` - Two nodes at same position
  - `InvalidCoordinate(entityId: String, coordinate: String, value: Double)` - NaN or Infinity coordinate
  - `ValidationFailed(violations: List<String>)` - General validation errors
- [ ] Each error has clear, actionable message
- [ ] Errors are serializable (for API responses)
- [ ] Test: Each error type instantiates correctly
- [ ] Test: Error messages are clear and helpful
- [ ] File location: `core/converter/src/main/kotlin/.../error/ConversionError.kt`
</ac-block>

---

### PR-04: Degenerate Geometry Detection (Hard Fail)

**Context (Human):** CRITICAL: Zero-length or near-zero-length members are physically invalid and will cause rendering/analysis failures. Must hard fail (not silently skip) to alert user.

<ac-block id="S4-PR04-AC1">
**Acceptance Criteria for PR04 (Degenerate Detection)**:
- [ ] Define constant: `const val MIN_MEMBER_LENGTH = 1e-6` (1 micrometer, effectively zero)
- [ ] Implement `fun checkDegenerateGeometry(members: List<Member2D>, nodes: Map<String, Node2D>): List<ConversionError>`
- [ ] For each member:
  1. Look up start and end node positions
  2. Calculate Euclidean distance
  3. If `distance < MIN_MEMBER_LENGTH`, return `ConversionError.DegenerateGeometry(memberId, distance, MIN_MEMBER_LENGTH)`
- [ ] DO NOT silently skip degenerate members (hard fail instead)
- [ ] Rationale: Degenerate geometry indicates user error (coincident nodes) that must be fixed
- [ ] Test: Member with length 0.0 returns error
- [ ] Test: Member with length 1e-9 (below threshold) returns error
- [ ] Test: Member with length 1e-5 (above threshold) passes
- [ ] Test: Error message includes member ID and exact length
- [ ] Code coverage >95%
- [ ] File location: `core/converter/src/main/kotlin/.../validation/DegenerateGeometryChecker.kt`
</ac-block>

---

## Pack B: Node Merging (Deterministic Spatial Clustering)

### PR-05: Epsilon Distance Comparison

**Context (Human):** Floating-point comparison requires epsilon tolerance. Two nodes within epsilon distance are considered coincident.

<ac-block id="S4-PR05-AC1">
**Acceptance Criteria for PR05 (Epsilon)**:
- [ ] Define constant: `const val EPSILON = 1e-6` (1 micrometer tolerance)
- [ ] Implement `fun areCoincident(p1: Point2D, p2: Point2D, epsilon: Double = EPSILON): Boolean`
- [ ] Calculate Euclidean distance: `sqrt((p1.x - p2.x)^2 + (p1.y - p2.y)^2)`
- [ ] Return `distance < epsilon`
- [ ] Test: Points at (0.0, 0.0) and (0.0, 0.0) are coincident
- [ ] Test: Points at (0.0, 0.0) and (0.0, 1e-7) are coincident (below epsilon)
- [ ] Test: Points at (0.0, 0.0) and (0.0, 1e-5) are NOT coincident (above epsilon)
- [ ] Test: Points at (1000.0, 1000.0) and (1000.0000001, 1000.0000001) are coincident
- [ ] Code coverage >95%
- [ ] File location: `core/converter/src/main/kotlin/.../geometry/EpsilonComparison.kt`
</ac-block>

---

### PR-06: Distance Rounding Before Comparison (ARM/x86 Stability)

**Context (Human):** CRITICAL: Floating-point arithmetic produces slightly different results on ARM vs x86 due to different FPU rounding modes. Must round distances before epsilon comparison to ensure determinism.

<ac-block id="S4-PR06-AC1">
**Acceptance Criteria for PR06 (Distance Rounding)**:
- [ ] Modify `areCoincident()` to round distance before comparison:
```kotlin
  fun areCoincident(p1: Point2D, p2: Point2D, epsilon: Double = EPSILON): Boolean {
      val dx = p1.x - p2.x
      val dy = p1.y - p2.y
      val distanceSquared = dx * dx + dy * dy
      val distance = sqrt(distanceSquared)

      // CRITICAL: Round before comparison (ARCH-MATH-001)
      val roundedDistance = MathUtils.round(distance, decimals = 4)

      return roundedDistance < epsilon
  }
```
- [ ] Use `MathUtils.round()` from Sprint 1 (with proper rounding, not truncation)
- [ ] Rationale: Different FPU implementations produce tiny differences (14th decimal place), rounding normalizes them
- [ ] Test: Distance 0.0009999999 (ARM quirk) rounds to 0.0010, compared correctly
- [ ] Test: Distance 0.0000009999 rounds to 0.0000, considered coincident
- [ ] Test: Same input on x86 and ARM produces identical result (simulate via test)
- [ ] Documentation: Explain why rounding is necessary for cross-platform determinism
- [ ] Code coverage >95%
- [ ] File location: Update `EpsilonComparison.kt`
</ac-block>

---

### PR-07: Spatial Clustering Algorithm (Not Pairwise)

**Context (Human):** CRITICAL PERFORMANCE: Naive pairwise merging is O(N²). Use spatial clustering (similar to DBSCAN) for O(N log N) performance. CRITICAL DETERMINISM: Clustering must be deterministic (independent of node order).

<ac-block id="S4-PR07-AC1">
**Acceptance Criteria for PR07 (Spatial Clustering)**:
- [ ] Implement `fun clusterCoincidentNodes(nodes: List<Node2D>, epsilon: Double = EPSILON): List<NodeCluster>`
- [ ] Data class: `data class NodeCluster(val representativeId: String, val memberIds: Set<String>, val centroidPosition: Point2D)`
- [ ] Algorithm (deterministic spatial clustering):
  1. Sort nodes by ID lexicographically (deterministic order)
  2. For each node (in sorted order):
     - If already in cluster, skip
     - Create new cluster with this node
     - Find all other nodes within `epsilon` distance (use spatial grid for efficiency)
     - Add them to cluster
     - Compute centroid: `centroid = average(all cluster node positions)` **with sorted IDs** (ARCH-PERF-001)
     - Mark all cluster nodes as processed
  3. Return list of clusters
- [ ] Centroid calculation MUST sort node IDs before averaging (prevents non-associative floating-point drift)
- [ ] Test: Nodes A, B, C within epsilon form single cluster (regardless of processing order)
- [ ] Test: Processing nodes in order [A,B,C] vs [C,A,B] produces IDENTICAL cluster (same centroid)
- [ ] Test: Chain merging: A---B---C where dist(A,B) < epsilon and dist(B,C) < epsilon produces 1 cluster (not 2)
- [ ] Test: Isolated node forms cluster of size 1
- [ ] Test: 1000 nodes with 100 clusters completes in <100ms (O(N log N) performance)
- [ ] Code coverage >90%
- [ ] Documentation: Explain why spatial clustering (not pairwise), why sorted IDs for centroid
- [ ] File location: `core/converter/src/main/kotlin/.../geometry/NodeClustering.kt`
</ac-block>

---

### PR-08: Chain Merging Test (Critical Edge Case)

**Context (Human):** Chain merging is the hardest test case: A---B---C where each pair is within epsilon. Naive pairwise merging can produce 2 clusters (A+B and C) depending on order. Spatial clustering MUST produce 1 cluster.

<ac-block id="S4-PR08-AC1">
**Acceptance Criteria for PR08 (Chain Test)**:
- [ ] Create comprehensive test for chain merging:
```kotlin
  test("Chain merging: A---B---C produces single cluster") {
      val epsilon = 1e-3
      val nodes = listOf(
          Node2D("A", Point2D(0.0, 0.0)),
          Node2D("B", Point2D(0.0005, 0.0)),  // 0.5mm from A
          Node2D("C", Point2D(0.001, 0.0))    // 0.5mm from B, 1mm from A
      )

      val clusters = clusterCoincidentNodes(nodes, epsilon)

      clusters.size shouldBe 1
      clusters[0].memberIds shouldBe setOf("A", "B", "C")
  }
```
- [ ] Test: Processing in different orders [A,B,C], [C,B,A], [B,A,C] all produce SAME result
- [ ] Test: Centroid calculation is deterministic (same cluster → same centroid, always)
- [ ] Test: Longer chain (5 nodes) also produces single cluster
- [ ] Test: Two separate chains produce two clusters (no merging across chains)
- [ ] Golden test: Chain scenario matches reference output byte-for-byte
- [ ] Code coverage: Chain merging code path covered
- [ ] File location: `core/converter/src/test/kotlin/.../geometry/NodeClusteringTest.kt`
</ac-block>

---

### PR-09: Node Cluster Merging (Apply Merges to Drawing)

**Context (Human):** Apply clustering result to Drawing2D: replace clustered nodes with single representative node, update member references.

<ac-block id="S4-PR09-AC1">
**Acceptance Criteria for PR09 (Apply Merges)**:
- [ ] Implement `fun applyNodeMerges(drawing: Drawing2D, clusters: List<NodeCluster>): Drawing2D`
- [ ] Algorithm:
  1. Create node mapping: `clusteredNodeId -> representativeId`
  2. For each cluster with >1 node:
     - Keep representative node (first by ID sort)
     - Remove other nodes from drawing
     - Update all member references: if `startNodeId` or `endNodeId` in cluster, replace with representative
  3. Remove duplicate members (same start+end after merge)
  4. Return updated drawing
- [ ] Representative node position = cluster centroid (average position)
- [ ] Duplicate members removed: If two members connect same nodes after merge, keep one (first by ID)
- [ ] Test: 3-node cluster merges into 1 node
- [ ] Test: Members connected to merged nodes updated correctly
- [ ] Test: Duplicate members removed after merge
- [ ] Test: Isolated nodes (clusters of size 1) unchanged
- [ ] Code coverage >85%
- [ ] File location: `core/converter/src/main/kotlin/.../geometry/NodeMergeApplier.kt`
</ac-block>

---

## Pack C: Conversion Pipeline (6 Phases - Fixed Order)

### PR-10: Phase 1 - Input Validation

**Context (Human):** First phase: validate input Drawing2D before conversion. Check for missing scale, broken references, invalid coordinates.

<ac-block id="S4-PR10-AC1">
**Acceptance Criteria for PR10 (Phase 1)**:
- [ ] Implement `fun validateInput(drawing: Drawing2D): Result<Unit, List<ConversionError>>`
- [ ] Checks:
  1. Scale exists: `drawing.metadata["scaleInfo"]` is present and parseable
  2. Scale is valid: `mmPerPixel > 0 && mmPerPixel.isFinite()`
  3. All node coordinates finite (not NaN or Infinity)
  4. All member references valid: `startNodeId` and `endNodeId` exist in `drawing.nodes`
  5. No empty node IDs, no empty member IDs
- [ ] Return `Failure(errors)` if any check fails (short-circuit, collect all errors)
- [ ] Return `Success(Unit)` if all checks pass
- [ ] Test: Drawing with no scale returns `NoScale` error
- [ ] Test: Drawing with scale=0 returns `InvalidScale` error
- [ ] Test: Node with NaN coordinate returns `InvalidCoordinate` error
- [ ] Test: Member referencing non-existent node returns `BrokenReference` error
- [ ] Test: Valid drawing returns Success
- [ ] Code coverage >90%
- [ ] File location: `core/converter/src/main/kotlin/.../pipeline/Phase1Validation.kt`
</ac-block>

---

### PR-11: Phase 2 - Scale Application

**Context (Human):** Convert drawing coordinates (pixels) to physical coordinates (millimeters) using scale from Drawing2D metadata.

<ac-block id="S4-PR11-AC1">
**Acceptance Criteria for PR11 (Phase 2)**:
- [ ] Implement `fun applyScale(drawing: Drawing2D, scaleInfo: ScaleInfo): Drawing2D`
- [ ] Parse `scaleInfo` from `drawing.metadata["scaleInfo"]` JSON
- [ ] Data class: `data class ScaleInfo(val mmPerPixel: Double)`
- [ ] Transform all node positions: `nodePhysical = nodePixel * mmPerPixel`
- [ ] Use Double precision (ARCH-MATH-001)
- [ ] Return new Drawing2D with scaled coordinates
- [ ] Test: Node at (100px, 200px) with scale 2.5mm/px becomes (250mm, 500mm)
- [ ] Test: Scale application preserves node count and member references
- [ ] Test: Invalid scale (zero, negative) handled in Phase 1 (this phase assumes valid)
- [ ] Code coverage >90%
- [ ] File location: `core/converter/src/main/kotlin/.../pipeline/Phase2ScaleApplication.kt`
</ac-block>

---

### PR-12: Phase 3 - Node Merging (Invoke Clustering)

**Context (Human):** Apply spatial clustering to merge coincident nodes. This is where PR-07's algorithm is invoked.

<ac-block id="S4-PR12-AC1">
**Acceptance Criteria for PR12 (Phase 3)**:
- [ ] Implement `fun mergeCoincidentNodes(drawing: Drawing2D, epsilon: Double = EPSILON): Drawing2D`
- [ ] Invokes `clusterCoincidentNodes()` from PR-07
- [ ] Invokes `applyNodeMerges()` from PR-09
- [ ] Returns drawing with merged nodes and updated member references
- [ ] Test: Drawing with 3 coincident nodes results in 1 merged node
- [ ] Test: Members referencing merged nodes updated correctly
- [ ] Test: Isolated nodes unchanged
- [ ] Test: Determinism: Same drawing produces same output (byte-for-byte)
- [ ] Golden test: Reference drawing with known merges matches expected output
- [ ] Code coverage >85%
- [ ] File location: `core/converter/src/main/kotlin/.../pipeline/Phase3NodeMerging.kt`
</ac-block>

---

### PR-13: Phase 4 - Degenerate Geometry Check

**Context (Human):** After node merging, some members may become zero-length (start and end merged to same node). Detect and fail.

<ac-block id="S4-PR13-AC1">
**Acceptance Criteria for PR13 (Phase 4)**:
- [ ] Implement `fun checkDegenerateMembers(drawing: Drawing2D): Result<Unit, List<ConversionError>>`
- [ ] Invokes `checkDegenerateGeometry()` from PR-04
- [ ] Checks all members after merging
- [ ] Returns `Failure(errors)` if any degenerate members found
- [ ] Returns `Success(Unit)` if all members have length >= MIN_MEMBER_LENGTH
- [ ] Test: Member with length 0.0 returns error
- [ ] Test: All valid members return Success
- [ ] Test: Error includes member ID and calculated length
- [ ] Code coverage >90%
- [ ] File location: `core/converter/src/main/kotlin/.../pipeline/Phase4DegenerateCheck.kt`
</ac-block>

---

### PR-14: Phase 5 - 3D Extrusion (Z-coordinate Assignment)

**Context (Human):** Convert 2D nodes to 3D nodes by assigning Z=0 (ground plane). Members inherit 3D coordinates from nodes.

<ac-block id="S4-PR14-AC1">
**Acceptance Criteria for PR14 (Phase 5)**:
- [ ] Implement `fun extrudeTo3D(drawing: Drawing2D): StructuralModel`
- [ ] For each Node2D: Create `Node3D(id, x, y, z=0.0)`
- [ ] For each Member2D:
  1. Look up start and end node 3D positions
  2. Calculate length: `sqrt((x2-x1)^2 + (y2-y1)^2 + (z2-z1)^2)`
  3. Create `Member3D(id, startNodeId, endNodeId, profileRef, length)`
- [ ] All Z coordinates = 0.0 for MVP (future: support multi-story buildings)
- [ ] Length calculated using Double precision, rounded to 4 decimals for JSON
- [ ] Test: 2D node at (1000mm, 2000mm) becomes 3D node at (1000, 2000, 0)
- [ ] Test: Member length calculated correctly in 3D
- [ ] Test: Profile references preserved from 2D to 3D
- [ ] Code coverage >90%
- [ ] File location: `core/converter/src/main/kotlin/.../pipeline/Phase5Extrusion.kt`
</ac-block>

---

### PR-15: Phase 6 - Output Serialization (Deterministic JSON)

**Context (Human):** Serialize StructuralModel to JSON with deterministic formatting (sorted keys, sorted collections, stable output).

<ac-block id="S4-PR15-AC1">
**Acceptance Criteria for PR15 (Phase 6)**:
- [ ] Implement `fun serializeModel(model: StructuralModel): String`
- [ ] Sort nodes by ID before serialization
- [ ] Sort members by ID before serialization
- [ ] Sort metadata keys alphabetically
- [ ] Use pretty print with 2-space indent
- [ ] Round all Double values to 4 decimals before serialization
- [ ] Return JSON string
- [ ] Test: Same model serialized twice produces identical JSON (byte-for-byte)
- [ ] Test: Nodes and members appear in sorted order in JSON
- [ ] Test: Metadata keys alphabetically sorted
- [ ] Golden test: Reference model matches expected JSON file exactly
- [ ] Code coverage >90%
- [ ] File location: `core/converter/src/main/kotlin/.../pipeline/Phase6Serialization.kt`
</ac-block>

---

### PR-16: Pipeline Orchestrator (Fixed 6-Phase Order)

**Context (Human):** Orchestrate all 6 phases in fixed order. This is the main conversion entry point.

<ac-block id="S4-PR16-AC1">
**Acceptance Criteria for PR16 (Orchestrator)**:
- [ ] Implement `class ConversionPipeline` with `fun convert(drawing: Drawing2D): Result<StructuralModel, List<ConversionError>>`
- [ ] Execute phases in FIXED order:
  1. Phase1Validation
  2. Phase2ScaleApplication
  3. Phase3NodeMerging
  4. Phase4DegenerateCheck
  5. Phase5Extrusion
  6. Phase6Serialization (returns model, not JSON, for testing)
- [ ] Short-circuit on first error: If any phase fails, return Failure immediately (no further phases)
- [ ] Log each phase execution (timing, success/failure) for diagnostics
- [ ] Return `Success(model)` if all phases pass
- [ ] Return `Failure(errors)` if any phase fails
- [ ] Test: End-to-end conversion of valid drawing succeeds
- [ ] Test: Drawing with missing scale fails at Phase 1
- [ ] Test: Drawing with degenerate member fails at Phase 4
- [ ] Test: Pipeline phases executed in correct order (verify via logging)
- [ ] Code coverage >85%
- [ ] File location: `core/converter/src/main/kotlin/.../ConversionPipeline.kt`
</ac-block>

---

## Pack D: CRLF Normalization (Cross-Platform Determinism)

### PR-17: Line Ending Detection

**Context (Human):** CRITICAL: Git can change line endings (CRLF ↔ LF) on Windows vs Unix. Must force LF for JSON output to ensure byte-for-byte determinism across platforms.

<ac-block id="S4-PR17-AC1">
**Acceptance Criteria for PR17 (Line Ending Detection)**:
- [ ] Implement `fun detectLineEnding(text: String): LineEnding`
- [ ] Enum: `enum class LineEnding { LF, CRLF, MIXED }`
- [ ] Scan text for `\r\n` (CRLF) and `\n` (LF)
- [ ] Return `CRLF` if any `\r\n` found, `LF` if only `\n` found, `MIXED` if both
- [ ] Test: Text with `\r\n` returns CRLF
- [ ] Test: Text with `\n` returns LF
- [ ] Test: Text with both returns MIXED
- [ ] Test: Text with no newlines returns LF (default)
- [ ] Code coverage >95%
- [ ] File location: `core/converter/src/main/kotlin/.../util/LineEndingDetector.kt`
</ac-block>

---

### PR-18: CRLF Normalization (Force LF)

**Context (Human):** Normalize all JSON output to LF (Unix style). This ensures golden tests pass on Windows, Mac, Linux identically.

<ac-block id="S4-PR18-AC1">
**Acceptance Criteria for PR18 (CRLF Normalization)**:
- [ ] Implement `fun normalizeLineEndings(text: String): String`
- [ ] Replace all `\r\n` with `\n`
- [ ] Replace all standalone `\r` with `\n` (old Mac style)
- [ ] Return normalized text (LF only)
- [ ] Apply normalization in `serializeModel()` after JSON generation
- [ ] Test: Text with CRLF normalized to LF
- [ ] Test: Text with mixed endings normalized to LF
- [ ] Test: Text already using LF unchanged
- [ ] Test: JSON output contains NO `\r` characters (verify via byte scan)
- [ ] Code coverage >95%
- [ ] File location: `core/converter/src/main/kotlin/.../util/LineEndingNormalizer.kt`
</ac-block>

---

### PR-19: JSON Writer (Force LF in Pretty Print)

**Context (Human):** Ensure kotlinx.serialization uses LF for pretty print indentation. By default, it uses system line separator which differs per OS.

<ac-block id="S4-PR19-AC1">
**Acceptance Criteria for PR19 (JSON Writer)**:
- [ ] Configure kotlinx.serialization JSON encoder:
```kotlin
  val json = Json {
      prettyPrint = true
      prettyPrintIndent = "  "  // 2 spaces
      encodeDefaults = true
  }
```
- [ ] After encoding, apply `normalizeLineEndings()` to force LF
- [ ] Alternative: Use custom JsonWriter that forces LF (if serialization library doesn't cooperate)
- [ ] Test: JSON output on Windows contains only LF (no CRLF)
- [ ] Test: JSON output on Linux contains only LF
- [ ] Test: Same model on Windows and Linux produces identical JSON (byte-for-byte)
- [ ] Golden test: Reference JSON file has only LF line endings (verify via hex dump)
- [ ] Code coverage >90%
- [ ] File location: Update `Phase6Serialization.kt`
</ac-block>

---

## Pack E: Pipeline Order Enforcement

### PR-20: Pipeline Phase Order Validation

**Context (Human):** Enforce that phases execute in correct order. Out-of-order execution can cause bugs (e.g., merging before scaling produces wrong results).

<ac-block id="S4-PR20-AC1">
**Acceptance Criteria for PR20 (Order Validation)**:
- [ ] Add phase ordering checks in `ConversionPipeline`:
```kotlin
  private var executedPhases = mutableListOf<String>()

  private fun executePhase(phaseName: String, block: () -> Result<*, *>) {
      executedPhases.add(phaseName)
      // Verify phase order
      val expectedIndex = PHASE_ORDER.indexOf(phaseName)
      val actualIndex = executedPhases.size - 1
      require(expectedIndex == actualIndex) {
          "Phase $phaseName executed out of order. Expected index $expectedIndex, got $actualIndex"
      }
      return block()
  }

  companion object {
      val PHASE_ORDER = listOf(
          "Validation", "ScaleApplication", "NodeMerging",
          "DegenerateCheck", "Extrusion", "Serialization"
      )
  }
```
- [ ] Test: Phases executed in correct order (verify via executedPhases list)
- [ ] Test: Attempting to skip phase throws exception (defensive programming)
- [ ] Test: Attempting to execute phases out of order throws exception
- [ ] Code coverage >90%
- [ ] File location: Update `ConversionPipeline.kt`
</ac-block>

---

### PR-21: Pipeline Order Documentation

**Context (Human):** Document the 6-phase pipeline with rationale for order. This is critical for maintainability.

<ac-block id="S4-PR21-AC1">
**Acceptance Criteria for PR21 (Pipeline Docs)**:
- [ ] Create `/docs/converter/PIPELINE_ORDER.md` documenting:
  - Phase 1: Validation (why first - fail fast on bad input)
  - Phase 2: Scale (why before merging - merging uses physical distances)
  - Phase 3: Merging (why before degenerate check - merging can create degenerates)
  - Phase 4: Degenerate check (why after merging - checks merged result)
  - Phase 5: Extrusion (why after all 2D processing - final 2D→3D step)
  - Phase 6: Serialization (why last - output step)
- [ ] Include diagram: `Drawing2D → [6 phases] → StructuralModel`
- [ ] Explain consequences of wrong order (e.g., merging before scaling = wrong epsilon)
- [ ] Document phase inputs/outputs
- [ ] Markdown must be valid
- [ ] File location: `/docs/converter/PIPELINE_ORDER.md`
</ac-block>

---

## Pack F: Golden Tests (Determinism Verification)

### PR-22: Golden Test Fixtures

**Context (Human):** Create reference input/output pairs for golden tests. These files are committed to repo and MUST NOT change.

<ac-block id="S4-PR22-AC1">
**Acceptance Criteria for PR22 (Fixtures)**:
- [ ] Create directory: `core/converter/src/test/resources/golden/`
- [ ] Create fixtures:
  - `simple.drawing2d.json` - 4 nodes, 3 members, no merging needed
  - `simple.expected.model.json` - Expected output
  - `merging.drawing2d.json` - 10 nodes with 3 pairs coincident (need merging)
  - `merging.expected.model.json` - Expected output (7 nodes after merge)
  - `chain.drawing2d.json` - A---B---C chain merging case
  - `chain.expected.model.json` - Expected output (1 node after merge)
  - `complex.drawing2d.json` - 100 nodes, 150 members, multiple merge scenarios
  - `complex.expected.model.json` - Expected output
- [ ] All expected outputs generated on reference platform (Linux CI)
- [ ] All JSON files use LF line endings (verify via `file` command or hex editor)
- [ ] Commit files to Git with `.gitattributes` entry: `*.json text eol=lf`
- [ ] Test: Load each fixture, verify parseable
- [ ] Documentation: README.md in golden/ explaining fixtures
- [ ] File location: `core/converter/src/test/resources/golden/`
</ac-block>

---

### PR-23: Golden Test Runner

**Context (Human):** Test harness that runs conversion on each golden fixture and compares output byte-for-byte.

<ac-block id="S4-PR23-AC1">
**Acceptance Criteria for PR23 (Golden Runner)**:
- [ ] Implement test class `GoldenTests` with test for each fixture:
```kotlin
  @ParameterizedTest
  @MethodSource("goldenFixtures")
  fun `golden test`(fixtureName: String) {
      // Load input
      val inputJson = loadResource("golden/$fixtureName.drawing2d.json")
      val drawing = Json.decodeFromString<Drawing2D>(inputJson)

      // Run conversion
      val result = ConversionPipeline().convert(drawing)

      // Assert success
      result.shouldBeSuccess()
      val model = result.value

      // Serialize output
      val actualJson = serializeModel(model)

      // Load expected
      val expectedJson = loadResource("golden/$fixtureName.expected.model.json")

      // Compare byte-for-byte
      actualJson shouldBe expectedJson

      // Additional: Compare SHA256 hashes (even more robust)
      Sha256.compute(actualJson.toByteArray()) shouldBe Sha256.compute(expectedJson.toByteArray())
  }
```
- [ ] Test suite includes: simple, merging, chain, complex fixtures
- [ ] If test fails, output diff showing mismatch
- [ ] Test runs on CI (Linux) and local (Mac/Windows) - must pass on all
- [ ] Test: Golden tests pass on x86 and ARM platforms (simulate if needed)
- [ ] Test: Golden tests pass on Windows, Mac, Linux (CRLF normalization validated)
- [ ] File location: `core/converter/src/test/kotlin/.../GoldenTests.kt`
</ac-block>

---

### PR-24: Golden Test Regeneration Script

**Context (Human):** Tool to regenerate expected outputs when pipeline logic legitimately changes. Must be explicit action (not automatic).

<ac-block id="S4-PR24-AC1">
**Acceptance Criteria for PR24 (Regen Script)**:
- [ ] Create script: `core/converter/scripts/regenerate-golden-tests.sh`
- [ ] Script:
  1. For each `*.drawing2d.json` in golden/
  2. Run conversion pipeline
  3. Write output to `*.expected.model.json`
  4. Normalize line endings to LF
  5. Pretty print JSON (2-space indent)
  6. Compute SHA256, print to console
- [ ] Script requires explicit confirmation: "This will overwrite expected outputs. Proceed? (y/n)"
- [ ] Document in README: When to regenerate (only when pipeline logic changes, not for bug fixes)
- [ ] Test: Script runs successfully
- [ ] Test: Generated outputs have LF line endings only
- [ ] Test: After regeneration, golden tests still pass (sanity check)
- [ ] File location: `core/converter/scripts/regenerate-golden-tests.sh`
</ac-block>

---

## Pack G: Testing & Closeout

### PR-25: Unit Test Coverage

**Context (Human):** Comprehensive unit tests for every component. Target: >90% coverage for pure logic module.

<ac-block id="S4-PR25-AC1">
**Acceptance Criteria for PR25 (Unit Tests)**:
- [ ] Test suite for each component:
  - EpsilonComparison: 10+ tests
  - NodeClustering: 20+ tests (including chain cases)
  - Each pipeline phase: 10+ tests per phase
  - ConversionPipeline: 15+ integration tests
  - Line ending normalization: 10+ tests
- [ ] Edge cases covered:
  - Empty drawings (0 nodes, 0 members)
  - Single node, single member
  - All nodes coincident (merge to 1)
  - Disconnected components
  - Very large coordinates (1,000,000mm)
  - Very small epsilon values (1e-9)
- [ ] Error paths covered:
  - Missing scale
  - Invalid scale (zero, negative, NaN)
  - Broken references
  - Degenerate geometry
  - Invalid coordinates (NaN, Infinity)
- [ ] Overall code coverage >90%
- [ ] All tests pass: `./gradlew :core:converter:test`
- [ ] Test execution time <30 seconds (fast feedback)
- [ ] File locations: `core/converter/src/test/kotlin/` (mirror package structure)
</ac-block>

---

### PR-26: Property-Based Testing (Kotest)

**Context (Human):** Use property-based testing to find edge cases. Generate random drawings, verify invariants.

<ac-block id="S4-PR26-AC1">
**Acceptance Criteria for PR26 (Property Tests)**:
- [ ] Use Kotest's property testing: `checkAll(Arb.drawing2d()) { drawing -> ... }`
- [ ] Property: Node count after merging ≤ node count before merging
- [ ] Property: Member count after removing degenerates ≤ member count before
- [ ] Property: All member references valid after conversion (no broken references)
- [ ] Property: All coordinates finite in output model
- [ ] Property: Conversion is deterministic (same input → same output, always)
- [ ] Property: Pipeline order matters (scaling then merging ≠ merging then scaling)
- [ ] Generate 100+ random drawings per property
- [ ] Test: Properties hold for all generated drawings
- [ ] Test: If property violated, Kotest provides shrunk counterexample
- [ ] Code coverage: Property tests exercise code paths not covered by unit tests
- [ ] File location: `core/converter/src/test/kotlin/.../PropertyBasedTests.kt`
</ac-block>

---

### PR-27: Performance Benchmarks

**Context (Human):** Verify conversion performance. Should be fast enough for real-time use (user waits for conversion).

<ac-block id="S4-PR27-AC1">
**Acceptance Criteria for PR27 (Benchmarks)**:
- [ ] Benchmark: Convert drawing with 100 nodes, 150 members → complete in <100ms
- [ ] Benchmark: Convert drawing with 1,000 nodes, 1,500 members → complete in <500ms
- [ ] Benchmark: Convert drawing with 10,000 nodes, 15,000 members → complete in <5 seconds
- [ ] Benchmark: Node clustering on 10,000 nodes → complete in <1 second (O(N log N) verified)
- [ ] Benchmark: JSON serialization of large model → complete in <500ms
- [ ] Use JMH or simple timing with multiple iterations (warmup + measure)
- [ ] Document results in `/docs/converter/PERFORMANCE.md`
- [ ] Test: All benchmarks meet target times on reference hardware (mid-range laptop)
- [ ] File location: `core/converter/src/test/kotlin/.../PerformanceBenchmarks.kt`
</ac-block>

---

### PR-28: Cross-Platform Determinism Test

**Context (Human):** THE MOST CRITICAL TEST: Verify conversion output identical on Windows, Mac, Linux, ARM, x86.

<ac-block id="S4-PR28-AC1">
**Acceptance Criteria for PR28 (Cross-Platform)**:
- [ ] Test setup: Run golden tests on multiple platforms:
  - Linux x86_64 (GitHub Actions ubuntu-latest)
  - Linux ARM64 (GitHub Actions ubuntu-latest on ARM runner, if available)
  - macOS x86_64 (GitHub Actions macos-latest)
  - macOS ARM64 (GitHub Actions macos-latest-xlarge)
  - Windows x86_64 (GitHub Actions windows-latest)
- [ ] For each platform:
  1. Run all golden tests
  2. Compute SHA256 of each output
  3. Compare SHA256 across platforms
- [ ] Test: SHA256 hashes IDENTICAL across all 5 platforms
- [ ] Test: Golden tests pass on all platforms (no diffs)
- [ ] If any platform differs, fail CI with detailed diff
- [ ] Document in README: "This converter guarantees byte-for-byte identical output across all platforms"
- [ ] CI configuration: `.github/workflows/cross-platform-test.yml`
- [ ] File location: GitHub Actions workflow file
</ac-block>

---

### PR-29: Documentation Complete

**Context (Human):** Comprehensive docs for converter module.

<ac-block id="S4-PR29-AC1">
**Acceptance Criteria for PR29 (Docs)**:
- [ ] Create `/docs/converter/` directory with files:
  - `PIPELINE_ORDER.md` (from PR-21)
  - `PERFORMANCE.md` (from PR-27)
  - `DETERMINISM.md` (explain how determinism achieved)
  - `TROUBLESHOOTING.md` (common errors and solutions)
- [ ] `DETERMINISM.md` must explain:
  - Why distance rounding (ARM/x86 FPU differences)
  - Why spatial clustering (not pairwise)
  - Why sorted IDs for centroid
  - Why CRLF normalization
  - Why fixed pipeline order
  - How golden tests verify determinism
- [ ] `TROUBLESHOOTING.md` must include:
  - "NoScale error" → how to set scale in editor
  - "DegenerateGeometry error" → user has coincident nodes, how to fix
  - "BrokenReference error" → data corruption, re-export drawing
  - "InvalidCoordinate error" → NaN/Infinity in input, check editor
- [ ] All docs have examples and code snippets
- [ ] Markdown valid, no broken links
- [ ] File locations: `/docs/converter/`
</ac-block>

---

### PR-30: Sprint 4 Closeout

**Context (Human):** Final verification before Sprint 4.5 (backend integration).

<ac-block id="S4-PR30-AC1">
**Acceptance Criteria for PR30 (Closeout)**:
- [ ] All 30 PRs merged to main branch
- [ ] Code coverage: `core-converter` module >90%
- [ ] Ktlint check passes: `./gradlew ktlintCheck` with 0 errors
- [ ] All unit tests pass: `./gradlew :core:converter:test`
- [ ] All golden tests pass on Linux x86_64
- [ ] All golden tests pass on macOS ARM64 (M1/M2)
- [ ] All golden tests pass on Windows x86_64
- [ ] Cross-platform determinism verified (SHA256 hashes match)
- [ ] Performance benchmarks pass (10K nodes in <5 sec)
- [ ] Property-based tests pass (100+ random drawings)
- [ ] No TODO comments in production code
- [ ] NO `android.*` imports (verified by grep)
- [ ] Update root README.md with Sprint 4 deliverables
- [ ] Documentation complete: 4 docs in `/docs/converter/`
- [ ] Git tag created: `git tag sprint-4-complete`
- [ ] Ready for Sprint 4.5: StructuralModel can be sent to FastAPI backend
</ac-block>

---

## Sprint 4 Success Metrics

**Definition of Done (Sprint Level):**
- ✅ All 30 PRs completed and merged
- ✅ Drawing2D → StructuralModel conversion working
- ✅ Node merging via spatial clustering (deterministic)
- ✅ Degenerate geometry detection (hard fail)
- ✅ 6-phase pipeline with fixed order
- ✅ CRLF normalization (cross-platform determinism)
- ✅ Golden tests pass on all platforms (Windows, Mac, Linux)
- ✅ Cross-platform determinism verified (byte-for-byte)
- ✅ Performance: 10K nodes in <5 seconds
- ✅ Code coverage >90%
- ✅ Pure JVM (no Android dependencies)
- ✅ Ready for Sprint 4.5 (backend sync)

**Key Deliverables:**
1. `core-converter` module (Pure JVM/Kotlin)
2. StructuralModel data classes
3. Spatial clustering algorithm (O(N log N), deterministic)
4. Epsilon comparison with distance rounding
5. 6-phase conversion pipeline
6. CRLF normalization for JSON
7. Golden test suite (4+ fixtures)
8. Cross-platform determinism verification
9. Property-based testing
10. Performance benchmarks
11. Comprehensive documentation

**Technical Debt:**
- Z-coordinate hardcoded to 0.0 (future: multi-story support)
- Profile validation not implemented (assumes profiles valid from editor)
- Member length stored but not used (future: structural analysis)
- No support for curved members (only straight lines)

**Architectural Decisions Applied:**
- ARCH-MATH-001: Double precision, round before epsilon comparison
- ARCH-PERF-001: Spatial clustering O(N log N), not pairwise O(N²)
- Pure JVM (no Android dependencies)
- Fixed pipeline order prevents emergent bugs
- Sorted IDs for centroid prevents non-associative float drift
- CRLF normalization ensures cross-platform determinism
- Hard fail on degenerate geometry (not silent skip)
- Golden tests as contract (byte-for-byte output)

---

## Notes for Developers

**Critical Implementation Details:**
1. **Distance Rounding**: MUST round distances before epsilon comparison (ARM/x86 stability)
2. **Spatial Clustering**: Use sorted IDs when computing centroid (prevents drift)
3. **Chain Merging**: A---B---C must produce 1 cluster (test this explicitly)
4. **Degenerate Geometry**: Hard fail, don't silently skip (alerts user to problem)
5. **Pipeline Order**: Fixed 6-phase order, enforced at runtime
6. **CRLF**: Force LF in all JSON output (cross-platform determinism)
7. **Golden Tests**: Byte-for-byte comparison, must pass on all platforms
8. **Pure JVM**: NO Android dependencies (must run on server/CI)

**Dependencies Between PRs:**
- PR-02 (StructuralModel) foundational for all later PRs
- PR-03 (Errors) used throughout pipeline
- PR-04 (Degenerate check) used in Phase 4
- PR-05, PR-06, PR-07 (Epsilon, rounding, clustering) sequential
- PR-08 (Chain test) validates PR-07
- PR-09 (Merge applier) depends on PR-07
- PR-10 through PR-16 (Pipeline phases) sequential
- PR-17, PR-18, PR-19 (CRLF) sequential
- PR-22 (Fixtures) before PR-23 (Golden tests)
- PR-28 (Cross-platform) requires all previous PRs complete

**Parallel Work Opportunities:**
- Pack A (module setup, models, errors) can be quick start
- Pack B (node merging) can develop independently once PR-02 done
- Pack C (pipeline phases) sequential but can parallelize with Pack D (CRLF)
- Pack F (golden tests) can start once pipeline working
- Pack G (testing) final verification phase

**Testing Strategy:**
- Unit tests: Every function, edge cases, error paths
- Golden tests: Byte-for-byte output verification
- Property tests: Random inputs, invariant checking
- Performance tests: Large drawings, timing benchmarks
- Cross-platform tests: CI on Windows, Mac, Linux, ARM

**Determinism Checklist:**
- [ ] Distance rounded before epsilon comparison
- [ ] Sorted IDs for centroid calculation
- [ ] Spatial clustering (not order-dependent pairwise)
- [ ] Fixed pipeline phase order
- [ ] CRLF normalized to LF
- [ ] Sorted collections in JSON output
- [ ] Rounded Double values in JSON
- [ ] Golden tests pass on all platforms

---

*Last updated: 2026-02-26*
*Sprint status: Ready for implementation*
*Previous sprint: Sprint 3 - Manual Editor*
*Next sprint: Sprint 4.5 - Backend Integration (FastAPI)*
