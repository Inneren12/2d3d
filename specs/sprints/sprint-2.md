# Sprint 2: Capture & CV Pipeline - Camera → Quality Gate → rectified.png

**Status:** Ready
**Dates:** 2026-03-19 — 2026-04-16
**Duration:** 4 weeks
**Team:** 2 Android developers (1 CV specialist)
**Goal:** Implement camera capture with Computer Vision pipeline to produce rectified, quality-verified blueprint images ready for manual editing.

**Key Deliverables:**
- `feature-drawing-import` module (Android + OpenCV)
- Camera capture with CameraX (handles EXIF rotation automatically)
- Page detection using OpenCV contour finding
- Perspective correction (homography with dynamic aspect ratio)
- Quality gates (blur, exposure, keystone detection)
- Atomic artifact storage with manifest
- 48MP image handling without OOM
- SSIM-based golden tests for CV pipeline

**Critical Architecture Applied:**
- ARCH-JNI-002: JNI Local Reference Table protection (ByteBuffer for cancellation)
- ARCH-MATH-001: Double precision for geometry calculations
- File-level SHA256 for image integrity (not pixel-level)

---

## Pack A: Camera Screen Foundation

### PR-1: Module `feature-drawing-import` Setup

**Context (Human):** Create the Android feature module for camera capture. This is our first Android-dependent module - unlike Sprint 1's pure JVM code, this needs Android SDK and will run instrumentation tests on real devices.

<ac-block id="S2-PR1-AC1">
**Acceptance Criteria for PR1 (Module Setup)**:
- [ ] Create module `feature-drawing-import` in `/feature/drawing-import/` directory
- [ ] Module path in settings.gradle.kts: `include(":feature:drawing-import")`
- [ ] build.gradle.kts includes: `id("com.android.library")`, Kotlin Android plugin
- [ ] Dependencies: AndroidX Core, CameraX (camera2, lifecycle, view), Hilt for DI, Navigation component
- [ ] Module compiles: `./gradlew :feature:drawing-import:assembleDebug`
- [ ] Empty instrumentation test runs: `./gradlew :feature:drawing-import:connectedAndroidTest`
- [ ] Add dependency to `:core:drawing2d` and `:core:storage`
- [ ] Create basic AndroidManifest.xml with CAMERA permission
- [ ] File location: `/feature/drawing-import/build.gradle.kts`
</ac-block>

---

### PR-2: Runtime Permissions + Lifecycle

**Context (Human):** Handle camera permissions properly. Modern Android requires runtime permission requests and proper handling of denials/revocations.

<ac-block id="S2-PR2-AC1">
**Acceptance Criteria for PR2 (Permissions)**:
- [ ] Create `CameraPermissionManager` class with methods: `checkPermission()`, `requestPermission()`, `shouldShowRationale()`
- [ ] Use `ActivityResultContracts.RequestPermission()` for permission request
- [ ] Handle permission states: GRANTED, DENIED, PERMANENTLY_DENIED
- [ ] Show clear rationale dialog if user denies permission
- [ ] ViewModel observes permission state via StateFlow
- [ ] Screen cannot proceed to preview without CAMERA permission
- [ ] Test: Mock permission grant/deny flows
- [ ] Test: ViewModel state updates correctly on permission changes
- [ ] Code coverage >80%
- [ ] File location: `feature/drawing-import/src/main/kotlin/.../CameraPermissionManager.kt`
</ac-block>

---

### PR-3: CameraX Preview UI

**Context (Human):** Basic camera preview using CameraX. This is simpler and more reliable than the old Camera2 API. No need for manual EXIF handling - CameraX will handle rotation for us in PR-7b.

<ac-block id="S2-PR3-AC1">
**Acceptance Criteria for PR3 (Preview)**:
- [ ] Create `CameraPreviewFragment` with PreviewView from CameraX
- [ ] Bind CameraX Preview use case in ViewModel
- [ ] Use `ProcessCameraProvider.getInstance()` for camera access
- [ ] Select back camera by default: `CameraSelector.DEFAULT_BACK_CAMERA`
- [ ] Preview fills screen with appropriate aspect ratio
- [ ] Lifecycle-aware: stop preview when fragment paused
- [ ] Handle camera unavailable errors gracefully with user message
- [ ] Test: Preview starts when fragment resumed with permission granted
- [ ] Test: Preview stops when fragment paused
- [ ] UI test: PreviewView is visible and rendering (Espresso)
- [ ] File location: `feature/drawing-import/src/main/kotlin/.../CameraPreviewFragment.kt`
</ac-block>

---

### PR-4: Save Raw Image to Storage

**Context (Human):** Capture photo and save to project directory. Uses atomic write from Sprint 1. Raw image is the original unmodified photo - we'll process it later.

<ac-block id="S2-PR4-AC1">
**Acceptance Criteria for PR4 (Capture)**:
- [ ] Add capture button to UI that triggers photo capture
- [ ] Use CameraX `ImageCapture` use case with `CAPTURE_MODE_MAXIMIZE_QUALITY`
- [ ] Save to project directory using `StorageLayout.getArtifactFile(projectDir, ArtifactPath.RAW_IMAGE)`
- [ ] Use `AtomicFileWriter.write()` from Sprint 1 for crash-safe writes
- [ ] Compute SHA256 hash of saved file
- [ ] Create `ArtifactEntryV1` with kind=RAW_IMAGE, filepath, hash, size
- [ ] Show loading indicator during capture
- [ ] Test: Capture button triggers ImageCapture.takePicture()
- [ ] Test: File saved to correct location with correct name (raw.jpg)
- [ ] Test: ArtifactEntry created with valid SHA256 hash
- [ ] Instrumentation test: End-to-end capture saves file successfully
- [ ] File location: `feature/drawing-import/src/main/kotlin/.../CaptureViewModel.kt`
</ac-block>

