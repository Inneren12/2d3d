# Sprint 3: Manual Editor - Interactive Drawing Editor with MVI Architecture

**Status:** Ready
**Dates:** 2026-04-23 — 2026-06-04
**Duration:** 5-6 weeks
**Team:** 2-3 Android developers (1 senior for architecture)
**Goal:** Build high-performance interactive editor for adding nodes, members, setting scale, with 60 FPS performance at 10,000 entities using QuadTree spatial indexing and split MVI state management.

**Key Deliverables:**
- `feature-editor` module with custom Canvas rendering
- MVI architecture with Domain/Transient state split
- QuadTree spatial indexing for O(log N) hit testing
- Scale tool for physical measurements
- Node/Member creation and editing
- Profile assignment UI
- Undo/Redo with delta patches
- Room database with File-Backed storage pattern
- Process Death recovery
- 60 FPS @ 10,000 entities

**Critical Architecture Applied:**
- ARCH-STATE-004: Room as SSOT (Single Source of Truth), MVI writes patches to DB
- ARCH-ROOM-003: File-Backed Room for large drawings (>2MB JSON in file, path in DB)
- ARCH-PERF-003: QuadTree operations in Dispatchers.Default with atomic reference swap
- ARCH-MATH-001: Double for coordinates, Float only for Canvas rendering

---

## Pack A: Editor Module Foundation

### PR-01: Module `feature-editor` Setup

**Context (Human):** Create the editor module with Room database, Navigation, and Hilt dependency injection. This is the core interactive UI module.

<ac-block id="S3-PR01-AC1">
**Acceptance Criteria for PR01 (Module Setup)**:
- [ ] Create module `feature-editor` in `/feature/editor/` directory
- [ ] Module path in settings.gradle.kts: `include(":feature:editor")`
- [ ] Dependencies: Room, Navigation Component, Hilt, Coroutines, Compose or View-based UI
- [ ] Room database schema version 1 with entities defined (will populate in later PRs)
- [ ] Module compiles: `./gradlew :feature:editor:assembleDebug`
- [ ] Empty instrumentation test runs: `./gradlew :feature:editor:connectedAndroidTest`
- [ ] Add dependencies to `:core:drawing2d`, `:core:storage`, `:core:validation`
- [ ] Create basic AndroidManifest.xml
- [ ] File location: `/feature/editor/build.gradle.kts`
</ac-block>

---

### PR-02: UUIDv7 ID Generation (Timestamp-Based)

**Context (Human):** Generate deterministic, sortable UUIDs for entities. UUIDv7 includes timestamp prefix, allowing chronological sorting and better database indexing. Critical for offline-first architecture and conflict resolution.

<ac-block id="S3-PR02-AC1">
**Acceptance Criteria for PR02 (UUIDv7)**:
- [ ] Implement `object IdGenerator` with `fun generate(): String` returning UUIDv7
- [ ] UUIDv7 format: First 48 bits = Unix timestamp (milliseconds), remaining bits = random
- [ ] Implementation: `val timestamp = System.currentTimeMillis(); val uuid = UUID.nameUUIDFromBytes(...)` with proper bit manipulation
- [ ] Alternative: Use library like `com.github.f4b6a3:uuid-creator` for RFC 9562 compliance
- [ ] Generated IDs are lexicographically sortable by creation time
- [ ] IDs are globally unique (collision probability < 1 in 10^18)
- [ ] Test: Generate 10,000 IDs, verify all unique
- [ ] Test: Generate IDs sequentially, verify lexicographic order matches chronological order
- [ ] Test: ID format matches UUIDv7 spec (timestamp prefix)
- [ ] Documentation: Explain why UUIDv7 (offline-first, sortability, DB index efficiency)
- [ ] Code coverage >90%
- [ ] File location: `feature/editor/src/main/kotlin/.../util/IdGenerator.kt`
</ac-block>

---

### PR-03: EditorScreen Composable/Fragment Shell

**Context (Human):** Basic editor screen container with toolbar, canvas area, and bottom toolbar. No rendering yet, just UI structure.

<ac-block id="S3-PR03-AC1">
**Acceptance Criteria for PR03 (Screen Shell)**:
- [ ] Create `EditorScreen` composable or `EditorFragment` with basic layout
- [ ] Layout structure:
  - Top toolbar: Back button, project name, undo/redo buttons
  - Center canvas area: Will contain custom drawing view
  - Bottom toolbar: Tool selection (node, member, scale, select)
  - FAB: Save button
- [ ] Navigation: Screen receives `projectId: String` as argument
- [ ] ViewModel: `EditorViewModel` created with Hilt injection
- [ ] ViewModel loads project on init (placeholder for now)
- [ ] UI shows loading state while project loading
- [ ] UI shows error state if project not found
- [ ] Test: Screen renders with all UI elements visible
- [ ] Test: ViewModel receives correct projectId from navigation args
- [ ] File location: `feature/editor/src/main/kotlin/.../ui/EditorScreen.kt`
</ac-block>

---

### PR-04: Custom Canvas View (Empty)

**Context (Human):** Custom view for drawing with onDraw() override. Will implement rendering later. For now, just set up the drawing surface with touch handling scaffold.

<ac-block id="S3-PR04-AC1">
**Acceptance Criteria for PR04 (Canvas View)**:
- [ ] Create `class DrawingCanvasView(context: Context, attrs: AttributeSet?) : View(context, attrs)`
- [ ] Override `onDraw(canvas: Canvas)` with background color
- [ ] Override `onTouchEvent(event: MotionEvent)` with placeholder (log events)
- [ ] Handle view sizing: `onSizeChanged()` callback
- [ ] Set up Paint objects for later rendering (member as val, initialize once)
- [ ] Enable hardware acceleration: `setLayerType(LAYER_TYPE_HARDWARE, null)`
- [ ] Test: View renders without crash
- [ ] Test: Touch events logged correctly
- [ ] Test: View handles size changes
- [ ] File location: `feature/editor/src/main/kotlin/.../ui/DrawingCanvasView.kt`
</ac-block>

---

## Pack B: MVI State Architecture (Critical for Performance)

### PR-05: EditorState Data Classes

**Context (Human):** Define the state model. CRITICAL: Split into Domain state (persisted) and Transient state (ephemeral) to avoid GC pressure during drag operations (ARCH-STATE-004).

<ac-block id="S3-PR05-AC1">
**Acceptance Criteria for PR05 (State Model)**:
- [ ] Create `data class DomainState` containing:
  - `drawing: Drawing2D` (from Sprint 1)
  - `selectedNodeIds: Set<String>`
  - `selectedMemberIds: Set<String>`
- [ ] Create `data class TransientState` containing:
  - `dragOffset: Point2D?` (current drag delta, null when not dragging)
  - `hoveredEntityId: String?` (entity under cursor/finger)
  - `isLoading: Boolean`
  - `errorMessage: String?`
