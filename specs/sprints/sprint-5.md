# Sprint 5: AR Rendering - Filament 3D Visualization with ARCore

**Status:** Ready
**Dates:** 2026-08-06 — 2026-09-03
**Duration:** 4 weeks
**Team:** 1-2 Android developers (1 with AR/graphics experience)
**Goal:** Implement AR visualization of 3D structural models using Filament rendering engine and ARCore for plane detection and tracking. Display structural members in physical space with accurate scale, user-controlled orientation, and robust lifecycle management.

**Key Deliverables:**
- `feature-ar` module with Filament integration
- ARCore plane detection and anchor placement
- Filament scene graph with structural model rendering
- Camera and lighting setup for realistic rendering
- User-controlled model rotation gesture
- Coordinate mapping (Drawing → AR physical space)
- Stale-while-revalidate cache for models
- Lifecycle-safe rendering (no black screen after background)
- Material system for different member profiles
- Distance measurement tool (optional)

**Critical Architecture Applied:**
- ARCH-AR-005: Engine/Scene/Renderer in ViewModel (not Surface lifecycle), SwapChain in Surface callbacks
- ARCH-MATH-001: Double for calculations, Float for Filament vertex buffers
- Far plane configuration for large structures (±100m bounding box)
- Rotation gesture BEFORE anchor placement (user controls orientation)

---

## Pack A: AR Module Foundation

### PR-01: Module `feature-ar` Setup

**Context (Human):** Create AR feature module with ARCore and Filament dependencies. This is a complex module with native libraries.

<ac-block id="S5-PR01-AC1">
**Acceptance Criteria for PR01 (Module Setup)**:
- [ ] Create module `feature-ar` in `/feature/ar/` directory
- [ ] Module path in settings.gradle.kts: `include(":feature:ar")`
- [ ] Dependencies in build.gradle.kts:
  - `com.google.ar:core:1.41.0` (ARCore SDK)
  - `com.google.android.filament:filament-android:1.51.5` (Filament rendering)
  - `com.google.android.filament:filament-utils-android:1.51.5` (Filament utilities)
  - `com.google.android.filament:gltfio-android:1.51.5` (glTF loading, optional)
  - AndroidX Core, Lifecycle, ViewModel, Hilt
  - `:core:drawing2d`, `:core:converter` (for model loading)
- [ ] AndroidManifest.xml:
  - `<uses-permission android:name="android.permission.CAMERA"/>`
  - `<uses-feature android:name="android.hardware.camera.ar" android:required="true"/>`
  - ARCore metadata: `<meta-data android:name="com.google.ar.core" android:value="required"/>`
- [ ] Proguard rules for Filament (keep native methods)
- [ ] Module compiles: `./gradlew :feature:ar:assembleDebug`
- [ ] Test: Empty instrumentation test runs
- [ ] File location: `/feature/ar/build.gradle.kts`, `/feature/ar/src/main/AndroidManifest.xml`
</ac-block>

---

### PR-02: ARCore Session Setup

**Context (Human):** Initialize ARCore session with configuration for plane detection. Handle ARCore installation and compatibility checks.

<ac-block id="S5-PR02-AC1">
**Acceptance Criteria for PR02 (ARCore Session)**:
- [ ] Create class: `class ArCoreSessionManager(context: Context)`
- [ ] Method: `fun createSession(): Session?` that:
  1. Checks ARCore availability: `ArCoreApk.getInstance().checkAvailability(context)`
  2. If not available/installed: Prompt user to install ARCore
  3. Creates ARCore Session: `Session(context)`
  4. Configures session:
```kotlin
     val config = Config(session).apply {
         planeFindingMode = Config.PlaneFindingMode.HORIZONTAL
         updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
         lightEstimationMode = Config.LightEstimationMode.AMBIENT_INTENSITY
     }
     session.configure(config)
```
  5. Returns configured session or null if failed
- [ ] Handle ARCore exceptions gracefully (device not supported, missing dependencies)
- [ ] Test: Session created successfully on ARCore-compatible device
- [ ] Test: Null returned on non-ARCore device (graceful degradation)
- [ ] Test: Configuration applied correctly (verify via session.config)
- [ ] File location: `feature/ar/src/main/kotlin/.../arcore/ArCoreSessionManager.kt`
</ac-block>

---

### PR-03: AR Fragment with SurfaceView

**Context (Human):** Create AR fragment with SurfaceView for rendering. This is the main AR screen.

<ac-block id="S5-PR03-AC1">
**Acceptance Criteria for PR03 (AR Fragment)**:
- [ ] Create `ArVisualizationFragment` with layout containing:
  - `SurfaceView` (fills entire screen, for Filament rendering)
  - Overlay UI: Instructions, controls, close button
  - Floating Action Button (FAB): Place anchor button
- [ ] Fragment receives `projectId: String` from navigation arguments
- [ ] ViewModel: `ArViewModel` injected via Hilt
- [ ] Lifecycle: Request camera permission before showing AR view
- [ ] Camera permission flow:
  1. Check permission granted
  2. If not: Request permission
  3. If denied: Show rationale, exit AR screen
  4. If granted: Initialize ARCore session
- [ ] Test: Fragment renders without crash
- [ ] Test: Permission request appears when camera permission not granted
- [ ] Test: SurfaceView visible after permission granted
- [ ] UI test: Espresso test for fragment lifecycle
- [ ] File location: `feature/ar/src/main/kotlin/.../ui/ArVisualizationFragment.kt`
</ac-block>

---

## Pack B: Filament Rendering Foundation

### PR-04: Filament Engine Singleton (ViewModel Scope)

**Context (Human):** CRITICAL: Filament Engine must live in ViewModel (not Surface callback) to survive config changes and backgrounding (ARCH-AR-005). This prevents "black screen" bugs.

<ac-block id="S5-PR04-AC1">
**Acceptance Criteria for PR04 (Engine Lifecycle)**:
- [ ] Create `ArViewModel : ViewModel` with Filament components:
```kotlin
  class ArViewModel : ViewModel() {
      // CRITICAL: These live in ViewModel, NOT Surface callbacks
      private val engine: Engine = Engine.create()
      private val scene: Scene = engine.createScene()
      private val renderer: Renderer = engine.createRenderer()
      private val camera: Camera = engine.createCamera(engine.entityManager.create())
      private val view: View = engine.createView()

      // SwapChain lives in Surface callbacks (recreated on surface changes)
      private var swapChain: SwapChain? = null

      init {
          view.scene = scene
          view.camera = camera
      }
  }
```
- [ ] Rationale: Engine survives Activity recreation, screen rotation, backgrounding
- [ ] Method: `fun onSurfaceCreated(surface: Surface, width: Int, height: Int)` that:
```kotlin
  swapChain = engine.createSwapChain(surface)
  view.viewport = Viewport(0, 0, width, height)
```
- [ ] Method: `fun onSurfaceDestroyed()` that:
```kotlin
  engine.destroySwapChain(swapChain)
  swapChain = null
  // DO NOT destroy engine/scene/renderer here!
```
- [ ] Override `onCleared()`:
```kotlin
  override fun onCleared() {
      // Full cleanup only when ViewModel destroyed
      scene.removeAllEntities()
      engine.destroyScene(scene)
      engine.destroyRenderer(renderer)
      engine.destroyCamera(camera)
      engine.destroyView(view)
      engine.destroy()
      super.onCleared()
  }
```
- [ ] Test: Engine survives screen rotation (Activity recreated, Engine persists)
- [ ] Test: SwapChain recreated on surface change
- [ ] Test: No memory leaks (Leak Canary clean after 10 AR sessions)
- [ ] Test: No black screen after backgrounding app for 10 minutes
- [ ] Documentation: Explain ARCH-AR-005 rationale in code comments
- [ ] File location: `feature/ar/src/main/kotlin/.../ArViewModel.kt`
</ac-block>