---

### PR-5: Result Preview Screen

**Context (Human):** Show captured image before processing. User can review and retake if needed. Must handle large images without OOM.

<ac-block id="S2-PR5-AC1">
**Acceptance Criteria for PR5 (Result Preview)**:
- [ ] Create `CaptureResultFragment` that displays captured image
- [ ] Load image using `BitmapFactory.decodeFile()` with `inSampleSize` for memory safety
- [ ] Use `MemoryGuard.calculateInSampleSize()` to determine safe sample rate (target: 2048px max dimension)
- [ ] Show "Retake" and "Process" buttons
- [ ] "Retake" returns to camera, deletes current raw image
- [ ] "Process" navigates to processing screen
- [ ] Handle orientation correctly (CameraX will handle this in PR-7b)
- [ ] Test: Large image (48MP) loads without OOM
- [ ] Test: Image displayed at reasonable resolution
- [ ] Test: Retake button deletes file and returns to camera
- [ ] UI test: Both buttons visible and clickable
- [ ] File location: `feature/drawing-import/src/main/kotlin/.../CaptureResultFragment.kt`
</ac-block>

---

### PR-6: Diagnostics + Logging

**Context (Human):** Instrument the capture flow with events for debugging and analytics. Track success rates, failure reasons, timing.

<ac-block id="S2-PR6-AC1">
**Acceptance Criteria for PR6 (Diagnostics)**:
- [ ] Create sealed class `CaptureEvent` with subtypes: `CaptureStarted`, `CaptureSuccess`, `CaptureFailed(reason)`, `ProcessingStarted`, `ProcessingComplete(durationMs)`
- [ ] Log events using Timber or similar structured logger
- [ ] Include metadata: timestamp, device model, camera resolution
- [ ] Failed captures include error type (OOM, permission denied, camera unavailable)
- [ ] Processing events include duration in milliseconds
- [ ] Events can be disabled in release builds via BuildConfig flag
- [ ] Test: Events logged with correct data
- [ ] Test: No PII (Personally Identifiable Information) in logs
- [ ] File location: `feature/drawing-import/src/main/kotlin/.../diagnostics/CaptureEvent.kt`
</ac-block>

---

## Pack B: Page Detection (OpenCV)

### PR-7a: MemoryGuard - Safe Image Loading

**Context (Human):** 48MP images are ~150MB uncompressed. Loading full resolution causes instant OOM on most devices. MemoryGuard calculates safe sample rate.

<ac-block id="S2-PR7a-AC1">
**Acceptance Criteria for PR7a (MemoryGuard)**:
- [ ] Create `object MemoryGuard` with function `calculateInSampleSize(srcWidth: Int, srcHeight: Int, maxDimension: Int = 2048): Int`
- [ ] Algorithm: Start with `inSampleSize = 1`, double until both dimensions fit within maxDimension
- [ ] Return power of 2 (BitmapFactory requirement: 1, 2, 4, 8, 16...)
- [ ] Test: 48MP (8000×6000) with maxDimension=2048 returns inSampleSize=4 (produces 2000×1500)
- [ ] Test: Small image (1000×1000) with maxDimension=2048 returns inSampleSize=1 (no downscaling)
- [ ] Test: Extremely large image (16000×12000) returns appropriate inSampleSize
- [ ] Integration test: Loading 48MP image with calculated inSampleSize keeps heap usage <64MB
- [ ] Code coverage >90%
- [ ] File location: `feature/drawing-import/src/main/kotlin/.../cv/MemoryGuard.kt`
</ac-block>

---

### PR-7b: Image Decode - CameraX Automatic Rotation

**Context (Human):** CRITICAL: CameraX can bake rotation directly into JPEG, avoiding manual EXIF handling and in-memory bitmap rotation (which causes OOM). This is the preferred approach per ARCH recommendation.

<ac-block id="S2-PR7b-AC1">
**Acceptance Criteria for PR7b (Image Decode)**:
- [ ] Configure ImageCapture with `setTargetRotation(Surface.ROTATION_0)` so CameraX handles rotation automatically
- [ ] Verify saved JPEG file has EXIF orientation = 1 (NORMAL/0 degrees) - no manual rotation needed
- [ ] Use `BitmapFactory.decodeFile()` with `inSampleSize` from MemoryGuard for memory-safe loading
- [ ] NO manual bitmap rotation in memory (CameraX pre-rotates the file)
- [ ] Test: Capture in portrait mode, verify saved JPEG is already rotated correctly (EXIF=1)
- [ ] Test: Capture in landscape mode, verify saved JPEG is already rotated correctly (EXIF=1)
- [ ] Test: Load 48MP image with inSampleSize=4, verify heap usage <64MB
- [ ] Test on Xiaomi/Oppo devices (if available) to verify vendor-specific EXIF handling works
- [ ] Documentation: Explain why CameraX handles rotation (EXIF vendor inconsistencies)
- [ ] File location: `feature/drawing-import/src/main/kotlin/.../cv/ImageDecoder.kt`
</ac-block>

---

### PR-7c: Native Memory Cleanup

**Context (Human):** OpenCV Mat objects allocate native memory outside Java heap. If not released, causes memory leaks that Leak Canary can't detect (native heap, not Java heap).

<ac-block id="S2-PR7c-AC1">
**Acceptance Criteria for PR7c (OpenCV Cleanup)**:
- [ ] All OpenCV Mat objects released in `finally` blocks or via `use {}` pattern
- [ ] Create extension function `Mat.use(block: (Mat) -> T): T` that ensures `mat.release()` called
- [ ] Test: Create 100 Mat objects in loop, verify all released (no native memory leak)
- [ ] Test: Exception during CV processing still releases Mat objects
- [ ] Leak Canary: Run capture flow 10 times, verify 0 leaks reported
- [ ] Code coverage >85% for cleanup paths
- [ ] File location: `feature/drawing-import/src/main/kotlin/.../cv/OpenCVExtensions.kt`
</ac-block>