- [ ] Create `data class EditorState(val domain: DomainState, val transient: TransientState)`
- [ ] CRITICAL: `TransientState` is NOT persisted to Room (ARCH-STATE-004)
- [ ] CRITICAL: During drag, only `transient.dragOffset` changes (domain untouched) to prevent GC pressure
- [ ] All coordinate types use `Double` (ARCH-MATH-001)
- [ ] Test: Serialization of `DomainState` (must be serializable)
- [ ] Test: `TransientState` is lightweight (size < 100 bytes)
- [ ] Test: Modifying `transient` doesn't modify `domain` (immutability)
- [ ] Documentation: Explain Domain/Transient split rationale
- [ ] File location: `feature/editor/src/main/kotlin/.../model/EditorState.kt`
</ac-block>

---

### PR-06: EditorIntent Sealed Class

**Context (Human):** Define all user intents (actions) in the editor. MVI pattern: UI sends intents, reducer produces new state.

<ac-block id="S3-PR06-AC1">
**Acceptance Criteria for PR06 (Intents)**:
- [ ] Create sealed class `EditorIntent` with subtypes:
  - `LoadProject(projectId: String)`
  - `AddNode(position: Point2D)`
  - `DeleteNode(nodeId: String)`
  - `MoveNode(nodeId: String, newPosition: Point2D)`
  - `StartDrag(entityId: String, startPosition: Point2D)`
  - `UpdateDrag(currentPosition: Point2D)`
  - `EndDrag`
  - `AddMember(startNodeId: String, endNodeId: String)`
  - `DeleteMember(memberId: String)`
  - `SelectEntity(entityId: String, addToSelection: Boolean)`
  - `ClearSelection`
  - `SetScale(pixelDistance: Double, realLengthMm: Double)`
  - `Undo`
  - `Redo`
- [ ] All intents are immutable data classes/objects
- [ ] Intents contain only necessary data (no logic)
- [ ] Test: All intents instantiate correctly
- [ ] Test: Intents are serializable (for state restoration)
- [ ] File location: `feature/editor/src/main/kotlin/.../model/EditorIntent.kt`
</ac-block>

---

### PR-07: EditorReducer (Pure Function)

**Context (Human):** Pure reducer function that takes (State, Intent) → NewState. No side effects, no async, just state transformation. This is the heart of MVI.

<ac-block id="S3-PR07-AC1">
**Acceptance Criteria for PR07 (Reducer)**:
- [ ] Create `class EditorReducer` with `fun reduce(state: EditorState, intent: EditorIntent): EditorState`
- [ ] Function is pure: same inputs always produce same output
- [ ] No side effects: no DB writes, no network calls, no logging (only state transformation)
- [ ] Handle each intent type:
  - `AddNode`: Add to `domain.drawing.nodes`, generate ID via `IdGenerator`
  - `DeleteNode`: Remove from nodes, also remove connected members (cascading)
  - `MoveNode`: Update node position in domain
  - `StartDrag`: Set `transient.dragOffset = Point2D(0, 0)`, remember dragged entity
  - `UpdateDrag`: Update `transient.dragOffset` only (domain unchanged)
  - `EndDrag`: Apply dragOffset to domain, clear transient
  - `SelectEntity`: Update `domain.selectedNodeIds` or `selectedMemberIds`
  - Other intents similarly
- [ ] Return new state (immutable), never mutate input state
- [ ] Test: Each intent produces expected state change
- [ ] Test: Reducer is deterministic (same input → same output)
- [ ] Test: Input state never modified (immutability)
- [ ] Test: Drag operations only modify transient state (domain unchanged during drag)
- [ ] Code coverage >95%
- [ ] File location: `feature/editor/src/main/kotlin/.../mvi/EditorReducer.kt`
</ac-block>

---

### PR-08: MVI State Split - Domain/Transient (Performance Critical)

**Context (Human):** CRITICAL ARCHITECTURE: Implement the Domain/Transient state split to prevent GC pressure during drag. This is the key to 60 FPS performance (ARCH-STATE-004).

<ac-block id="S3-PR08-AC1">
**Acceptance Criteria for PR08 (State Split)**:
- [ ] Refactor `EditorReducer` to handle drag intents specially:
  - `StartDrag`: Only modify `transient` state, copy domain as-is
  - `UpdateDrag`: Only modify `transient.dragOffset`, domain unchanged
  - `EndDrag`: Merge transient into domain (apply dragOffset to node position), clear transient
- [ ] During drag (StartDrag → UpdateDrag → EndDrag), `domain` state immutable (no allocations)
- [ ] Only `transient` state changes rapidly during drag (lightweight object)
- [ ] Test: During 1000 UpdateDrag intents, `domain` reference unchanged
- [ ] Test: After EndDrag, domain contains updated node position
- [ ] Performance test: 5 sec continuous drag (60 FPS = 300 frames), measure GC pauses
- [ ] Performance test: GC pauses must be <16ms (no frame drops)
- [ ] Memory profiling: Drag operation allocates <1KB total (proof of no GC pressure)
- [ ] Documentation: Explain why split prevents GC pressure during drag
- [ ] File location: Update `EditorReducer.kt`
</ac-block>

---

## Pack C: Room Database (SSOT Architecture)

### PR-09: Room Entity - EditorProject

**Context (Human):** Define Room entity for storing editor project. CRITICAL: Use File-Backed pattern for large drawings (ARCH-ROOM-003). Store file path, not JSON blob.

<ac-block id="S3-PR09-AC1">
**Acceptance Criteria for PR09 (Room Entity)**:
- [ ] Create `@Entity` data class `EditorProjectEntity` with fields:
  - `@PrimaryKey val projectId: String`
  - `val name: String`
  - `val drawingFilePath: String` (absolute path to drawing JSON file)
  - `val rectifiedImagePath: String` (path to rectified.png from Sprint 2)
  - `val createdAt: Long`
  - `val updatedAt: Long`
  - `val version: Int` (for sync conflict resolution, will use in Sprint 4.5)
- [ ] CRITICAL: NO `drawingJson: String` field (would exceed 2MB CursorWindow limit)
- [ ] File-Backed pattern: Drawing JSON stored in `Context.filesDir/projects/{projectId}/drawing.json`
- [ ] Room stores only metadata and file path
- [ ] Test: Entity compiles and Room schema generation succeeds
- [ ] Test: Entity size <1KB (lightweight)
- [ ] Documentation: Explain File-Backed pattern rationale (ARCH-ROOM-003)
- [ ] File location: `feature/editor/src/main/kotlin/.../data/EditorProjectEntity.kt`
</ac-block>

---

### PR-10: Room DAO - Basic CRUD