---

### PR-04.5: Model Cache with Stale-While-Revalidate

**Context (Human):** Cache 3D models in memory for fast loading. Use stale-while-revalidate pattern: return cached model immediately, then check server for updates in background.

<ac-block id="S5-PR04.5-AC1">
**Acceptance Criteria for PR04.5 (Model Cache)**:
- [ ] Create class: `class ModelCache`
- [ ] Fields:
```kotlin
  private val cache = mutableMapOf<String, CacheEntry>()

  data class CacheEntry(
      val model: StructuralModel,
      val timestamp: Long,
      val etag: String?
  )
```
- [ ] Method: `suspend fun getModel(projectId: String): StructuralModel` that:
  1. Check cache: If entry exists and age < 5 minutes, return immediately
  2. Launch background validation:
     - Fetch from server with If-None-Match (ETag)
     - If 304 Not Modified: Update timestamp, return cached
     - If 200 OK: Update cache with new model
  3. Return cached model (even if stale) while background fetch runs
- [ ] Rationale: Fast AR load (no waiting), always fresh data eventually
- [ ] Cache eviction: LRU with max 10 entries (prevent unbounded memory)
- [ ] Test: First load fetches from server
- [ ] Test: Second load returns cached immediately
- [ ] Test: Background validation updates cache if model changed
- [ ] Test: Stale model returned immediately, not blocked by network
- [ ] Test: Cache size capped at 10 entries
- [ ] File location: `feature/ar/src/main/kotlin/.../data/ModelCache.kt`
</ac-block>

---

### PR-05: Scene Graph Setup (Sky, Ground Plane)

**Context (Human):** Set up basic scene with skybox and ground plane. Provides spatial reference for AR models.

<ac-block id="S5-PR05-AC1">
**Acceptance Criteria for PR05 (Scene Setup)**:
- [ ] Create skybox:
```kotlin
  val skybox = Skybox.Builder()
      .color(0.5f, 0.7f, 1.0f, 1.0f) // Light blue sky
      .build(engine)
  scene.skybox = skybox
```
- [ ] Create ground plane entity:
```kotlin
  val groundPlane = EntityManager.get().create()
  RenderableManager.Builder(1)
      .boundingBox(Box(0f, 0f, 0f, 100f, 0f, 100f)) // Large ground plane
      .material(0, groundMaterial)
      .geometry(0, RenderableManager.PrimitiveType.TRIANGLES, groundVertexBuffer, groundIndexBuffer)
      .build(engine, groundPlane)
  scene.addEntity(groundPlane)
```
- [ ] Ground plane: White, slightly transparent (alpha 0.2), large (200m × 200m)
- [ ] Test: Scene renders with skybox visible
- [ ] Test: Ground plane visible when looking down
- [ ] Test: Scene entities added to scene graph correctly
- [ ] File location: `feature/ar/src/main/kotlin/.../rendering/SceneSetup.kt`
</ac-block>

---

### PR-06: Camera Setup with Projection Matrix

**Context (Human):** Configure Filament camera with perspective projection matching ARCore's camera intrinsics.

<ac-block id="S5-PR06-AC1">
**Acceptance Criteria for PR06 (Camera Setup)**:
- [ ] Method: `fun updateCamera(arCamera: com.google.ar.core.Camera)` that:
```kotlin
  // Get ARCore projection matrix
  val projectionMatrix = FloatArray(16)
  arCamera.getProjectionMatrix(projectionMatrix, 0, 0.1f, 100f)

  // Get ARCore view matrix
  val viewMatrix = FloatArray(16)
  arCamera.getViewMatrix(viewMatrix, 0)

  // Apply to Filament camera
  filamentCamera.setCustomProjection(
      mat4fFromFloatArray(projectionMatrix),
      0.1, // near plane
      100.0 // far plane
  )

  val transform = Mat4.inverseTransform(mat4fFromFloatArray(viewMatrix))
  filamentCamera.transform = transform
```
- [ ] Camera updates every frame (called in ARCore update loop)
- [ ] Near plane: 0.1m (10cm, good for close-up inspection)
- [ ] Far plane: 100m (covers large structures)
- [ ] Test: Camera projection matches ARCore camera
- [ ] Test: View matrix updated correctly each frame
- [ ] Test: Near/far planes set correctly (verified via camera.getNear/getFar)
- [ ] File location: `feature/ar/src/main/kotlin/.../rendering/CameraSync.kt`
</ac-block>

---

### PR-07: Lighting Setup (Ambient + Directional)

**Context (Human):** Configure scene lighting for realistic rendering. Use ARCore light estimation for ambient, add directional sun light.

<ac-block id="S5-PR07-AC1">
**Acceptance Criteria for PR07 (Lighting)**:
- [ ] Create indirect light (ambient):
```kotlin
  val indirectLight = IndirectLight.Builder()
      .intensity(30000f) // Lux, adjusted per ARCore estimate
      .build(engine)
  scene.indirectLight = indirectLight
```
- [ ] Create directional light (sun):
```kotlin
  val sun = EntityManager.get().create()
  LightManager.Builder(LightManager.Type.DIRECTIONAL)
      .color(1f, 1f, 1f)
      .intensity(100000f) // Lux
      .direction(0f, -1f, -0.5f) // Angled from above
      .castShadows(true)
      .build(engine, sun)
  scene.addEntity(sun)
```
- [ ] Update ambient intensity from ARCore light estimate each frame:
```kotlin
  val lightEstimate = frame.lightEstimate
  val intensity = lightEstimate.pixelIntensity * 30000f
  indirectLight.intensity = intensity
```
- [ ] Test: Scene has lighting (objects not black)
- [ ] Test: Ambient intensity updates from ARCore
- [ ] Test: Directional light casts shadows (if shadow rendering enabled)
- [ ] File location: `feature/ar/src/main/kotlin/.../rendering/LightingSetup.kt`
</ac-block>

---

## Pack C: ARCore Integration

### PR-08: Frame Update Loop

**Context (Human):** Main ARCore update loop: fetch frame, update camera, check for planes, render. This runs every frame (60 FPS).