---

### PR-8: Edge Detection (Canny)

**Context (Human):** Find edges in the image using Canny algorithm. This is the first step in detecting the document boundary. Output is binary image (black/white).

<ac-block id="S2-PR8-AC1">
**Acceptance Criteria for PR8 (Canny)**:
- [ ] Implement `fun detectEdges(image: Mat): Mat` using OpenCV Canny algorithm
- [ ] Pre-processing: Convert to grayscale, apply Gaussian blur (kernel 5×5)
- [ ] Canny parameters: low threshold = 50, high threshold = 150 (good defaults for documents)
- [ ] Return binary Mat (CV_8UC1) with edges as white pixels
- [ ] Memory: Input Mat NOT modified (create new Mat for output)
- [ ] Test: Synthetic image with clear rectangle produces expected edge map
- [ ] Test: Output Mat is single-channel (grayscale)
- [ ] Test: All Mat objects released properly (no leaks)
- [ ] Golden test: `test_blueprint_01.jpg` → edges match reference `edges_expected.png` (SSIM > 0.95)
- [ ] Code coverage >85%
- [ ] File location: `feature/drawing-import/src/main/kotlin/.../cv/EdgeDetector.kt`
</ac-block>

---

### PR-9: Contour Finding + Largest Quad Selection

**Context (Human):** Find all contours in edge map, then select the largest quadrilateral (4-sided polygon). This should be the document boundary.

<ac-block id="S2-PR9-AC1">
**Acceptance Criteria for PR9 (Contours)**:
- [ ] Implement `fun findLargestQuad(edgeMap: Mat): List<Point>?` that returns 4 corner points or null
- [ ] Use `Imgproc.findContours()` with `RETR_EXTERNAL` mode (only outer contours)
- [ ] For each contour, approximate to polygon using `Imgproc.approxPolyDP()` with epsilon = 2% of perimeter
- [ ] Filter: Keep only contours with exactly 4 points (quadrilaterals)
- [ ] Select quadrilateral with largest area: `Imgproc.contourArea()`
- [ ] Convexity check: Use `Imgproc.isContourConvex()` - reject concave quads
- [ ] Return null if no valid quad found (image rejected)
- [ ] Test: Synthetic image with rectangle returns correct 4 corners
- [ ] Test: Image with multiple rectangles returns largest one
- [ ] Test: Image with no clear quad returns null
- [ ] Test: Non-convex polygon rejected
- [ ] Golden test: `test_blueprint_01.jpg` detects quad matching reference coordinates (within 5px tolerance)
- [ ] Code coverage >85%
- [ ] File location: `feature/drawing-import/src/main/kotlin/.../cv/ContourDetector.kt`
</ac-block>

---

### PR-10: Corner Ordering (TL/TR/BR/BL)

**Context (Human):** OpenCV returns corners in arbitrary order. We need consistent TL→TR→BR→BL ordering for homography matrix calculation. Use geometric sorting, not assumptions about contour direction.

<ac-block id="S2-PR10-AC1">
**Acceptance Criteria for PR10 (Corner Sorting)**:
- [ ] Implement `fun orderCorners(points: List<Point>): OrderedCorners` that returns data class with `topLeft`, `topRight`, `bottomRight`, `bottomLeft` fields
- [ ] Algorithm:
  1. Sort by Y coordinate to find top 2 and bottom 2 points
  2. Within top pair: smaller X = topLeft, larger X = topRight
  3. Within bottom pair: smaller X = bottomLeft, larger X = bottomRight
- [ ] Handle edge case: if Y coordinates too close (< 5px), use X coordinate as primary sort
- [ ] Return type: `data class OrderedCorners(val topLeft: Point, val topRight: Point, val bottomRight: Point, val bottomLeft: Point)`
- [ ] Test: 4 random points sorted correctly to TL/TR/BR/BL
- [ ] Test: Square corners produce correct ordering regardless of input order
- [ ] Test: Edge case with nearly horizontal document handled correctly
- [ ] Code coverage >90%
- [ ] File location: `feature/drawing-import/src/main/kotlin/.../cv/CornerOrdering.kt`
</ac-block>

---

### PR-11: Sub-pixel Refinement

**Context (Human):** Improve corner accuracy using OpenCV's cornerSubPix. This refines corners to sub-pixel precision, important for large documents where 1px error = multiple mm.

<ac-block id="S2-PR11-AC1">
**Acceptance Criteria for PR11 (Sub-pixel)**:
- [ ] Implement `fun refineCorners(image: Mat, corners: List<Point>): List<Point>` using `Imgproc.cornerSubPix()`
- [ ] Convert image to grayscale if not already
- [ ] Window size: 11×11 pixels (good balance between accuracy and robustness)
- [ ] Termination criteria: max 30 iterations or epsilon=0.1 convergence
- [ ] Input corners modified in-place (OpenCV requirement)
- [ ] Verify refinement improved accuracy (corners moved < 2px typically)
- [ ] Test: Synthetic corner at (100.0, 100.0) refined to sub-pixel precision
- [ ] Test: Function handles corners near image edges without crash
- [ ] Test: Grayscale conversion handled correctly
- [ ] Code coverage >80%
- [ ] File location: `feature/drawing-import/src/main/kotlin/.../cv/CornerRefinement.kt`
</ac-block>

---

### PR-12: Debug Visualization (corners.png)

**Context (Human):** Save debug image showing detected corners for troubleshooting. This helps diagnose detection failures without needing debugger.