**Context (Human):** Data Access Object for Room operations. No complex logic here, just basic queries.

<ac-block id="S3-PR10-AC1">
**Acceptance Criteria for PR10 (DAO)**:
- [ ] Create `@Dao interface EditorProjectDao` with methods:
  - `@Query("SELECT * FROM editor_projects WHERE projectId = :id") fun observeProject(id: String): Flow<EditorProjectEntity?>`
  - `@Insert(onConflict = REPLACE) suspend fun insert(project: EditorProjectEntity)`
  - `@Update suspend fun update(project: EditorProjectEntity)`
  - `@Delete suspend fun delete(project: EditorProjectEntity)`
  - `@Query("SELECT * FROM editor_projects ORDER BY updatedAt DESC") fun getAllProjects(): Flow<List<EditorProjectEntity>>`
- [ ] All suspend functions for coroutines
- [ ] `observeProject()` returns Flow (reactive updates)
- [ ] Test: Each DAO method using Room in-memory database
- [ ] Test: Flow emits updates when entity changed
- [ ] Test: REPLACE strategy handles duplicate inserts correctly
- [ ] File location: `feature/editor/src/main/kotlin/.../data/EditorProjectDao.kt`
</ac-block>

---

### PR-11: EditorRepository - File-Backed Storage

**Context (Human):** Repository layer that orchestrates Room + file system. Implements File-Backed pattern: metadata in Room, JSON in files.

<ac-block id="S3-PR11-AC1">
**Acceptance Criteria for PR11 (Repository)**:
- [ ] Create `class EditorRepository(dao: EditorProjectDao, context: Context)`
- [ ] Method: `fun observeDrawing(projectId: String): Flow<Drawing2D?>` that:
  1. Observes `dao.observeProject(projectId)`
  2. When entity changes, loads JSON from `drawingFilePath`
  3. Parses JSON to `Drawing2D`
  4. Emits to Flow
- [ ] Method: `suspend fun saveDrawing(projectId: String, drawing: Drawing2D)` that:
  1. Serializes `drawing` to JSON using `drawing.toJsonStable()` from Sprint 1
  2. Writes JSON to file using `AtomicFileWriter`
  3. Updates `EditorProjectEntity.updatedAt` and `version++`
  4. Calls `dao.update(entity)`
- [ ] Method: `suspend fun createProject(name: String, rectifiedImagePath: String): String` that:
  1. Generates new projectId via `IdGenerator`
  2. Creates empty `Drawing2D` with default page size from rectified image dimensions
  3. Saves drawing to file
  4. Inserts entity to Room
  5. Returns projectId
- [ ] All file operations in `Dispatchers.IO`
- [ ] Handle file read/write errors gracefully (return null or throw)
- [ ] Test: Save/load cycle preserves drawing data
- [ ] Test: File path stored correctly in Room
- [ ] Test: Large drawing (10,000 nodes) saves/loads without CursorWindow error
- [ ] Test: Concurrent saves are serialized (no race conditions)
- [ ] Code coverage >85%
- [ ] File location: `feature/editor/src/main/kotlin/.../data/EditorRepository.kt`
</ac-block>

---

### PR-12: Process Death Recovery with NonCancellable

**Context (Human):** CRITICAL: Handle Android Process Death (low memory kill). When user returns, app must restore exact state without data loss (ARCH-ROOM-003 + ARCH-STATE-004).

<ac-block id="S3-PR12-AC1">
**Acceptance Criteria for PR12 (Process Death)**:
- [ ] Save current editor state to Room on every significant change (not every frame)
- [ ] Use debouncing: Save at most once per 500ms (avoid excessive DB writes)
- [ ] On Process Death, Room database survives (persistent storage)
- [ ] On app restart, load last saved state from Room
- [ ] CRITICAL: Use `NonCancellable` context for final save in `ViewModel.onCleared()`:
```kotlin
  override fun onCleared() {
      viewModelScope.launch(NonCancellable) {
          repository.saveDrawing(projectId, currentState.domain.drawing)
      }
  }
```
- [ ] Test: Simulate Process Death (android:process="@null" flag), verify state restored
- [ ] Test: Unsaved changes persisted via onCleared save
- [ ] Test: File-Backed storage survives Process Death (file still exists)
- [ ] Instrumentation test: Kill process, restart app, verify drawing intact
- [ ] Documentation: Explain NonCancellable rationale (coroutine not cancelled during onCleared)
- [ ] File location: Update `EditorViewModel.kt`
</ac-block>

---

### PR-12.5: Room Recovery from Corruption

**Context (Human):** Handle corrupted Room database (rare but catastrophic if not handled). Provide recovery mechanism.

<ac-block id="S3-PR12.5-AC1">
**Acceptance Criteria for PR12.5 (Room Recovery)**:
- [ ] Implement Room fallback strategy in database builder:
```kotlin
  Room.databaseBuilder(context, AppDatabase::class.java, "editor.db")
      .fallbackToDestructiveMigration() // Last resort for corrupted schema
      .addCallback(object : RoomDatabase.Callback() {
          override fun onCorruptionDetected(db: SupportSQLiteDatabase) {
              // Log corruption event
              // Attempt to recover from file-backed JSON (files survive DB corruption)
          }
      })
```
- [ ] On corruption detected: Rebuild database from file-backed JSON files
- [ ] Corruption recovery process:
  1. Scan `Context.filesDir/projects/` for drawing JSON files
  2. Parse each file
  3. Recreate `EditorProjectEntity` entries
  4. Insert into new database