<ac-block id="S5-PR08-AC1">
**Acceptance Criteria for PR08 (Frame Loop)**:
- [ ] Implement render loop in Fragment:
```kotlin
  private val frameCallback = object : Choreographer.FrameCallback {
      override fun doFrame(frameTimeNanos: Long) {
          if (!isPaused) {
              updateFrame()
              Choreographer.getInstance().postFrameCallback(this)
          }
      }
  }

  fun updateFrame() {
      val frame = session.update()

      // Update Filament camera from ARCore
      viewModel.updateCamera(frame.camera)

      // Update light estimation
      viewModel.updateLighting(frame.lightEstimate)

      // Check for tracked planes
      val planes = session.getAllTrackables(Plane::class.java)
      viewModel.updatePlanes(planes.filter { it.trackingState == TrackingState.TRACKING })

      // Render
      viewModel.render()
  }
```
- [ ] Frame loop starts in `onResume()`, stops in `onPause()`
- [ ] Render at display refresh rate (typically 60 FPS)
- [ ] Test: Frame loop runs at ~60 FPS (measure with frame time logging)
- [ ] Test: Frame loop stops when paused (no wasted CPU)
- [ ] Test: Camera/lighting updates each frame
- [ ] Performance: Frame time <16ms (no dropped frames)
- [ ] File location: `feature/ar/src/main/kotlin/.../ui/ArVisualizationFragment.kt`
</ac-block>

---

### PR-09: Plane Detection Visualization

**Context (Human):** Visualize detected horizontal planes (tables, floors) as semi-transparent polygons. Helps user understand where they can place anchor.

<ac-block id="S5-PR09-AC1">
**Acceptance Criteria for PR09 (Plane Viz)**:
- [ ] For each detected plane, create Filament renderable:
```kotlin
  fun visualizePlane(plane: Plane) {
      val polygon = plane.polygon
      val vertices = polygon.array() // 2D vertices in plane space

      // Convert to 3D vertices in world space
      val vertices3D = vertices.map { v ->
          val pose = plane.centerPose
          val point = floatArrayOf(v.x, 0f, v.y, 1f)
          val worldPoint = FloatArray(4)
          Matrix.multiplyMV(worldPoint, 0, pose.matrix, 0, point, 0)
          Vec3(worldPoint[0], worldPoint[1], worldPoint[2])
      }

      // Create mesh (triangulate polygon)
      val mesh = PolygonMesh.triangulate(vertices3D)

      // Create renderable
      val entity = EntityManager.get().create()
      RenderableManager.Builder(1)
          .geometry(0, mesh)
          .material(0, planeMaterial) // Semi-transparent white
          .build(engine, entity)

      scene.addEntity(entity)
      planeEntities[plane.id] = entity
  }
```
- [ ] Plane material: White, 50% transparent, double-sided
- [ ] Update plane visualization each frame (planes grow as ARCore scans)
- [ ] Remove plane entities when planes stop tracking
- [ ] Test: Detected planes visible as white polygons
- [ ] Test: Plane polygons update as plane boundaries refine
- [ ] Test: Planes removed when tracking lost
- [ ] File location: `feature/ar/src/main/kotlin/.../rendering/PlaneRenderer.kt`
</ac-block>

---

### PR-10: Anchor Placement (Tap to Place)

**Context (Human):** User taps screen to place anchor on detected plane. This is where the 3D model will be positioned.

<ac-block id="S5-PR10-AC1">
**Acceptance Criteria for PR10 (Anchor Placement)**:
- [ ] Handle tap events on SurfaceView:
```kotlin
  surfaceView.setOnTouchListener { _, event ->
      if (event.action == MotionEvent.ACTION_UP) {
          handleTap(event.x, event.y)
      }
      true
  }
```
- [ ] Method: `fun handleTap(x: Float, y: Float)` that:
```kotlin
  val frame = session.update()
  val hits = frame.hitTest(x, y)

  // Find first hit on a plane
  val planeHit = hits.firstOrNull { hit ->
      val trackable = hit.trackable
      trackable is Plane && trackable.isPoseInPolygon(hit.hitPose)
  }

  if (planeHit != null) {
      // Create anchor at hit location
      val anchor = planeHit.createAnchor()
      viewModel.placeModel(anchor)
  }
```
- [ ] Only allow anchor placement if plane detected (show instructions otherwise)
- [ ] Anchor persists across frames (tracked by ARCore)
- [ ] Test: Tap on plane creates anchor
- [ ] Test: Tap on empty space (no plane) does nothing
- [ ] Test: Anchor position tracked correctly (moves with plane if plane adjusts)
- [ ] File location: `feature/ar/src/main/kotlin/.../ui/ArVisualizationFragment.kt`
</ac-block>

---

## Pack D: Rotation Gesture & Anchor Workflow

### PR-11: Rotation Gesture (Before Anchor Placement)

**Context (Human):** CRITICAL: User should control model orientation BEFORE placing anchor. This is better UX than rotating after placement. User sees preview, rotates it, then confirms placement.

<ac-block id="S5-PR11-AC1">
**Acceptance Criteria for PR11 (Rotation Gesture)**:
- [ ] Two-stage workflow:
  1. SCANNING state: User scans for planes, sees plane visualization
  2. PREVIEW state: First tap shows model preview, user can rotate, second tap confirms
- [ ] State machine:
```kotlin
  sealed class ArState {
      object Scanning : ArState()
      data class Preview(val hitPose: Pose, val rotation: Float) : ArState()
      data class Placed(val anchor: Anchor, val rotation: Float) : ArState()
  }
```
- [ ] In PREVIEW state, show model at hit location (no anchor yet):
```kotlin
  // Render model at hit pose with current rotation
  val modelTransform = Matrix()
  hitPose.toMatrix(modelTransform, 0)
  Matrix.rotateM(modelTransform, 0, rotation, 0f, 1f, 0f) // Y-axis rotation

  renderModel(modelTransform)
```
- [ ] Rotation gesture: Two-finger rotate (detect via RotationGestureDetector):
```kotlin
  val rotationDetector = RotationGestureDetector { angle ->
      if (state is Preview) {
          state = state.copy(rotation = state.rotation + angle)
      }
  }
```
- [ ] Confirm button: Visible in PREVIEW state, creates anchor with current rotation
- [ ] Test: First tap shows preview (no anchor created)
- [ ] Test: Rotation gesture updates model orientation
- [ ] Test: Confirm button creates anchor with correct rotation
- [ ] Test: Model orientation persists after anchor created
- [ ] UI test: Rotation gesture recognized correctly
- [ ] Documentation: Explain two-stage workflow rationale (better UX)
- [ ] File location: `feature/ar/src/main/kotlin/.../ui/ArVisualizationFragment.kt`
</ac-block>

---

### PR-12: Rotation Gesture Detector

**Context (Human):** Implement two-finger rotation detection (pinch-rotate gesture).