<ac-block id="S2-PR12-AC1">
**Acceptance Criteria for PR12 (Debug Viz)**:
- [ ] Implement `fun drawDebugCorners(image: Mat, corners: OrderedCorners, outputPath: String)`
- [ ] Draw green circles (radius 10px) at each corner
- [ ] Draw green lines connecting corners to form quadrilateral
- [ ] Label each corner: TL, TR, BR, BL (white text)
- [ ] Save to `debug/corners.png` in project directory using `StorageLayout`
- [ ] Use `Imgproc.circle()`, `Imgproc.line()`, `Imgproc.putText()` for drawing
- [ ] Debug images only created if debug mode enabled (BuildConfig.DEBUG or explicit flag)
- [ ] Test: Debug image created with correct drawings
- [ ] Test: No crash if output directory doesn't exist (create it)
- [ ] Test: Debug mode disabled = no file created
- [ ] File location: `feature/drawing-import/src/main/kotlin/.../cv/DebugRenderer.kt`
</ac-block>

---

### PR-13: Failure Reasons + Error Codes

**Context (Human):** When page detection fails, provide specific reason so user knows what to fix. Clear errors improve UX dramatically.

<ac-block id="S2-PR13-AC1">
**Acceptance Criteria for PR13 (Error Codes)**:
- [ ] Create sealed class `PageDetectionError` with subtypes:
  - `NoEdgesFound(threshold: Double)` - Canny didn't find enough edges
  - `NoContoursFound` - No closed contours in edge map
  - `NoQuadFound(candidateCount: Int)` - Found contours but none are 4-sided
  - `QuadTooSmall(area: Double, minArea: Double)` - Quad smaller than 10% of image
  - `QuadNotConvex` - Best quad is concave (likely wrong detection)
- [ ] Each error has human-readable `message: String` property
- [ ] Localized messages: "Move closer to document", "Ensure document is flat", "Improve lighting"
- [ ] Test: Each error type produces clear, actionable message
- [ ] Test: Messages suitable for display in UI toast/dialog
- [ ] File location: `feature/drawing-import/src/main/kotlin/.../cv/PageDetectionError.kt`
</ac-block>

---

## Pack C: Rectification Pipeline

### PR-14: Homography Computation

**Context (Human):** Compute perspective transformation matrix that will unwarp the document. This maps quad corners to rectangle corners.

<ac-block id="S2-PR14-AC1">
**Acceptance Criteria for PR14 (Homography)**:
- [ ] Implement `fun computeHomography(sourceCorners: OrderedCorners, destSize: Size): Mat`
- [ ] Use `Imgproc.getPerspectiveTransform()` with source (actual corners) and destination (rectangle corners)
- [ ] Destination corners: `(0,0)`, `(width,0)`, `(width,height)`, `(0,height)` for output rectangle
- [ ] Return 3×3 transformation matrix (Mat type CV_64FC1)
- [ ] Matrix values use Double precision (ARCH-MATH-001)
- [ ] Verify matrix is valid (not singular, determinant != 0)
- [ ] Test: Known corners produce expected transformation matrix (within floating-point tolerance)
- [ ] Test: Identity transformation (corners already rectangle) produces identity-like matrix
- [ ] Test: Matrix values are finite (no NaN/Infinity)
- [ ] Code coverage >85%
- [ ] File location: `feature/drawing-import/src/main/kotlin/.../cv/HomographyComputer.kt`
</ac-block>

---

### PR-15: Dynamic Destination Size (Aspect Ratio Preservation)

**Context (Human):** CRITICAL FIX: Don't force square output (2000×2000). A4 is 1:1.414 ratio. Forcing square distorts scale, breaking measurements. Calculate output size to preserve physical aspect ratio.

<ac-block id="S2-PR15-AC1">
**Acceptance Criteria for PR15 (Destination Size)**:
- [ ] Implement `fun calculateDestinationSize(corners: OrderedCorners, maxDimension: Int = 2000): Size`
- [ ] Algorithm:
  1. Compute physical width: `max(distance(TL, TR), distance(BL, BR))`
  2. Compute physical height: `max(distance(TL, BL), distance(TR, BR))`
  3. Compute aspect ratio: `aspectRatio = height / width`
  4. If width >= height: output = `Size(maxDimension, (maxDimension * aspectRatio).toInt())`
  5. If height > width: output = `Size((maxDimension / aspectRatio).toInt(), maxDimension)`
- [ ] Use `Point.distanceTo()` from Sprint 1 for distance calculations
- [ ] Test: A4 corners (1:1.414 ratio) produce ~2000×1414 or ~1414×2000 output size
- [ ] Test: Square document produces ~2000×2000 output
- [ ] Test: Very wide document (3:1) produces correct proportions
- [ ] Test: Scale tool in Sprint 3 will work correctly (no distortion)
- [ ] Code coverage >90%
- [ ] File location: `feature/drawing-import/src/main/kotlin/.../cv/DestinationSizeCalculator.kt`
</ac-block>

---

### PR-16: Warp Perspective (Rectification)

**Context (Human):** Apply the perspective transformation to unwarp the document. Output is the straightened, top-down view.

<ac-block id="S2-PR16-AC1">
**Acceptance Criteria for PR16 (Warp)**:
- [ ] Implement `fun warpPerspective(image: Mat, homography: Mat, destSize: Size): Mat`
- [ ] Use `Imgproc.warpPerspective()` with homography matrix and destination size
- [ ] Interpolation: `INTER_LINEAR` (good balance of quality and speed)
- [ ] Border mode: `BORDER_CONSTANT` with white fill (suitable for documents)
- [ ] Output Mat has size exactly matching `destSize`
- [ ] Output Mat type: CV_8UC3 (BGR color) or CV_8UC1 (grayscale) depending on input
- [ ] Test: Warping trapezoid-shaped document produces rectangle
- [ ] Test: Output dimensions match requested destSize
- [ ] Test: White borders added if document doesn't fill frame
- [ ] Golden test: Warp `test_blueprint_01.jpg` produces output matching reference (SSIM > 0.95)
- [ ] Code coverage >85%
- [ ] File location: `feature/drawing-import/src/main/kotlin/.../cv/PerspectiveWarper.kt`
</ac-block>