- [ ] User notified if data loss occurred (toast/dialog)
- [ ] Test: Simulate corrupted database (delete DB file), verify recovery
- [ ] Test: Recovery rebuilds database from JSON files correctly
- [ ] Test: Corrupted JSON files skipped (don't crash recovery)
- [ ] File location: `feature/editor/src/main/kotlin/.../data/AppDatabase.kt`
</ac-block>

---

## Pack D: Coordinate Systems & Camera

### PR-09: Coordinate System Documentation + Matrix Storage

**Context (Human):** CRITICAL: Define and document coordinate spaces. Store camera matrix components separately to avoid accumulation errors during pan/zoom (ARCH recommendation from deep dive).

<ac-block id="S3-PR09A-AC1">
**Acceptance Criteria for PR09A (Coordinates)**:
- [ ] Create `/docs/editor/COORDINATE_SYSTEMS.md` documenting:
  - Drawing Space: Coordinates in millimeters (origin at drawing top-left)
  - Screen Space: Pixels on device screen (origin at view top-left)
  - Transformations: Drawing → Screen via camera matrix (scale, translate, no rotation)
- [ ] Create `data class CameraState` with:
  - `val scale: Double` (zoom factor, 1.0 = 1 drawing mm = 1 screen px)
  - `val offsetX: Double` (pan offset in drawing mm)
  - `val offsetY: Double` (pan offset in drawing mm)
- [ ] CRITICAL: Store primitives (scale, offsetX, offsetY), NOT full Matrix
- [ ] Rationale: Matrix accumulation causes drift over 1000+ operations (ARCH recommendation)
- [ ] Build matrix on-demand in `onDraw()`:
```kotlin
  val matrix = Matrix().apply {
      postScale(scale.toFloat(), scale.toFloat())
      postTranslate(offsetX.toFloat(), offsetY.toFloat())
  }
```
- [ ] Matrix inverse computed fresh each frame (for touch → drawing coordinate conversion)
- [ ] Test: 1000 pan/zoom operations, verify drift <0.5px (no accumulation)
- [ ] Test: Matrix rebuilt from primitives matches expected transformation
- [ ] Test: Matrix inverse is valid (determinant != 0), or handle singular matrix gracefully
- [ ] Documentation: Explain why primitives (not accumulated matrix)
- [ ] File locations:
  - `/docs/editor/COORDINATE_SYSTEMS.md`
  - `feature/editor/src/main/kotlin/.../model/CameraState.kt`
</ac-block>

---

### PR-13: Matrix Singularity Handling

**Context (Human):** CRITICAL: Matrix.invert() can return null if matrix is singular (determinant = 0). This happens at extreme zoom (scale → 0) or degenerate states. Must handle gracefully.

<ac-block id="S3-PR13-AC1">
**Acceptance Criteria for PR13 (Singularity)**:
- [ ] Add check in touch event handler:
```kotlin
  val inverseMatrix = Matrix()
  if (!cameraMatrix.invert(inverseMatrix)) {
      // Singular matrix - touch events blocked
      return false // Don't process touch
  }
```
- [ ] When matrix singular: Block all touch interactions (return false from onTouchEvent)
- [ ] Show warning to user: "Zoom limit reached" (toast or overlay)
- [ ] Prevent scale from reaching 0 or extreme values (min=0.01, max=100.0)
- [ ] Test: Set scale to 0.0, verify matrix.invert() returns false
- [ ] Test: Touch events blocked when matrix singular
- [ ] Test: Scale clamped to valid range (0.01 to 100.0)
- [ ] Test: User receives feedback when limit hit
- [ ] Code coverage >90% for singularity handling code
- [ ] File location: Update `DrawingCanvasView.kt`
</ac-block>

---

### PR-14: Pan Gesture (Drag with One Finger)

**Context (Human):** Implement pan (move viewport) with single-finger drag when no tool selected. Update CameraState.offset, rebuild matrix.

<ac-block id="S3-PR14-AC1">
**Acceptance Criteria for PR14 (Pan)**:
- [ ] Detect single-finger drag in `onTouchEvent()`
- [ ] Calculate delta: `(currentX - previousX, currentY - previousY)` in screen pixels
- [ ] Convert delta to drawing space: `delta / scale`
- [ ] Update CameraState: `offsetX += deltaDrawingX`, `offsetY += deltaDrawingY`
- [ ] Emit intent: `PanCamera(deltaX, deltaY)` or update transient state directly
- [ ] Matrix rebuilt from new primitives in next frame
- [ ] Pan only works when no tool selected (otherwise tool takes priority)
- [ ] Test: Drag finger, verify offset updates correctly
- [ ] Test: 100 pan operations, verify no drift (primitives don't accumulate error)
- [ ] Test: Pan blocked when tool active (e.g., adding node)
- [ ] Performance: Pan gesture at 60 FPS (no frame drops)
- [ ] File location: Update `DrawingCanvasView.kt`
</ac-block>

---

### PR-15: Pinch Zoom Gesture

**Context (Human):** Implement pinch-to-zoom with two fingers. Update CameraState.scale, rebuild matrix. Zoom toward pinch center (not screen center).

<ac-block id="S3-PR15-AC1">
**Acceptance Criteria for PR15 (Zoom)**:
- [ ] Use `ScaleGestureDetector` for pinch detection
- [ ] Calculate scale factor: `newScale = oldScale * detector.scaleFactor`
- [ ] Clamp scale: `scale.coerceIn(0.01, 100.0)` (prevent extreme zoom)
- [ ] Zoom toward focus point (pinch center):
```kotlin
  val focusDrawing = screenToDrawing(detector.focusX, detector.focusY)
  // Update scale
  val focusScreen = drawingToScreen(focusDrawing)
  // Adjust offset so focusDrawing stays at focusScreen
```
- [ ] Emit intent: `ZoomCamera(newScale, focusPoint)`
- [ ] Test: Pinch gesture updates scale correctly
- [ ] Test: Zoom toward pinch center (not screen center)
- [ ] Test: Scale clamped to valid range
- [ ] Test: 100 zoom operations, no drift (primitives stable)
- [ ] Performance: Zoom at 60 FPS
- [ ] File location: Update `DrawingCanvasView.kt`
</ac-block>

---

## Pack E: Spatial Indexing (Performance Critical)

### PR-10.5: QuadTree Implementation with Free Agents Strategy

**Context (Human):** CRITICAL PERFORMANCE: Naive O(N) hit testing fails at 10,000 entities. QuadTree provides O(log N) spatial queries. Free Agents strategy prevents UI freeze during tree rebuild (ARCH-PERF-003).

<ac-block id="S3-PR10.5-AC1">
**Acceptance Criteria for PR10.5 (QuadTree + Free Agents)**:
- [ ] Implement `class QuadTree<T>` with methods:
  - `fun insert(bounds: RectF, item: T)`
  - `fun query(region: RectF): List<T>` - O(log N) spatial query
  - `fun nearest(point: Point2D, maxDistance: Double): T?` - find closest item
  - `fun rebuild(items: List<Pair<RectF, T>>): QuadTree<T>` - create new tree (immutable)
- [ ] Tree parameters: max depth = 8, max items per node = 10 (tunable)
- [ ] Free Agents Strategy (ARCH-PERF-003):
  1. During drag start: Remove dragged entities from QuadTree → `freeAgents` list
  2. During drag: `freeAgents` checked linearly (only dragged items, typically <10)
  3. QuadTree still used for non-dragged entities (O(log N))
  4. During drag end: Rebuild QuadTree with all entities in `Dispatchers.Default` thread
  5. Atomically swap old tree with new tree via `AtomicReference`
- [ ] Tree rebuild NEVER on Main thread (ARCH-PERF-003)
- [ ] Test: Insert 10,000 items, query small region, verify O(log N) time (<10ms)
- [ ] Test: Nearest neighbor search on 10,000 items completes <5ms
- [ ] Test: During drag, query still works (uses freeAgents + tree)
- [ ] Test: After drag end, tree rebuilt correctly (all items present)
- [ ] Performance test: Tree rebuild for 10,000 items completes <100ms on background thread
- [ ] Performance test: Query during tree rebuild doesn't block (uses old tree until swap)
- [ ] Code coverage >85%
- [ ] Documentation: Explain Free Agents strategy and why it prevents UI freeze
- [ ] File location: `feature/editor/src/main/kotlin/.../spatial/QuadTree.kt`
</ac-block>

---

### PR-16: Hit Testing with QuadTree

**Context (Human):** Use QuadTree for fast hit testing (what entity is under finger?). Query only nearby region, not all entities.

<ac-block id="S3-PR16-AC1">
**Acceptance Criteria for PR16 (Hit Test)**:
- [ ] Implement `fun hitTest(point: Point2D): String?` that:
  1. Defines search region: `RectF(point.x - hitRadius, point.y - hitRadius, point.x + hitRadius, point.y + hitRadius)`
  2. Queries QuadTree: `quadTree.query(searchRegion)` → candidates
  3. For each candidate, check precise hit (point-in-circle for nodes, point-to-segment for members)
  4. Return closest entity ID or null
- [ ] Hit radius: 20dp converted to drawing coordinates (accounts for scale)
- [ ] Prioritize nodes over members (nodes drawn on top)
- [ ] Check `freeAgents` list in addition to QuadTree (during drag)
- [ ] Test: Hit test on 10,000 entities completes <5ms (O(log N) via QuadTree)
- [ ] Test: Hit test on empty point returns null
- [ ] Test: Hit test during drag finds dragged entity (in freeAgents)
- [ ] Test: Hit radius scales with zoom (larger at low zoom, smaller at high zoom)
- [ ] Performance: 1000 hit tests on 10,000 entities, average <5ms each
- [ ] File location: Update `DrawingCanvasView.kt`
</ac-block>

---

## Pack F: Rendering Pipeline

### PR-17: Node Rendering (Canvas Circles)

**Context (Human):** Draw nodes as circles on Canvas. Use Float for rendering (ARCH-MATH-001), but coordinates stored as Double.

<ac-block id="S3-PR17-AC1">
**Acceptance Criteria for PR17 (Node Rendering)**:
- [ ] In `onDraw()`, iterate through `drawing.nodes` (from state)
- [ ] Transform each node position to screen space using camera matrix
- [ ] Draw circle: `canvas.drawCircle(screenX.toFloat(), screenY.toFloat(), radius, paint)`
- [ ] Node appearance:
  - Radius: 8dp (converted to pixels)
  - Color: Blue (#2196F3) if not selected, Orange (#FF9800) if selected
  - Stroke width: 2dp
  - Fill: White background
- [ ] Selected nodes drawn with thicker stroke (4dp)
- [ ] Use single Paint object (reuse, don't allocate per node)
- [ ] Test: 10,000 nodes render without OOM
- [ ] Test: Selected node rendered with different color
- [ ] Performance: 10,000 nodes render at 60 FPS (frame time <16ms)
- [ ] Rendering uses Float (converted from Double coordinates) - ARCH-MATH-001
- [ ] File location: Update `DrawingCanvasView.kt`
</ac-block>

---

### PR-18: Member Rendering (Canvas Lines)

**Context (Human):** Draw members as lines connecting nodes. Look up node positions from drawing.

<ac-block id="S3-PR18-AC1">
**Acceptance Criteria for PR18 (Member Rendering)**:
- [ ] Iterate through `drawing.members`
- [ ] For each member, look up start and end node positions from `drawing.nodes`
- [ ] Transform positions to screen space
- [ ] Draw line: `canvas.drawLine(startX.toFloat(), startY.toFloat(), endX.toFloat(), endY.toFloat(), paint)`
- [ ] Member appearance:
  - Color: Gray (#757575) if not selected, Orange (#FF9800) if selected
  - Stroke width: 2dp for regular, 4dp for selected
  - Line style: Solid (no dashes for MVP)
- [ ] Skip member if start or end node missing (broken reference)
- [ ] Test: Members rendered between correct nodes
- [ ] Test: Broken member reference doesn't crash rendering
- [ ] Performance: 10,000 members render at 60 FPS
- [ ] File location: Update `DrawingCanvasView.kt`
</ac-block>

---

### PR-19: Overlay Rendering (Selection Highlight, Hover)

**Context (Human):** Draw additional overlays: selection boxes, hover highlights, drag ghost.

<ac-block id="S3-PR19-AC1">
**Acceptance Criteria for PR19 (Overlays)**:
- [ ] Selection box: Draw dashed rectangle around selected entities
- [ ] Hover highlight: Draw subtle circle around hovered entity (larger radius)
- [ ] Drag ghost: While dragging, draw semi-transparent preview at new position
- [ ] Grid background: Draw grid lines at 1000mm intervals (adjusts with zoom)
- [ ] Test: Overlays rendered correctly
- [ ] Test: Overlays don't interfere with entity rendering
- [ ] Performance: Overlays add <2ms to frame time
- [ ] File location: Update `DrawingCanvasView.kt`
</ac-block>

---

## Pack G: Tool Implementation

### PR-20: Tool State Machine

**Context (Human):** Define tool states (AddNode, AddMember, Select, Scale, Pan). Each tool has different interaction behavior.

<ac-block id="S3-PR20-AC1">
**Acceptance Criteria for PR20 (Tool State)**:
- [ ] Create sealed class `EditorTool`:
  - `Pan` - Default, drag to move viewport
  - `AddNode` - Tap to add node
  - `AddMember` - Tap two nodes to connect
  - `Select` - Tap to select, drag to move
  - `SetScale` - Tap two points to define scale
- [ ] Tool state stored in `TransientState.activeTool: EditorTool`
- [ ] Tool changed via intent: `SelectTool(tool: EditorTool)`
- [ ] Bottom toolbar shows active tool (highlight selected button)
- [ ] Test: Tool state updates correctly
- [ ] Test: Tool button UI reflects active tool
- [ ] File location: `feature/editor/src/main/kotlin/.../model/EditorTool.kt`
</ac-block>

---

### PR-21: Add Node Tool

**Context (Human):** Tap empty space to add node. Node placed at tap location in drawing coordinates.

<ac-block id="S3-PR21-AC1">
**Acceptance Criteria for PR21 (Add Node)**:
- [ ] When `activeTool = AddNode` and user taps:
  1. Convert screen tap coordinates to drawing coordinates (matrix inverse)
  2. Check matrix inverse valid (not singular)
  3. Generate new node ID via `IdGenerator`
  4. Emit intent: `AddNode(position)`
  5. Reducer adds node to `domain.drawing.nodes`
- [ ] New node has default properties: no profile assigned, not selected
- [ ] Node appears immediately after tap (no lag)
- [ ] Test: Tap adds node at correct position
- [ ] Test: Node ID is unique (UUIDv7)
- [ ] Test: Multiple taps add multiple nodes
- [ ] Test: Matrix singular blocks node creation (graceful failure)
- [ ] File location: Update `DrawingCanvasView.kt` touch handling
</ac-block>

---

### PR-22: Add Member Tool

**Context (Human):** Two-tap workflow: tap first node, then tap second node to connect them with member.

<ac-block id="S3-PR22-AC1">
**Acceptance Criteria for PR22 (Add Member)**:
- [ ] State machine:
  - State 1 (idle): Tap node → store as `firstNodeId`, transition to State 2
  - State 2 (waiting): Tap different node → emit `AddMember(firstNodeId, secondNodeId)`, transition to State 1
  - State 2: Tap same node → cancel, transition to State 1
  - State 2: Tap empty space → cancel, transition to State 1
- [ ] Visual feedback: In State 2, draw dashed line from first node to cursor (rubber band)
- [ ] Member creation:
  1. Generate member ID via `IdGenerator`
  2. Reducer adds member to `domain.drawing.members`
- [ ] Prevent duplicate members: Check if member already exists between same nodes
- [ ] Test: Two-tap workflow creates member
- [ ] Test: Tap same node twice cancels (no self-loop member)
- [ ] Test: Duplicate member prevented
- [ ] Test: Visual rubber band line shown in State 2
- [ ] File location: Update `DrawingCanvasView.kt`
</ac-block>

---

### PR-23: Select & Move Tool

**Context (Human):** Tap to select entity, drag to move selected entities. Uses MVI drag pattern (StartDrag → UpdateDrag → EndDrag).

<ac-block id="S3-PR23-AC1">
**Acceptance Criteria for PR23 (Select & Move)**:
- [ ] Tap entity → emit `SelectEntity(entityId, addToSelection = false)`
- [ ] Shift+Tap (or long press) → emit `SelectEntity(entityId, addToSelection = true)` for multi-select
- [ ] Drag selected entity:
  1. ACTION_DOWN on selected entity → emit `StartDrag(entityId, startPos)`
  2. ACTION_MOVE → emit `UpdateDrag(currentPos)` (updates transient.dragOffset only)
  3. ACTION_UP → emit `EndDrag` (merges transient into domain, creates PatchOp for undo)
- [ ] During drag, render all selected entities at offset position (transient.dragOffset applied)
- [ ] After drag, nodes snapped to grid if enabled (optional enhancement)
- [ ] Test: Select entity updates selection state
- [ ] Test: Multi-select adds to selection set
- [ ] Test: Drag moves selected entities
- [ ] Test: Drag uses transient state (domain unchanged during drag)
- [ ] Test: After drag end, domain updated with new positions
- [ ] Performance: Drag at 60 FPS (transient state prevents GC pressure)
- [ ] File location: Update `DrawingCanvasView.kt`
</ac-block>

---

### PR-24: Scale Tool (Two-Tap Physical Measurement)

**Context (Human):** Define scale: tap two nodes that represent a known physical distance (e.g., 2500mm). This sets the mm-per-pixel ratio for the entire drawing.

<ac-block id="S3-PR24-AC1">
**Acceptance Criteria for PR24 (Scale Tool)**:
- [ ] Two-tap workflow:
  1. Tap first node → store as `firstNodeId`
  2. Tap second node → store as `secondNodeId`
  3. Show dialog: "Enter distance between nodes (mm)"
  4. User inputs real distance (e.g., "2500")
  5. Calculate: `pixelDistance = euclidean(node1.position, node2.position)`
  6. Calculate: `mmPerPixel = realDistanceMm / pixelDistance`
  7. Emit intent: `SetScale(pixelDistance, realDistanceMm)`
  8. Store scale in `drawing.metadata["scaleInfo"]` as JSON
- [ ] Scale stored persistently in drawing (survives save/load)
- [ ] Scale displayed in UI toolbar (e.g., "Scale: 1px = 2.5mm")
- [ ] Test: Two-tap workflow calculates correct scale
- [ ] Test: Scale persisted in drawing metadata
- [ ] Test: After setting scale, measurements accurate
- [ ] Test: Invalid input (negative, zero) rejected with error message
- [ ] File location: `feature/editor/src/main/kotlin/.../tools/ScaleTool.kt`
</ac-block>

---

## Pack H: Undo/Redo System

### PR-25: Undo Stack with PatchOp

**Context (Human):** Implement undo/redo using delta patches from Sprint 1. Memory efficient: store deltas, not full snapshots.

<ac-block id="S3-PR25-AC1">
**Acceptance Criteria for PR25 (Undo Stack)**:
- [ ] Create `class UndoStack` with:
  - `val undoStack: MutableList<PatchOpV1>` (operations that can be undone)
  - `val redoStack: MutableList<PatchOpV1>` (operations that were undone)
  - `fun push(op: PatchOpV1)` - add to undo stack, clear redo stack
  - `fun undo(): PatchOpV1?` - pop from undo stack, push to redo stack
  - `fun redo(): PatchOpV1?` - pop from redo stack, push to undo stack
  - `fun canUndo(): Boolean`, `fun canRedo(): Boolean`
- [ ] Max stack size: 100 operations (prevent unbounded memory growth)
- [ ] When user performs action (AddNode, MoveNode, etc.), create `PatchOpV1` and push to stack
- [ ] Undo intent: Pop operation, apply `op.inverse()` to drawing
- [ ] Redo intent: Pop from redo stack, apply operation to drawing
- [ ] Test: Add node → undo → node removed
- [ ] Test: Undo → redo → node restored
- [ ] Test: 100 operations, undo all, verify drawing matches initial state
- [ ] Test: Stack size capped at 100 (oldest operations discarded)
- [ ] Memory: 100 operations < 100KB (efficient deltas, not snapshots)
- [ ] File location: `feature/editor/src/main/kotlin/.../undo/UndoStack.kt`
</ac-block>

---

### PR-26: Undo/Redo UI Integration

**Context (Human):** Wire undo/redo buttons to undo stack. Enable/disable buttons based on stack state.

<ac-block id="S3-PR26-AC1">
**Acceptance Criteria for PR26 (Undo UI)**:
- [ ] Undo button in top toolbar, enabled when `undoStack.canUndo()`
- [ ] Redo button in top toolbar, enabled when `undoStack.canRedo()`
- [ ] Button click emits intent: `Undo` or `Redo`
- [ ] Reducer applies inverse operation (undo) or forward operation (redo)
- [ ] Keyboard shortcut: Ctrl+Z for undo, Ctrl+Y or Ctrl+Shift+Z for redo (optional)
- [ ] Test: Undo button disabled when stack empty
- [ ] Test: Undo button enabled after action
- [ ] Test: Undo action reverts drawing change
- [ ] UI test: Button states update correctly
- [ ] File location: Update `EditorScreen.kt` and `EditorViewModel.kt`
</ac-block>

---

## Pack I: Profile Assignment UI

### PR-27: Profile Catalog Loading

**Context (Human):** Load profile catalog (from Sprint 1.5 or external JSON). Profiles define cross-section shapes (W8x10, etc.).

<ac-block id="S3-PR27-AC1">
**Acceptance Criteria for PR27 (Profile Catalog)**:
- [ ] Load profile catalog from assets: `assets/profiles/catalog.json`
- [ ] Parse to `List<ProfileDefinition>` with fields:
  - `id: String` (e.g., "W8x10")
  - `name: String` (display name)
  - `category: String` (e.g., "I-Beam", "Tube")
  - `dimensions: Map<String, Double>` (width, height, thickness, etc.)
- [ ] Catalog cached in memory (load once per app session)
- [ ] Test: Catalog loads successfully from JSON
- [ ] Test: All profiles parsed correctly
- [ ] Test: Invalid JSON handled gracefully (fallback to empty catalog)
- [ ] File location: `feature/editor/src/main/kotlin/.../profiles/ProfileCatalog.kt`
</ac-block>

---

### PR-28: Profile Selection Bottom Sheet

**Context (Human):** Show bottom sheet with profile list. User selects profile to assign to member.

<ac-block id="S3-PR28-AC1">
**Acceptance Criteria for PR28 (Profile UI)**:
- [ ] Show bottom sheet when user long-presses member
- [ ] Bottom sheet displays profile list grouped by category
- [ ] Search box to filter profiles by name/ID
- [ ] Tap profile → emit intent: `AssignProfile(memberId, profileId)`
- [ ] Reducer updates `member.profileRef` in domain state
- [ ] Member color changes based on profile category (visual feedback)
- [ ] Test: Bottom sheet displays all profiles
- [ ] Test: Search filters profiles correctly
- [ ] Test: Selecting profile updates member
- [ ] UI test: Bottom sheet opens and closes correctly
- [ ] File location: `feature/editor/src/main/kotlin/.../ui/ProfileSelectionSheet.kt`
</ac-block>

---

## Pack J: Cascading Deletes & Dependency Resolution

### PR-19: DependencyResolver (Transactional Cascading Deletes)

**Context (Human):** CRITICAL: When deleting node, also delete all connected members (cascading delete). Must be transactional and show confirmation with exact counts to prevent accidental data loss.

<ac-block id="S3-PR19A-AC1">
**Acceptance Criteria for PR19A (Dependency Resolution)**:
- [ ] Create `class DependencyResolver` with method:
```kotlin
  fun resolveDependencies(drawing: Drawing2D, entitiesToDelete: Set<String>): DeletionPlan
```
- [ ] `DeletionPlan` contains:
  - `nodesToDelete: Set<String>` (explicit + implied)
  - `membersToDelete: Set<String>` (connected to deleted nodes)
  - `annotationsToDelete: Set<String>` (referencing deleted entities)
  - `totalCount: Int` (sum of all deletions)
- [ ] Algorithm:
  1. Start with explicit `entitiesToDelete`
  2. Find all members where `startNodeId` or `endNodeId` in `entitiesToDelete`
  3. Find all annotations where `targetId` in deleted entities
  4. Return complete deletion plan
- [ ] Before deleting, show confirmation dialog:
  - "Delete 1 node? This will also delete 3 connected members and 2 annotations."
  - User can cancel or confirm
- [ ] If confirmed, apply deletions in transaction (all-or-nothing)
- [ ] Test: Deleting node with 3 connected members plans 4 deletions (1 node + 3 members)
- [ ] Test: Deleting standalone node plans 1 deletion (no cascading)
- [ ] Test: User cancels confirmation → no deletions applied
- [ ] Test: Deletions applied atomically (no partial state)
- [ ] Test: Deletion creates single `PatchOpV1` for undo (undo restores all)
- [ ] Code coverage >90%
- [ ] File location: `feature/editor/src/main/kotlin/.../logic/DependencyResolver.kt`
</ac-block>

---

## Pack K: Testing & Closeout

### PR-30: Performance Benchmarks

**Context (Human):** Verify performance targets are met. 60 FPS is non-negotiable at 10,000 entities.

<ac-block id="S3-PR30-AC1">
**Acceptance Criteria for PR30 (Benchmarks)**:
- [ ] Benchmark: Render 10,000 nodes + 10,000 members at 60 FPS
  - Measure frame time: Must be <16ms average
  - 99th percentile frame time <20ms
- [ ] Benchmark: Hit test on 10,000 entities completes <5ms
- [ ] Benchmark: QuadTree query on 10,000 entities completes <10ms
- [ ] Benchmark: Drag operation (5 sec continuous drag, 60 FPS = 300 frames)
  - Measure GC pauses: All pauses <16ms (no frame drops)
  - Measure memory allocations: <1KB per frame
- [ ] Benchmark: Pan/zoom 1000 times, verify drift <0.5px (primitive storage validation)
- [ ] Use Android Profiler or Macrobenchmark library for measurements
- [ ] Document results in `/docs/editor/PERFORMANCE.md`
- [ ] If benchmarks fail, identify bottleneck and optimize
- [ ] Test: All benchmarks pass on mid-range device (Snapdragon 730 or equivalent)
- [ ] File location: `feature/editor/src/androidTest/kotlin/.../PerformanceBenchmarks.kt`
</ac-block>

---

### PR-31: Instrumentation Tests (End-to-End)

**Context (Human):** Comprehensive E2E tests covering full user workflows.

<ac-block id="S3-PR31-AC1">
**Acceptance Criteria for PR31 (E2E Tests)**:
- [ ] Test: Create project → add 10 nodes → add 5 members → save → reload → verify data intact
- [ ] Test: Drag node → verify position updated → undo → verify position restored
- [ ] Test: Delete node with connected members → confirm cascading delete → verify members deleted
- [ ] Test: Set scale tool → verify scale stored → verify measurements accurate
- [ ] Test: Assign profile to member → verify profile stored → reload → verify profile persisted
- [ ] Test: Process Death simulation → verify state restored from Room
- [ ] Test: Multi-select 5 nodes → drag → verify all moved together
- [ ] Test: QuadTree query during drag → verify free agents + tree used
- [ ] All tests run on emulator/device
- [ ] Test suite completes in <5 minutes
- [ ] File location: `feature/editor/src/androidTest/kotlin/.../EditorE2ETests.kt`
</ac-block>

---

### PR-32: Memory Leak Detection

**Context (Human):** Verify no memory leaks with Leak Canary. Critical for long editing sessions.

<ac-block id="S3-PR32-AC1">
**Acceptance Criteria for PR32 (Leak Detection)**:
- [ ] Integrate Leak Canary in debug build
- [ ] Test scenario:
  1. Open editor
  2. Add 100 nodes
  3. Drag nodes 50 times
  4. Undo 50 times
  5. Close editor
  6. Repeat 10 times
- [ ] Leak Canary reports 0 leaks after test
- [ ] If leaks detected, identify source and fix
- [ ] Document known non-issues (Android framework leaks, if any)
- [ ] Test: ViewModel doesn't leak (context references cleaned)
- [ ] Test: QuadTree doesn't leak (old trees garbage collected)
- [ ] Test: Canvas Paint objects reused (not leaked)
- [ ] File location: Configuration in `app/build.gradle.kts`
</ac-block>

---

### PR-33: Sprint 3 Closeout

**Context (Human):** Final verification before Sprint 4 (converter).

<ac-block id="S3-PR33-AC1">
**Acceptance Criteria for PR33 (Closeout)**:
- [ ] All 33 PRs merged to main branch
- [ ] Code coverage: `feature-editor` module >75%
- [ ] Ktlint check passes: `./gradlew ktlintCheck` with 0 errors
- [ ] All unit tests pass: `./gradlew test`
- [ ] All instrumentation tests pass: `./gradlew connectedAndroidTest`
- [ ] Performance benchmarks pass: 60 FPS @ 10,000 entities
- [ ] Leak Canary clean: 0 leaks after 10 editing sessions
- [ ] Memory test: Peak heap during editing <256MB
- [ ] Process Death test: State restored correctly after kill
- [ ] Room recovery test: Database rebuilt from JSON files after corruption
- [ ] QuadTree test: Hit test on 10,000 entities <5ms
- [ ] No TODO comments in production code
- [ ] Update root README.md with Sprint 3 deliverables
- [ ] Documentation complete: COORDINATE_SYSTEMS.md, PERFORMANCE.md exist
- [ ] Git tag created: `git tag sprint-3-complete`
- [ ] Ready for Sprint 4: Drawing2D data can be passed to converter
</ac-block>

---

## Sprint 3 Success Metrics

**Definition of Done (Sprint Level):**
- ✅ All 33 PRs completed and merged
- ✅ Interactive editor working with node/member creation
- ✅ 60 FPS performance at 10,000 entities
- ✅ No GC pauses >16ms during drag
- ✅ QuadTree hit testing <5ms
- ✅ Room File-Backed storage working (no CursorWindow errors)
- ✅ Process Death recovery working
- ✅ Undo/Redo system functional
- ✅ Scale tool working with accurate measurements
- ✅ Profile assignment working
- ✅ Cascading deletes with confirmation
- ✅ Leak Canary clean
- ✅ Ready for Sprint 4 (converter)

**Key Deliverables:**
1. `feature-editor` module with full MVI architecture
2. Custom Canvas rendering at 60 FPS
3. QuadTree spatial indexing with Free Agents strategy
4. Room database with File-Backed pattern
5. Coordinate system with primitive storage (no matrix accumulation)
6. Pan/zoom gestures with singularity handling
7. Node/Member tools with hit testing
8. Scale tool for physical measurements
9. Undo/Redo with delta patches
10. Profile assignment UI
11. Cascading delete with dependency resolution
12. Process Death recovery with NonCancellable
13. Comprehensive performance benchmarks

**Technical Debt:**
- QuadTree parameters (max depth=8, max items=10) may need tuning based on real-world usage
- Grid snapping not implemented (optional enhancement for future)
- Multi-touch gestures (rotate, 3-finger pan) not implemented (out of scope for MVP)
- Profile catalog currently static JSON (could be dynamic from backend in future)

**Architectural Decisions Applied:**
- ARCH-STATE-004: Room as SSOT, MVI writes patches to DB, not in-memory state
- ARCH-ROOM-003: File-Backed Room (JSON in files, paths in DB) for >2MB drawings
- ARCH-PERF-003: QuadTree rebuild in Dispatchers.Default with atomic swap
- ARCH-MATH-001: Double for coordinates, Float only for Canvas rendering
- Domain/Transient state split prevents GC pressure during drag
- Primitive camera storage (scale, offsetX, offsetY) prevents matrix accumulation drift
- Matrix singularity handling prevents crashes at extreme zoom
- Free Agents strategy prevents UI freeze during QuadTree rebuild
- NonCancellable context ensures final save during onCleared
- DependencyResolver provides transactional cascading deletes

---

## Notes for Developers

**Critical Implementation Details:**
1. **MVI State Split**: Domain (persisted) vs Transient (ephemeral) is KEY to 60 FPS performance
2. **Room File-Backed**: Store JSON in files, not DB columns (CursorWindow 2MB limit)
3. **QuadTree Free Agents**: Remove dragged entities from tree during drag, rebuild after
4. **Camera Primitives**: Store scale/offset, rebuild matrix each frame (no accumulation drift)
5. **Matrix Singularity**: Always check `matrix.invert()` return value before using inverse
6. **Process Death**: NonCancellable in onCleared ensures final save completes
7. **Cascading Deletes**: Use DependencyResolver for transactional deletes with confirmation
8. **UUIDv7**: Timestamp-based IDs for sortability and offline-first architecture

**Dependencies Between PRs:**
- PR-02 (UUIDv7) used by all creation PRs (21, 22, 23)
- PR-05 to PR-08 (State/MVI) are foundational for all interaction PRs
- PR-09 to PR-12.5 (Room) must complete before file operations
- PR-09 (Coordinates) foundational for camera PRs (13-15)
- PR-10.5 (QuadTree) required for PR-16 (Hit Testing)
- PR-17 to PR-19 (Rendering) sequential
- PR-20 (Tools) foundational for tool PRs (21-24)
- PR-25 (Undo) used by tool PRs for history
- PR-19 (DependencyResolver) used by delete operations

**Parallel Work Opportunities:**
- Pack A (module setup) and Pack B (MVI) can overlap
- Pack C (Room) can develop alongside Pack B
- Pack D (coordinates/camera) can start early
- Pack E (QuadTree) independent until Pack F needs it
- Pack G (tools) and Pack H (undo) can overlap
- Pack I (profiles) and Pack J (cascading deletes) can overlap

**Testing Strategy:**
- Unit tests: MVI reducer, QuadTree, coordinate transforms
- Instrumentation tests: UI interactions, Room persistence, Process Death
- Performance benchmarks: Frame time, hit testing, memory allocations
- Leak tests: Leak Canary after repeated workflows
- Stress tests: 10,000 entities rendering, drag operations

---

*Last updated: 2026-02-26*
*Sprint status: Ready for implementation*
*Previous sprint: Sprint 2 - Capture & CV*
*Next sprint: Sprint 4 - Converter (Drawing2D → StructuralModel)*