<ac-block id="S5-PR12-AC1">
**Acceptance Criteria for PR12 (Gesture Detector)**:
- [ ] Create class: `class RotationGestureDetector(val onRotate: (angleDegrees: Float) -> Unit)`
- [ ] Algorithm:
```kotlin
  private var previousAngle = 0f

  fun onTouchEvent(event: MotionEvent): Boolean {
      if (event.pointerCount != 2) {
          previousAngle = 0f
          return false
      }

      when (event.actionMasked) {
          MotionEvent.ACTION_POINTER_DOWN -> {
              previousAngle = calculateAngle(event)
          }
          MotionEvent.ACTION_MOVE -> {
              val currentAngle = calculateAngle(event)
              val delta = currentAngle - previousAngle
              onRotate(delta)
              previousAngle = currentAngle
          }
      }
      return true
  }

  private fun calculateAngle(event: MotionEvent): Float {
      val dx = event.getX(0) - event.getX(1)
      val dy = event.getY(0) - event.getY(1)
      return atan2(dy.toDouble(), dx.toDouble()).toFloat() * 180f / PI.toFloat()
  }
```
- [ ] Rotation accumulates smoothly (no jitter)
- [ ] Test: Two-finger rotate gesture detected
- [ ] Test: Rotation angle calculated correctly
- [ ] Test: Single-finger touch ignored (no rotation)
- [ ] Performance: Gesture detection runs at 60 FPS (no frame drops)
- [ ] File location: `feature/ar/src/main/kotlin/.../gestures/RotationGestureDetector.kt`
</ac-block>

---

### PR-13: Filament Lifecycle Management with SurfaceHolder.Callback

**Context (Human):** CRITICAL: Properly handle Surface lifecycle with SurfaceHolder.Callback. This is part of ARCH-AR-005 implementation - SwapChain tied to Surface, Engine in ViewModel.

<ac-block id="S5-PR13-AC1">
**Acceptance Criteria for PR13 (Surface Lifecycle)**:
- [ ] Implement SurfaceHolder.Callback in Fragment:
```kotlin
  private val surfaceCallback = object : SurfaceHolder.Callback {
      override fun surfaceCreated(holder: SurfaceHolder) {
          val surface = holder.surface
          viewModel.onSurfaceCreated(surface, holder.surfaceFrame.width(), holder.surfaceFrame.height())
      }

      override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
          viewModel.onSurfaceChanged(width, height)
      }

      override fun surfaceDestroyed(holder: SurfaceHolder) {
          viewModel.onSurfaceDestroyed()
      }
  }
```
- [ ] In ViewModel (from PR-04):
```kotlin
  fun onSurfaceCreated(surface: Surface, width: Int, height: Int) {
      swapChain = engine.createSwapChain(surface)
      view.viewport = Viewport(0, 0, width, height)
  }

  fun onSurfaceChanged(width: Int, height: Int) {
      view.viewport = Viewport(0, 0, width, height)
  }

  fun onSurfaceDestroyed() {
      engine.destroySwapChain(swapChain)
      swapChain = null
      // Engine/Scene/Renderer NOT destroyed (ARCH-AR-005)
  }
```
- [ ] Register callback: `surfaceView.holder.addCallback(surfaceCallback)`
- [ ] Unregister in onDestroyView: `surfaceView.holder.removeCallback(surfaceCallback)`
- [ ] Test: Surface created triggers SwapChain creation
- [ ] Test: Surface destroyed triggers SwapChain cleanup (but NOT Engine)
- [ ] Test: Screen rotation recreates SwapChain, preserves Engine/Scene
- [ ] Test: Background 10 minutes, restore, no black screen
- [ ] Leak Canary: No leaks after 10 surface create/destroy cycles
- [ ] File location: Update `ArVisualizationFragment.kt`
</ac-block>

---

## Pack E: Model Loading & Rendering

### PR-14: StructuralModel to Filament Geometry

**Context (Human):** Convert StructuralModel (from Sprint 4 converter) to Filament renderable meshes. Each member becomes a cylinder connecting two nodes.

<ac-block id="S5-PR14-AC1">
**Acceptance Criteria for PR14 (Model Loading)**:
- [ ] Implement: `class ModelRenderer(engine: Engine, scene: Scene)`
- [ ] Method: `fun loadModel(model: StructuralModel, anchorPose: Pose, rotation: Float)`
- [ ] Algorithm:
  1. For each member in model:
```kotlin
     val startNode = model.nodes.find { it.id == member.startNodeId }
     val endNode = model.nodes.find { it.id == member.endNodeId }

     val start = Vec3(startNode.x, startNode.y, startNode.z)
     val end = Vec3(endNode.x, endNode.y, endNode.z)

     // Create cylinder mesh between start and end
     val cylinder = createCylinderMesh(start, end, radius = 0.05) // 5cm radius

     // Create entity
     val entity = EntityManager.get().create()
     RenderableManager.Builder(1)
         .geometry(0, cylinder)
         .material(0, memberMaterial)
         .build(engine, entity)

     scene.addEntity(entity)
```
  2. Apply anchor transformation to all entities
  3. Apply user rotation around Y-axis
- [ ] Member material: Gray metallic, slight roughness
- [ ] Node visualization: Small spheres (10cm radius) at node positions
- [ ] Test: Model with 10 members renders correctly
- [ ] Test: Member cylinders connect correct nodes
- [ ] Test: Model positioned at anchor pose
- [ ] Test: Model rotation applied correctly
- [ ] Performance: 100-member model loads in <1 second
- [ ] File location: `feature/ar/src/main/kotlin/.../rendering/ModelRenderer.kt`
</ac-block>

---

### PR-15: CoordinateMapper with Extreme Scale Guards

**Context (Human):** CRITICAL: Map drawing coordinates (millimeters, typically 0-50,000mm) to AR physical space (meters, typically 0-50m). Handle extreme cases (tiny models, huge models) with guards and far plane adjustment.

<ac-block id="S5-PR15-AC1">
**Acceptance Criteria for PR15 (Coordinate Mapping)**:
- [ ] Create class: `class CoordinateMapper`
- [ ] Method: `fun mapToArSpace(model: StructuralModel): StructuralModel` that:
```kotlin
  // Calculate bounding box
  val bbox = calculateBoundingBox(model.nodes)
  val maxDim = max(bbox.width, max(bbox.height, bbox.depth))

  // Scale factor: millimeters to meters
  val mmToMeters = 0.001
  val maxDimMeters = maxDim * mmToMeters

  // Guard: If model too large (>100m), scale down
  val scaleFactor = if (maxDimMeters > 100.0) {
      100.0 / maxDimMeters
  } else {
      1.0
  }

  // Guard: If model too small (<0.1m), scale up
  val finalScale = if (maxDimMeters * scaleFactor < 0.1) {
      0.1 / maxDimMeters
  } else {
      scaleFactor
  }

  // Apply scaling
  val scaledNodes = model.nodes.map { node ->
      node.copy(
          x = node.x * mmToMeters * finalScale,
          y = node.y * mmToMeters * finalScale,
          z = node.z * mmToMeters * finalScale
      )
  }

  return model.copy(nodes = scaledNodes)
```
- [ ] Far plane adjustment: If bbox > 50m, increase camera far plane:
```kotlin
  val requiredFarPlane = maxDimMeters * 2.0 // 2x bounding box
  if (requiredFarPlane > camera.cullingFar) {
      camera.setProjection(..., near = 0.01, far = requiredFarPlane)
  }
```
- [ ] Center model at origin (subtract bbox center from all coordinates)
- [ ] Test: Model with 50m span maps to 50m in AR space
- [ ] Test: Model with 200m span scaled down to 100m (guard triggered)
- [ ] Test: Model with 5cm span scaled up to 0.1m (guard triggered)
- [ ] Test: Far plane adjusted for large models (no culling)
- [ ] Test: Model centered at origin (bbox center = 0,0,0)
- [ ] Documentation: Explain coordinate mapping and guards
- [ ] File location: `feature/ar/src/main/kotlin/.../rendering/CoordinateMapper.kt`
</ac-block>