---

### PR-17: Encode rectified.png + Pipeline Orchestration with JNI Safety

**Context (Human):** Save the rectified image and orchestrate the entire CV pipeline. CRITICAL: Must handle JNI cancellation safely to avoid Local Reference Table overflow (ARCH-JNI-002).

<ac-block id="S2-PR17-AC1">
**Acceptance Criteria for PR17 (Pipeline + JNI Safety)**:
- [ ] Implement `fun encodePng(image: Mat, outputPath: String)` using `Imgproc.imwrite()` with PNG format (lossless)
- [ ] Implement pipeline orchestrator `class PageDetectionPipeline` with `suspend fun process(inputPath: String, outputDir: File): Result<RectifiedImage>`
- [ ] Pipeline steps (in order):
  1. Load image with MemoryGuard inSampleSize
  2. Detect edges (Canny)
  3. Find contours and select largest quad
  4. Order corners (TL/TR/BR/BL)
  5. Refine corners (sub-pixel)
  6. Calculate destination size (preserve aspect ratio)
  7. Compute homography
  8. Warp perspective
  9. Encode PNG to `rectified.png`
  10. Compute SHA256 hash
  11. Save debug visualization if enabled
- [ ] Cancellation support: Accept `cancelFlag: ByteBuffer` and check it between pipeline steps
- [ ] JNI Safety (ARCH-JNI-002): Use `ByteBuffer.allocateDirect(1)` for cancel flag (not AtomicBoolean in JNI loop)
- [ ] All Mat objects released in finally blocks (no native leaks)
- [ ] Return `Result.Success(RectifiedImage)` or `Result.Failure(PageDetectionError)`
- [ ] Timeout: Entire pipeline must complete within 10 seconds or return timeout error
- [ ] Test: End-to-end pipeline processes sample image successfully
- [ ] Test: Cancellation stops pipeline and releases all resources
- [ ] Test: Pipeline failure releases all Mat objects (no leaks)
- [ ] Test: Timeout triggers after 10s, resources cleaned up
- [ ] Instrumentation test: Process real 48MP photo without OOM
- [ ] Battery test: 1000 cancellations during CV processing produce no SIGSEGV crashes
- [ ] Code coverage >80% for pipeline orchestration
- [ ] File locations:
  - `feature/drawing-import/src/main/kotlin/.../cv/PageDetectionPipeline.kt`
  - `feature/drawing-import/src/main/kotlin/.../cv/PngEncoder.kt`
</ac-block>

---

### PR-18: Performance Guardrails

**Context (Human):** Set strict limits on execution time and resource usage. Prevents infinite loops and protects user's device from battery drain.

<ac-block id="S2-PR18-AC1">
**Acceptance Criteria for PR18 (Performance)**:
- [ ] Pipeline timeout: 10 seconds max for entire CV processing
- [ ] Image size cap: Maximum 48MP input (8000×6000 pixels), larger images rejected with error "Image too large: {width}×{height}. Maximum: 8000×6000"
- [ ] Memory budget: Peak heap usage <128MB during processing
- [ ] Output size cap: rectified.png maximum 4000×4000 pixels (even if input larger)
- [ ] Execution time logged for each pipeline step (diagnostics)
- [ ] If any step exceeds timeout, cancel remaining steps and return error
- [ ] Test: 48MP image processes within 10 seconds
- [ ] Test: 100MP image rejected with clear error message
- [ ] Test: Synthetic slow operation triggers timeout and cleans up
- [ ] Performance test: Process image 10 times, verify heap doesn't grow (no leaks)
- [ ] File location: Add to `PageDetectionPipeline.kt`
</ac-block>

---

## Pack D: Quality Checks

### PR-19: Blur Detection (Laplacian Variance on Content Region)

**Context (Human):** Detect out-of-focus images using Laplacian variance metric. CRITICAL: Compute ONLY on document region (not entire frame) and normalize by downscaling to stable resolution. Prevents false positives from background.

<ac-block id="S2-PR19-AC1">
**Acceptance Criteria for PR19 (Blur Detection)**:
- [ ] Implement `fun detectBlur(image: Mat, contentRegion: Rect): BlurMetric` where Rect is the detected document bounding box
- [ ] Algorithm:
  1. Crop image to contentRegion (document only, exclude background)
  2. Downscale to 800×600 for stable threshold (use `Imgproc.resize()`)
  3. Convert to grayscale
  4. Apply Laplacian filter: `Imgproc.Laplacian(gray, laplacian, CV_64F)`
  5. Compute variance: `Core.meanStdDev()` then `variance = stddev^2`
- [ ] Return `data class BlurMetric(val variance: Double, val isBlurred: Boolean)`
- [ ] Threshold: `variance < 50.0` = blurred (empirically determined for 800×600 resolution)
- [ ] Test: Sharp synthetic document (checkerboard pattern) returns variance > 50
- [ ] Test: Blurred synthetic document (Gaussian blur applied) returns variance < 50
- [ ] Test: Empty white region returns very low variance (< 10)
- [ ] Test: Threshold stable across different document sizes (due to downscaling)
- [ ] Golden test: Sharp blueprint returns isBlurred=false, blurred blueprint returns isBlurred=true
- [ ] Code coverage >85%
- [ ] File location: `feature/drawing-import/src/main/kotlin/.../cv/BlurDetector.kt`
</ac-block>

---

### PR-20: Exposure Metrics (Histogram Analysis)

**Context (Human):** Detect overexposed (blown highlights) or underexposed (lost shadow detail) images. Uses histogram analysis.