---

### PR-16: AR State Machine (SCANNING → PREVIEW → PLACED)

**Context (Human):** Formalize AR state machine. Clear states prevent bugs and improve UX.

<ac-block id="S5-PR16-AC1">
**Acceptance Criteria for PR16 (State Machine)**:
- [ ] Sealed class (from PR-11):
```kotlin
  sealed class ArState {
      object Scanning : ArState() // Looking for planes
      data class Preview(val hitPose: Pose, val rotation: Float = 0f) : ArState() // Preview with rotation
      data class Placed(val anchor: Anchor, val rotation: Float) : ArState() // Model anchored
  }
```
- [ ] StateFlow in ViewModel:
```kotlin
  private val _arState = MutableStateFlow<ArState>(ArState.Scanning)
  val arState: StateFlow<ArState> = _arState.asStateFlow()
```
- [ ] State transitions:
  - SCANNING → PREVIEW: User taps on detected plane
  - PREVIEW → SCANNING: User taps "Cancel" button
  - PREVIEW → PLACED: User taps "Confirm" button (creates anchor)
  - PLACED → PREVIEW: User taps "Reposition" button (deletes anchor)
- [ ] UI updates based on state:
  - SCANNING: Show "Point camera at floor" instruction, plane visualization
  - PREVIEW: Show rotation controls, Cancel/Confirm buttons
  - PLACED: Show measurement tools, hide placement controls
- [ ] Test: State transitions correctly on user actions
- [ ] Test: UI updates when state changes
- [ ] Test: Anchor only created in PLACED state
- [ ] File location: `feature/ar/src/main/kotlin/.../ArViewModel.kt`
</ac-block>

---

## Pack F: Materials & Visuals

### PR-17: Material System for Different Profiles

**Context (Human):** Different structural profiles (I-beams, tubes, etc.) should have different materials/colors. This helps visualize structure.

<ac-block id="S5-PR17-AC1">
**Acceptance Criteria for PR17 (Materials)**:
- [ ] Create material library:
```kotlin
  object MaterialLibrary {
      fun createMaterial(engine: Engine, profile: String?): MaterialInstance {
          return when {
              profile?.startsWith("W") == true -> steelBeamMaterial(engine) // I-beams
              profile?.startsWith("HSS") == true -> steelTubeMaterial(engine) // Hollow tubes
              else -> defaultMaterial(engine)
          }
      }

      private fun steelBeamMaterial(engine: Engine): MaterialInstance {
          // Dark gray metallic
          val material = Material.Builder()
              .baseColor(0.3f, 0.3f, 0.3f, 1.0f)
              .metallic(1.0f)
              .roughness(0.4f)
              .build(engine)
          return material.createInstance()
      }

      private fun steelTubeMaterial(engine: Engine): MaterialInstance {
          // Lighter gray metallic
          val material = Material.Builder()
              .baseColor(0.5f, 0.5f, 0.5f, 1.0f)
              .metallic(1.0f)
              .roughness(0.3f)
              .build(engine)
          return material.createInstance()
      }
  }
```
- [ ] Apply materials when loading model (in PR-14's loadModel)
- [ ] Test: I-beams render with dark gray material
- [ ] Test: Hollow tubes render with lighter gray material
- [ ] Test: Unknown profiles use default material
- [ ] Visual test: Materials look realistic under lighting
- [ ] File location: `feature/ar/src/main/kotlin/.../rendering/MaterialLibrary.kt`
</ac-block>

---

### PR-18: Node Visualization (Spheres at Joints)

**Context (Human):** Show small spheres at structural nodes (connection points). Helps understand structure topology.

<ac-block id="S5-PR18-AC1">
**Acceptance Criteria for PR18 (Node Spheres)**:
- [ ] For each node in model, create sphere entity:
```kotlin
  fun createNodeSphere(position: Vec3): Int {
      val sphere = EntityManager.get().create()

      val sphereMesh = IcoSphere.Builder()
          .radius(0.05f) // 5cm radius
          .subdivisions(2)
          .build(engine)

      RenderableManager.Builder(1)
          .geometry(0, sphereMesh)
          .material(0, nodeMaterial) // Red metallic
          .build(engine, sphere)

      TransformManager.getInstance().setTransform(
          TransformManager.getInstance().getInstance(sphere),
          floatArrayOf(
              1f, 0f, 0f, 0f,
              0f, 1f, 0f, 0f,
              0f, 0f, 1f, 0f,
              position.x, position.y, position.z, 1f
          )
      )

      scene.addEntity(sphere)
      return sphere
  }
```
- [ ] Node material: Red metallic (stands out from members)
- [ ] Optional: Toggle visibility (some users may want to hide nodes)
- [ ] Test: Spheres rendered at correct node positions
- [ ] Test: Spheres visible on top of members (depth ordering)
- [ ] Test: Toggle visibility works
- [ ] File location: Update `ModelRenderer.kt`
</ac-block>

---

## Pack G: Performance & Cleanup

### PR-19: Frame Rate Monitoring

**Context (Human):** Monitor rendering performance, ensure 60 FPS. Drop below 60 FPS degrades AR experience.

<ac-block id="S5-PR19-AC1">
**Acceptance Criteria for PR19 (Frame Rate)**:
- [ ] Add FPS counter:
```kotlin
  private var frameCount = 0
  private var lastFpsUpdateTime = System.nanoTime()

  fun updateFrame() {
      frameCount++
      val now = System.nanoTime()
      val elapsed = now - lastFpsUpdateTime

      if (elapsed > 1_000_000_000L) { // 1 second
          val fps = frameCount.toFloat() / (elapsed / 1_000_000_000f)
          Log.d("AR", "FPS: $fps")

          frameCount = 0
          lastFpsUpdateTime = now
      }

      // ... rest of frame update
  }
```
- [ ] Log warning if FPS < 55 (indicates performance issue)
- [ ] Measure frame time distribution (min, avg, max, p95, p99)
- [ ] Test: Empty scene renders at 60 FPS
- [ ] Test: 100-member model renders at >55 FPS
- [ ] Test: Frame time p99 < 20ms (occasional spikes acceptable)
- [ ] Performance profiling: Identify bottlenecks if FPS drops
- [ ] File location: Update `ArVisualizationFragment.kt`
</ac-block>

---

### PR-20: Memory Management (Entity Cleanup)

**Context (Human):** Properly clean up Filament entities when model removed or updated. Prevents memory leaks in long AR sessions.

<ac-block id="S5-PR20-AC1">
**Acceptance Criteria for PR20 (Cleanup)**:
- [ ] Track all created entities:
```kotlin
  private val modelEntities = mutableListOf<Int>()
  private val planeEntities = mutableMapOf<Int, Int>()
```
- [ ] Method: `fun removeModel()` that:
```kotlin
  for (entity in modelEntities) {
      scene.removeEntity(entity)
      engine.destroyEntity(entity)
  }
  modelEntities.clear()
```
- [ ] Method: `fun updatePlanes(planes: Collection<Plane>)` that:
```kotlin
  // Remove entities for planes no longer tracked
  val currentPlaneIds = planes.map { it.hashCode() }.toSet()
  val entitiesToRemove = planeEntities.keys.filter { it !in currentPlaneIds }

  for (planeId in entitiesToRemove) {
      val entity = planeEntities.remove(planeId)
      if (entity != null) {
          scene.removeEntity(entity)
          engine.destroyEntity(entity)
      }
  }
```
- [ ] Clean up on ViewModel clear (already in PR-04's onCleared)
- [ ] Test: Entities removed when model cleared
- [ ] Test: Plane entities removed when planes stop tracking
- [ ] Test: No memory leaks after 10 model load/unload cycles (Leak Canary)
- [ ] Test: Native memory cleaned up (Filament entities destroyed)
- [ ] File location: Update `ModelRenderer.kt`, `PlaneRenderer.kt`
</ac-block>

---

## Pack H: Testing & Closeout

### PR-21: AR Instrumentation Tests

**Context (Human):** Basic AR tests on real device/emulator. Limited by ARCore emulator capabilities.

<ac-block id="S5-PR21-AC1">
**Acceptance Criteria for PR21 (AR Tests)**:
- [ ] Test suite:
  - AR session creation succeeds on supported device
  - Camera permission flow works correctly
  - Fragment lifecycle (create, pause, resume, destroy) no crashes
  - ViewModel lifecycle (screen rotation) preserves Engine
  - State transitions (SCANNING → PREVIEW → PLACED) work
- [ ] Use mock ARCore session for deterministic tests where possible
- [ ] Test: Session created successfully
- [ ] Test: Permission denied shows rationale
- [ ] Test: Screen rotation doesn't destroy Engine (ARCH-AR-005 validation)
- [ ] Test: Background 1 minute, restore, no crash
- [ ] UI test: State machine transitions correctly
- [ ] Note: Full AR testing requires physical device with ARCore
- [ ] File location: `feature/ar/src/androidTest/kotlin/.../ArInstrumentationTest.kt`
</ac-block>

---

### PR-22: Leak Detection (Filament Native Memory)

**Context (Human):** CRITICAL: Filament allocates native memory (outside JVM heap). Standard Leak Canary won't detect these leaks. Need manual verification.

<ac-block id="S5-PR22-AC1">
**Acceptance Criteria for PR22 (Leak Detection)**:
- [ ] Test scenario:
  1. Open AR screen
  2. Load model
  3. Rotate model
  4. Place anchor
  5. Close AR screen
  6. Repeat 20 times
- [ ] Monitor native memory:
```bash
  # Check native heap via dumpsys
  adb shell dumpsys meminfo com.yourapp | grep "Native Heap"

  # Should be stable (not growing) after 20 cycles
```
- [ ] Leak Canary: 0 leaks reported (JVM heap)
- [ ] Native heap: Growth <10MB after 20 cycles (acceptable for caching)
- [ ] If leaks detected:
  - Check all Filament entities destroyed in onCleared
  - Check SwapChain destroyed in surfaceDestroyed
  - Check MaterialInstances destroyed
  - Check VertexBuffers/IndexBuffers destroyed
- [ ] Documentation: Explain native memory monitoring in README
- [ ] File location: Document in `/docs/ar/MEMORY_MANAGEMENT.md`
</ac-block>

---

### PR-23: Documentation (AR Architecture)

**Context (Human):** Comprehensive docs for AR module.

<ac-block id="S5-PR23-AC1">
**Acceptance Criteria for PR23 (AR Docs)**:
- [ ] Create `/docs/ar/` directory with files:
  - `ARCHITECTURE.md` - Overall AR design
  - `FILAMENT_LIFECYCLE.md` - Engine/SwapChain lifecycle (ARCH-AR-005)
  - `COORDINATE_MAPPING.md` - Drawing space → AR space
  - `STATE_MACHINE.md` - AR state transitions
  - `MATERIALS.md` - Material system
  - `TROUBLESHOOTING.md` - Common issues
- [ ] `ARCHITECTURE.md` includes:
  - Component diagram (ARCore, Filament, ViewModel, Fragment)
  - Data flow diagram (Model → Renderer → Filament → Screen)
  - Lifecycle diagram (Surface, ViewModel, ARCore session)
- [ ] `FILAMENT_LIFECYCLE.md` explains:
  - Why Engine in ViewModel (not Surface)
  - Why SwapChain in Surface callbacks
  - What happens during screen rotation
  - What happens during backgrounding
  - ARCH-AR-005 rationale
- [ ] `COORDINATE_MAPPING.md` explains:
  - Millimeters → meters conversion
  - Bounding box calculation
  - Scale guards (too large, too small)
  - Far plane adjustment
- [ ] All docs have diagrams (ASCII art or Mermaid)
- [ ] All docs have code examples
- [ ] Markdown valid, no broken links
- [ ] File locations: `/docs/ar/*.md`
</ac-block>

---

### PR-24: Sprint 5 Closeout

**Context (Human):** Final verification before Sprint 6 (OCR auto-assist).

<ac-block id="S5-PR24-AC1">
**Acceptance Criteria for PR24 (Closeout)**:
- [ ] All 24 PRs merged to main branch
- [ ] Code coverage: `feature-ar` module >70% (lower acceptable due to native libraries)
- [ ] Ktlint check passes: `./gradlew ktlintCheck` with 0 errors
- [ ] All unit tests pass: `./gradlew test`
- [ ] All instrumentation tests pass: `./gradlew :feature:ar:connectedAndroidTest`
- [ ] Manual AR test: Load 100-member model, place in AR, rotate, renders at >55 FPS
- [ ] Manual AR test: Screen rotation preserves scene (no black screen)
- [ ] Manual AR test: Background 10 minutes, restore, no crash
- [ ] Leak Canary: 0 leaks after 10 AR sessions
- [ ] Native memory: Stable after 20 AR session cycles
- [ ] ARCH-AR-005 validated: Engine survives config changes, SwapChain recreated
- [ ] Performance: 100-member model loads in <1 second, renders at 60 FPS
- [ ] No TODO comments in production code
- [ ] Update root README.md with Sprint 5 deliverables
- [ ] Documentation complete: 6 docs in `/docs/ar/`
- [ ] Git tag created: `git tag sprint-5-complete`
- [ ] Ready for Sprint 6: AR visualization working end-to-end
</ac-block>

---

## Sprint 5 Success Metrics

**Definition of Done (Sprint Level):**
- ✅ All 24 PRs completed and merged
- ✅ AR module functional with ARCore + Filament
- ✅ Plane detection and visualization working
- ✅ Anchor placement with tap gesture
- ✅ Model rotation before placement (better UX)
- ✅ 3D model rendering in AR space
- ✅ Coordinate mapping (mm → m) with scale guards
- ✅ Lifecycle-safe (Engine in ViewModel, SwapChain in Surface)
- ✅ No black screen after config changes
- ✅ Performance: 60 FPS with 100-member models
- ✅ No memory leaks (JVM and native)
- ✅ Material system for different profiles
- ✅ Documentation complete
- ✅ Ready for Sprint 6 (OCR auto-assist)

**Key Deliverables:**
1. feature-ar module with ARCore integration
2. Filament rendering engine setup
3. ARCore session and plane detection
4. Anchor placement with tap-to-place
5. Rotation gesture BEFORE anchor placement
6. StructuralModel → Filament geometry conversion
7. Coordinate mapping (mm → m) with guards
8. Material system (different profiles = different colors)
9. Node visualization (spheres at joints)
10. Lifecycle-safe architecture (ARCH-AR-005)
11. Stale-while-revalidate model cache
12. Frame rate monitoring
13. Native memory management
14. Comprehensive AR documentation

**Technical Debt:**
- No multi-anchor support (only one model at a time)
- No distance measurement tool (deferred to future)
- No AR recording/screenshot (nice-to-have)
- No occlusion (objects don't go behind real-world surfaces)
- No physics/collision detection
- Far plane hardcoded to 100m (works for most cases, may need adjustment for massive structures)

**Architectural Decisions Applied:**
- ARCH-AR-005: Engine/Scene/Renderer in ViewModel scope, SwapChain in Surface callbacks
- ARCH-MATH-001: Double for calculations, Float for Filament vertex buffers
- Two-stage workflow: PREVIEW with rotation → PLACED with anchor
- Stale-while-revalidate cache pattern for fast AR loads
- Coordinate mapping with extreme scale guards (prevent tiny/huge models)
- Far plane adjustment for large structures
- Frame rate monitoring (ensure 60 FPS)
- Native memory tracking (Filament entities properly destroyed)

---

## Notes for Developers

**Critical Implementation Details:**
1. ARCH-AR-005: Engine MUST be in ViewModel, NOT Surface callbacks (prevents black screen)
2. SwapChain Lifecycle: Created in surfaceCreated, destroyed in surfaceDestroyed
3. Rotation Gesture: Happens BEFORE anchor placement (better UX than after)
4. Coordinate Mapping: Millimeters → meters with scale guards (0.1m to 100m)
5. Far Plane: Adjust dynamically based on model bounding box
6. Frame Rate: Monitor continuously, log warnings if <55 FPS
7. Native Memory: Manually verify no leaks (Leak Canary won't catch native heap)
8. Material System: Different profiles get different materials for visual clarity

**Dependencies Between PRs:**
- PR-01 (Module setup) foundational
- PR-02, PR-03 (ARCore session, Fragment) sequential
- PR-04 (Engine lifecycle) CRITICAL, must complete before rendering PRs
- PR-04.5 (Cache) can develop in parallel with AR setup
- PR-05 to PR-07 (Scene, Camera, Lighting) sequential, depend on PR-04
- PR-08 to PR-10 (Frame loop, Planes, Anchors) sequential
- PR-11, PR-12 (Rotation gesture) can develop in parallel with Pack C
- PR-13 (Surface lifecycle) integrates PR-04 with PR-03
- PR-14, PR-15 (Model loading, Coordinates) sequential, depend on PR-04
- PR-16 (State machine) formalizes PR-11 workflow
- PR-17, PR-18 (Materials, Nodes) can develop in parallel
- PR-19, PR-20 (Performance, Cleanup) final polish
- PR-21, PR-22 (Testing, Leaks) validation phase
- PR-23, PR-24 (Docs, Closeout) final documentation

**Parallel Work Opportunities:**
- Pack A (Foundation) quick start
- Pack B (Filament) and Pack C (ARCore) can overlap after PR-04
- Pack D (Rotation gesture) can develop alongside Pack C
- Pack E (Model loading) starts after Pack B complete
- Pack F (Materials) can develop once PR-14 working
- Pack G (Performance) final optimization
- Pack H (Testing/Docs) throughout sprint

**Testing Strategy:**
- Unit tests: Coordinate mapping, state machine, gesture detection
- Integration tests: Filament lifecycle, entity cleanup
- Instrumentation tests: ARCore session, fragment lifecycle, screen rotation
- Manual tests: Real AR device testing (emulator limited)
- Performance tests: Frame rate monitoring, large models
- Memory tests: Leak Canary (JVM) + dumpsys (native heap)
- Stress tests: 20 AR session cycles (load/unload)

**ARCH-AR-005 Deep Dive:**

This is THE MOST CRITICAL architectural decision in Sprint 5. Wrong implementation = black screen bugs.

Problem: Naive approach ties Engine to Surface lifecycle
```kotlin
// ❌ WRONG - DO NOT DO THIS
override fun surfaceCreated(holder: SurfaceHolder) {
    engine = Engine.create() // Created with surface
    // ... setup scene
}

override fun surfaceDestroyed(holder: SurfaceHolder) {
    engine.destroy() // Destroyed with surface
}
```

Why it fails:
- Screen rotation → Surface destroyed → Engine destroyed → Scene lost
- Backgrounding app → Surface destroyed → Engine destroyed → Black screen on restore
- User locks phone → Surface destroyed → Everything gone

Solution: Decouple Engine from Surface
```kotlin
// ✅ CORRECT - DO THIS
class ArViewModel : ViewModel() {
    // Engine lives here (ViewModel scope)
    private val engine = Engine.create()
    private val scene = engine.createScene()
    private var swapChain: SwapChain? = null

    fun onSurfaceCreated(surface: Surface) {
        // Only create SwapChain (lightweight, tied to Surface)
        swapChain = engine.createSwapChain(surface)
    }

    fun onSurfaceDestroyed() {
        // Only destroy SwapChain (Engine persists)
        engine.destroySwapChain(swapChain)
        swapChain = null
    }

    override fun onCleared() {
        // Full cleanup only when ViewModel destroyed
        engine.destroy()
    }
}
```

Benefits:
- Screen rotation → SwapChain recreated, Engine/Scene persist → No black screen
- Backgrounding → SwapChain destroyed, Engine persists → Instant restore
- Scene graph intact across config changes → Better UX

Testing ARCH-AR-005:
```kotlin
@Test
fun testEngineLifecycle() {
    // 1. Create ViewModel
    val viewModel = ArViewModel()
    val engineRef = viewModel.engine // Store reference

    // 2. Simulate surface created
    viewModel.onSurfaceCreated(mockSurface)

    // 3. Simulate screen rotation (surface destroyed)
    viewModel.onSurfaceDestroyed()

    // 4. Simulate surface recreated
    viewModel.onSurfaceCreated(mockSurface2)

    // 5. Verify Engine is SAME instance (not recreated)
    assertSame(engineRef, viewModel.engine)

    // 6. Verify scene intact (entities still present)
    assertEquals(initialEntityCount, viewModel.scene.entityCount)
}
```

**Rotation Gesture Workflow:**

Two-stage workflow is MUCH better UX than traditional AR apps:

Traditional (worse UX):
1. User taps → Anchor placed immediately
2. Model appears, orientation random
3. User tries to rotate anchored model (awkward gestures)
4. Model jumps around, hard to control

Our approach (better UX):
1. User taps → PREVIEW appears (NO anchor)
2. User rotates preview freely (two-finger gesture)
3. User confirms → Anchor created with chosen orientation
4. Model stays in place, user satisfied

State machine:
```
SCANNING (looking for planes)
   ↓ (tap on plane)
PREVIEW (model visible, rotatable, NO anchor)
   ↓ (confirm button)
PLACED (anchor created, model fixed)
```

Code pattern:
```kotlin
when (state) {
    is ArState.Scanning -> {
        // Show plane visualization
        // Show "Point at floor" instruction
        renderPlanes()
    }

    is ArState.Preview -> {
        // Show model at hit pose (no anchor yet)
        // Show rotation controls
        // Show Cancel/Confirm buttons
        val transform = hitPose.toMatrix() * rotateY(state.rotation)
        renderModel(transform)
    }

    is ArState.Placed -> {
        // Show model at anchor
        // Hide placement controls
        // Show measurement tools
        val transform = anchor.pose.toMatrix() * rotateY(state.rotation)
        renderModel(transform)
    }
}
```

**Coordinate Mapping Edge Cases:**

Drawing coordinates are in millimeters (0-50,000mm typical). AR space is in meters (0-50m typical). Need careful mapping with guards.

Edge Case 1: Tiny model (e.g., 50mm toy structure)
```kotlin
// Without guard: 50mm → 0.05m (too small to see in AR)
// With guard: Scale up to 0.1m minimum
val scaledSize = max(0.1, originalSize * 0.001)
```

Edge Case 2: Huge model (e.g., 500m bridge)
```kotlin
// Without guard: 500m model → exceeds far plane (100m) → culled (invisible)
// With guard: Scale down to 100m AND adjust far plane
val scaledSize = min(100.0, originalSize * 0.001)
camera.setProjection(..., far = scaledSize * 2.0)
```

Edge Case 3: Extreme aspect ratio (e.g., 1mm tall, 50m long)
```kotlin
// Without guard: Flat pancake in AR (looks wrong)
// With guard: Normalize aspect ratio OR warn user
val aspectRatio = max / min
if (aspectRatio > 100) {
    showWarning("Model very flat, may not display well in AR")
}
```

Bounding box calculation:
```kotlin
fun calculateBoundingBox(nodes: List<Node3D>): BoundingBox {
    val xs = nodes.map { it.x }
    val ys = nodes.map { it.y }
    val zs = nodes.map { it.z }

    return BoundingBox(
        min = Vec3(xs.minOrNull() ?: 0.0, ys.minOrNull() ?: 0.0, zs.minOrNull() ?: 0.0),
        max = Vec3(xs.maxOrNull() ?: 0.0, ys.maxOrNull() ?: 0.0, zs.maxOrNull() ?: 0.0)
    )
}
```

**Performance Targets:**

| Metric | Target | Measurement |
|--------|--------|-------------|
| Frame rate | 60 FPS | Frame time <16ms average |
| Frame rate p99 | >50 FPS | Frame time <20ms p99 |
| Model load time | <1s | 100-member model |
| Gesture latency | <50ms | Rotation gesture → render |
| Memory (JVM) | <100MB | Peak during AR session |
| Memory (native) | <50MB | Filament entities |
| Cold start | <2s | Launch → AR ready |

Optimization tips:
- Use indexed geometry (don't duplicate vertices)
- Batch similar materials (reduce state changes)
- Use LOD (level of detail) for distant objects
- Cull off-screen entities (frustum culling)
- Reuse vertex buffers (don't recreate each frame)
- Use instanced rendering for repeated geometry

**Native Memory Debugging:**

Filament allocates native memory (C++). Standard tools don't see it.

Check native heap growth:
```bash
# Before AR session
adb shell dumpsys meminfo com.yourapp | grep "Native Heap"
# Native Heap: 25000 KB

# After 20 AR sessions
adb shell dumpsys meminfo com.yourapp | grep "Native Heap"
# Native Heap: 27000 KB (acceptable growth)

# Bad: Native Heap: 100000 KB (leak!)
```

Common leak sources:
- Engine not destroyed (in onCleared)
- SwapChain not destroyed (in surfaceDestroyed)
- Entities not destroyed (when removing model)
- MaterialInstances not destroyed
- VertexBuffer/IndexBuffer not destroyed
- Texture not destroyed

Proper cleanup pattern:
```kotlin
// Track everything created
val entities = mutableListOf<Int>()
val materials = mutableListOf<MaterialInstance>()
val buffers = mutableListOf<VertexBuffer>()

// Clean up in reverse order (LIFO)
fun cleanup() {
    // 1. Remove from scene
    entities.forEach { scene.removeEntity(it) }

    // 2. Destroy entities
    entities.forEach { engine.destroyEntity(it) }
    entities.clear()

    // 3. Destroy materials
    materials.forEach { engine.destroyMaterialInstance(it) }
    materials.clear()

    // 4. Destroy buffers
    buffers.forEach { engine.destroyVertexBuffer(it) }
    buffers.clear()
}
```

**Material System Design:**

Different structural profiles should be visually distinct.

Profile → Material mapping:
```kotlin
val materialMap = mapOf(
    "W" to steelBeamMaterial,    // I-beams: Dark gray metallic
    "HSS" to steelTubeMaterial,  // Hollow tubes: Light gray metallic
    "C" to channelMaterial,       // Channels: Medium gray
    "L" to angleMaterial,         // Angles: Blue-gray
    "default" to defaultMaterial  // Unknown: White
)

fun getMaterial(profileRef: String?): MaterialInstance {
    val prefix = profileRef?.take(1) ?: ""
    return materialMap[prefix] ?: materialMap["default"]!!
}
```

Material properties (PBR - Physically Based Rendering):
- `baseColor`: Surface color (RGB)
- `metallic`: 0.0 (dielectric) to 1.0 (metal)
- `roughness`: 0.0 (mirror) to 1.0 (matte)
- `reflectance`: Fresnel reflectance at normal incidence
- `clearCoat`: Optional clear coat layer

Steel I-beam material:
```kotlin
Material.Builder()
    .baseColor(0.25f, 0.25f, 0.25f, 1.0f) // Dark gray
    .metallic(1.0f)                        // Full metallic
    .roughness(0.4f)                       // Slightly rough (not mirror)
    .reflectance(0.5f)                     // Moderate reflectance
    .build(engine)
```

**Troubleshooting Common Issues:**

| Issue | Symptom | Cause | Solution |
|-------|---------|-------|----------|
| Black screen after rotation | Screen black after device rotation | Engine destroyed with Surface | Implement ARCH-AR-005 |
| Model invisible | Model loaded but not visible | Outside camera frustum | Adjust far plane, check coordinates |
| Low FPS | Stuttering, dropped frames | Too many entities or materials | Optimize geometry, batch materials |
| Memory leak | App crashes after several sessions | Entities not destroyed | Implement cleanup in onCleared |
| Model too small | Tiny model, can't see it | Coordinate scale wrong | Apply 100x scale multiplier |
| Model too large | Model clipped, partially visible | Exceeds far plane | Scale down OR increase far plane |
| Anchor drifts | Model moves slowly over time | Normal ARCore behavior | Can't fix, ARCore limitation |
| No planes detected | Can't place anchor | Poor lighting or texture | Improve environment, point at textured surface |

---

*Last updated: 2026-02-26*
*Sprint status: Ready for implementation*
*Previous sprint: Sprint 4.5 - Backend Integration*
*Next sprint: Sprint 6 - OCR Auto-Assist (Dimension Recognition)*