<ac-block id="S2-PR20-AC1">
**Acceptance Criteria for PR20 (Exposure)**:
- [ ] Implement `fun analyzeExposure(image: Mat): ExposureMetric`
- [ ] Convert to grayscale, compute histogram with 256 bins
- [ ] Calculate percentage of pixels in brightest 5% (bin 242-255) = `highlightClipping`
- [ ] Calculate percentage of pixels in darkest 5% (bin 0-13) = `shadowClipping`
- [ ] Return `data class ExposureMetric(val highlightClipping: Double, val shadowClipping: Double, val isOverexposed: Boolean, val isUnderexposed: Boolean)`
- [ ] Thresholds:
  - `isOverexposed = highlightClipping > 10%`
  - `isUnderexposed = shadowClipping > 10%`
- [ ] Test: Correctly exposed image returns both false
- [ ] Test: Synthetic overexposed image (many white pixels) returns isOverexposed=true
- [ ] Test: Synthetic underexposed image (many black pixels) returns isUnderexposed=true
- [ ] Test: Edge case of fully black/white image handled without crash
- [ ] Code coverage >85%
- [ ] File location: `feature/drawing-import/src/main/kotlin/.../cv/ExposureAnalyzer.kt`
</ac-block>

---

### PR-21: Keystone/Skew Metrics

**Context (Human):** Measure how distorted the document is (keystone effect from angle). Helps decide if rectification will work well or if user should retake.

<ac-block id="S2-PR21-AC1">
**Acceptance Criteria for PR21 (Keystone)**:
- [ ] Implement `fun measureKeystone(corners: OrderedCorners): KeystoneMetric`
- [ ] Calculate 4 corner angles using dot product formula
- [ ] Ideal rectangle has all 90° angles
- [ ] Compute maximum angle deviation: `maxDeviation = max(|angle - 90°|)` for all 4 corners
- [ ] Return `data class KeystoneMetric(val maxAngleDeviation: Double, val isSeverelyDistorted: Boolean)`
- [ ] Threshold: `isSeverelyDistorted = maxDeviation > 30°`
- [ ] Test: Perfect rectangle returns maxDeviation ≈ 0°
- [ ] Test: Trapezoid with 60° and 120° corners returns maxDeviation = 30°
- [ ] Test: Severely skewed quad flagged as distorted
- [ ] Code coverage >85%
- [ ] File location: `feature/drawing-import/src/main/kotlin/.../cv/KeystoneAnalyzer.kt`
</ac-block>

---

### PR-22: Quality Decision Aggregator

**Context (Human):** Combine all quality checks into single pass/fail decision with specific reasons for failure. This is what determines if rectified image is good enough.

<ac-block id="S2-PR22-AC1">
**Acceptance Criteria for PR22 (Quality Gate)**:
- [ ] Implement `fun evaluateQuality(blur: BlurMetric, exposure: ExposureMetric, keystone: KeystoneMetric): QualityDecision`
- [ ] Return `sealed class QualityDecision`:
  - `Pass` - All checks passed
  - `Fail(reasons: List<QualityIssue>)` - One or more checks failed
- [ ] Quality issues enum:
  - `BLURRED` - "Image is out of focus. Hold camera steady."
  - `OVEREXPOSED` - "Too bright. Move away from direct light."
  - `UNDEREXPOSED` - "Too dark. Improve lighting."
  - `SEVERELY_DISTORTED` - "Document too skewed. Position camera directly above."
- [ ] Aggregate logic: Fail if ANY metric indicates problem
- [ ] Multiple issues can be present (e.g., blurred AND overexposed)
- [ ] Test: All metrics OK returns Pass
- [ ] Test: Blur metric failed returns Fail with BLURRED reason
- [ ] Test: Multiple failures return all relevant reasons
- [ ] Test: Reasons have clear, actionable messages for UI display
- [ ] Code coverage >90%
- [ ] File location: `feature/drawing-import/src/main/kotlin/.../cv/QualityAggregator.kt`
</ac-block>

---

## Pack E: Artifact Saving

### PR-23: capture_meta.json Writer

**Context (Human):** Save metadata about the capture: device info, quality scores, timestamp. Useful for debugging and analytics.

<ac-block id="S2-PR23-AC1">
**Acceptance Criteria for PR23 (Metadata)**:
- [ ] Create `data class CaptureMetadataV1` with fields:
  - `schemaVersion: Int = 1`
  - `timestamp: Long` (epoch millis)
  - `deviceModel: String` (Build.MODEL)
  - `deviceManufacturer: String` (Build.MANUFACTURER)
  - `androidVersion: Int` (Build.VERSION.SDK_INT)
  - `cameraResolution: String` (e.g., "8000×6000")
  - `qualityScores: QualityScores` (blur variance, exposure percentages, keystone deviation)
  - `processingDurationMs: Long`
- [ ] Serialize to JSON with kotlinx.serialization
- [ ] Save to `capture_meta.json` in project directory
- [ ] Use pretty print for readability
- [ ] Test: Metadata serializes with all fields present
- [ ] Test: Deserialization round-trip preserves data
- [ ] File location: `feature/drawing-import/src/main/kotlin/.../model/CaptureMetadata.kt`
</ac-block>

---

### PR-24: Atomic Write + Rollback

**Context (Human):** Save all artifacts atomically. If any step fails, rollback entire operation. User never sees partial/corrupted state.

<ac-block id="S2-PR24-AC1">
**Acceptance Criteria for PR24 (Atomic Save)**:
- [ ] Implement `class ArtifactSaver` with method `suspend fun saveArtifacts(projectId: String, artifacts: CaptureArtifacts): Result<Unit>`
- [ ] Transaction pattern:
  1. Write all files to `.tmp` suffixes
  2. Compute SHA256 for each file
  3. Verify all writes succeeded
  4. Atomically rename all `.tmp` files to final names
  5. On error: delete all `.tmp` files, throw exception
- [ ] Files to save: raw.jpg, rectified.png, debug/corners.png (if debug), capture_meta.json
- [ ] Use `AtomicFileWriter` from Sprint 1 for each file
- [ ] All-or-nothing: Either ALL files saved or NONE (no partial state)
- [ ] Test: Successful save creates all expected files
- [ ] Test: Simulated failure during write leaves no partial files (.tmp cleaned up)
- [ ] Test: Disk full error triggers rollback and cleanup
- [ ] Test: After rollback, project directory is empty (clean state)
- [ ] Code coverage >85%
- [ ] File location: `feature/drawing-import/src/main/kotlin/.../storage/ArtifactSaver.kt`
</ac-block>

---

### PR-25: Manifest Finalization

**Context (Human):** Write manifest.json with all artifact entries and their SHA256 hashes. This completes the atomic save transaction.

<ac-block id="S2-PR25-AC1">
**Acceptance Criteria for PR25 (Manifest)**:
- [ ] Create `ManifestV1` (from Sprint 1) with all artifact entries
- [ ] Each entry includes: `kind`, `relativePath`, `fileSha256`, `sizeBytes`, `createdAt`
- [ ] Add `updatedAt` timestamp to manifest
- [ ] Serialize manifest to JSON (pretty print)
- [ ] Save to `manifest.json` using `AtomicFileWriter`
- [ ] Manifest is LAST file written (indicates complete transaction)
- [ ] Test: Manifest contains all expected artifacts
- [ ] Test: All SHA256 hashes in manifest match actual file hashes
- [ ] Test: Manifest JSON is valid and deserializes correctly
- [ ] Integration test: Full save transaction produces valid manifest
- [ ] File location: `feature/drawing-import/src/main/kotlin/.../storage/ManifestWriter.kt`
</ac-block>

---

### PR-26: Project Folder Management + Reconciliation

**Context (Human):** CRITICAL: On app startup, check for incomplete/corrupted projects and clean them up. Prevents accumulation of broken state from crashes. Implementation of reconciliation pattern.

<ac-block id="S2-PR26-AC1">
**Acceptance Criteria for PR26 (Reconciliation)**:
- [ ] Implement `class ProjectFolderManager` with `fun reconcile(projectDir: File)`
- [ ] Reconciliation logic:
  1. Check if `manifest.json` exists
  2. If missing: Delete entire project directory (incomplete capture)
  3. If exists: Parse manifest
  4. If parse fails (corrupted JSON): Delete entire project directory
  5. Verify all artifacts listed in manifest exist on disk
  6. If any missing: Delete entire project directory
  7. Verify SHA256 hash for each artifact matches manifest
  8. If any mismatch: Delete entire project directory
  9. Delete all `.tmp` files (leftover from crashes)
  10. If all checks pass: Project is valid, keep it
- [ ] Call reconciliation on app startup (Application.onCreate or WorkManager)
- [ ] Run on background thread (Dispatchers.IO)
- [ ] Log reconciliation actions: projects deleted, errors found
- [ ] Test: Valid project passes reconciliation (no deletion)
- [ ] Test: Project with missing manifest is deleted
- [ ] Test: Project with corrupted manifest JSON is deleted
- [ ] Test: Project with missing artifact (from manifest) is deleted
- [ ] Test: Project with corrupted artifact (hash mismatch) is deleted
- [ ] Test: Orphaned .tmp files are cleaned up
- [ ] Instrumentation test: Simulate crash mid-save, verify reconciliation cleans up on restart
- [ ] Code coverage >90%
- [ ] File location: `feature/drawing-import/src/main/kotlin/.../storage/ProjectFolderManager.kt`
</ac-block>

---

## Pack F: Instrumentation Tests

### PR-27: CV Pipeline Unit Tests

**Context (Human):** Comprehensive unit tests for all CV components with synthetic test images. Fast tests that don't require device.

<ac-block id="S2-PR27-AC1">
**Acceptance Criteria for PR27 (Unit Tests)**:
- [ ] Test suite for each CV component (Canny, contours, homography, warp, etc.)
- [ ] Use synthetic test images: solid colors, patterns, shapes
- [ ] Test edge cases: empty images, single-color images, extremely small/large images
- [ ] Test error paths: invalid Mat types, null pointers, out-of-bounds regions
- [ ] Memory leak tests: Create and release 100 Mat objects, verify no leaks
- [ ] All tests run on JVM (not requiring Android device) where possible
- [ ] Test: Each component releases Mat objects properly
- [ ] Test: Each component handles errors without crashing
- [ ] Overall code coverage for `/cv/` package >85%
- [ ] All tests pass: `./gradlew :feature:drawing-import:test`
</ac-block>

---

### PR-28: UI Smoke Tests (Espresso)

**Context (Human):** Basic UI tests to ensure screens load and interactions work. Just smoke tests, not comprehensive UI testing.

<ac-block id="S2-PR28-AC1">
**Acceptance Criteria for PR28 (UI Tests)**:
- [ ] Camera permission screen: Displays rationale, request button works
- [ ] Camera preview: Preview view visible, capture button clickable
- [ ] Result preview: Image displayed, retake/process buttons work
- [ ] Error dialog: Quality failure shows dialog with correct message
- [ ] Use Espresso for UI testing
- [ ] Mock ViewModel states for deterministic tests
- [ ] Tests run on emulator or physical device
- [ ] Test: Permission granted navigates to preview
- [ ] Test: Permission denied shows rationale
- [ ] Test: Capture button triggers image capture
- [ ] All UI tests pass: `./gradlew :feature:drawing-import:connectedAndroidTest`
</ac-block>

---

## Pack G: Documentation & Closeout

### PR-29: Pipeline Documentation

**Context (Human):** Comprehensive documentation of the CV pipeline with diagrams, parameter explanations, troubleshooting guide.

<ac-block id="S2-PR29-AC1">
**Acceptance Criteria for PR29 (Docs)**:
- [ ] Create `/docs/capture/PIPELINE.md`
- [ ] Document must include:
  - Pipeline flow diagram (ASCII art or Mermaid)
  - Each CV step explained with parameters
  - Quality gate thresholds with rationale
  - Memory management strategy (MemoryGuard, inSampleSize)
  - Troubleshooting guide: common failures and solutions
  - Performance characteristics: typical processing time, memory usage
  - ARCH-JNI-002 explanation: why ByteBuffer for cancellation
- [ ] Include code examples for each major component
- [ ] Reference SSIM golden tests location
- [ ] Explain dynamic aspect ratio preservation (why not square)
- [ ] Markdown must be valid with no broken links
- [ ] File location: `/docs/capture/PIPELINE.md`
</ac-block>

---

### PR-30: Sprint 2 Closeout

**Context (Human):** Final verification checklist before moving to Sprint 3.

<ac-block id="S2-PR30-AC1">
**Acceptance Criteria for PR30 (Closeout)**:
- [ ] All 30 PRs merged to main branch
- [ ] Code coverage: `feature-drawing-import` module >75%
- [ ] Ktlint check passes: `./gradlew ktlintCheck` with 0 errors
- [ ] All unit tests pass: `./gradlew test`
- [ ] All instrumentation tests pass: `./gradlew connectedAndroidTest`
- [ ] Leak Canary clean: Run capture flow 10 times, 0 leaks reported
- [ ] Memory test: 48MP image processes with peak heap <128MB
- [ ] Performance test: CV pipeline completes in <10 seconds on mid-range device
- [ ] SSIM golden tests: All test images match references with SSIM >0.95
- [ ] No TODO comments in production code
- [ ] Update root README.md with Sprint 2 deliverables
- [ ] Documentation complete: PIPELINE.md exists and is comprehensive
- [ ] Git tag created: `git tag sprint-2-complete`
- [ ] CameraX lifecycle stress test: Background app 10 min, restore, no crashes (ARCH-JNI-002 validation)
</ac-block>

---

## Sprint 2 Success Metrics

**Definition of Done (Sprint Level):**
- ✅ All 30 PRs completed and merged
- ✅ Camera capture working with 48MP support
- ✅ CV pipeline produces rectified.png with quality gates
- ✅ No memory leaks (Leak Canary clean)
- ✅ No OOM crashes on 48MP images
- ✅ Processing time <10 seconds
- ✅ Golden tests pass with SSIM >0.95
- ✅ Dynamic aspect ratio preservation working
- ✅ JNI safety validated (1000 cancellations, no SIGSEGV)
- ✅ Reconciliation pattern working (cleanup on startup)
- ✅ Ready for Sprint 3 (manual editor)

**Key Deliverables:**
1. `feature-drawing-import` module with full camera + CV pipeline
2. CameraX integration (automatic EXIF handling)
3. OpenCV page detection with sub-pixel accuracy
4. Perspective correction with dynamic sizing
5. Quality gates (blur, exposure, keystone)
6. Atomic artifact storage with reconciliation
7. SSIM-based golden test suite
8. Comprehensive documentation

**Technical Debt:**
- SSIM threshold (0.95) may need tuning based on real-world testing
- Quality gate thresholds (blur=50, exposure=10%) empirically determined, may need field adjustment
- Debug visualization always creates corners.png even in production (consider BuildConfig.DEBUG gate)

**Architectural Decisions Applied:**
- ARCH-JNI-002: ByteBuffer for cancellation flag (not AtomicBoolean in JNI loop)
- ARCH-MATH-001: Double precision for geometry calculations
- CameraX handles EXIF rotation (avoids vendor issues and OOM from manual rotation)
- File-level SHA256 (not pixel-level) for integrity
- Dynamic aspect ratio preservation (not forced square)
- Compute blur metric only on document region, normalized by downscaling

---

## Notes for Developers

**Critical Implementation Details:**
1. **CameraX Rotation**: Set `setTargetRotation(Surface.ROTATION_0)` - CameraX bakes rotation into JPEG
2. **Memory Safety**: Always use MemoryGuard.calculateInSampleSize() before loading images
3. **JNI Safety**: Use ByteBuffer.allocateDirect() for cancel flags, never iterate JNI calls in tight loops
4. **Aspect Ratio**: Use dynamic destination sizing, never force square output
5. **Quality Metrics**: Blur computed on document region only (after downscaling to 800×600)
6. **Reconciliation**: Run on app startup to clean up corrupted projects from crashes

**Dependencies Between PRs:**
- PR-7b depends on PR-3 (CameraX setup)
- PR-8 through PR-11 are sequential (CV pipeline)
- PR-14 through PR-16 are sequential (rectification)
- PR-17 depends on all CV components (orchestration)
- PR-19 through PR-22 depend on PR-17 (quality checks on rectified image)
- PR-24, PR-25 depend on all previous (atomic save)
- PR-26 depends on PR-25 (reconciliation uses manifest)

**Parallel Work Opportunities:**
- Pack A (camera) and Pack B (OpenCV) can be developed in parallel
- Pack D (quality checks) can start once PR-17 is done
- Pack E (storage) can be developed alongside Pack D

**Testing Strategy:**
- Unit tests: Synthetic images, fast, no device needed
- Instrumentation tests: Real device/emulator, end-to-end flows
- Golden tests: Reference images with SSIM comparison
- Memory tests: Heap profiling during 48MP processing
- Leak tests: Leak Canary after repeated captures
- Stress tests: 1000 cancellations, background/foreground cycles

---

*Last updated: 2026-02-26*
*Sprint status: Ready for implementation*
*Previous sprint: Sprint 1 - Foundation*
*Next sprint: Sprint 3 - Manual Editor*
