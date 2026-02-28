# Sprint 6: OCR Auto-Assist - Dimension Recognition & Auto-Population (FINAL SPRINT)

**Status:** Ready
**Dates:** 2026-09-10 — 2026-10-08
**Duration:** 4 weeks
**Team:** 1-2 Android developers (1 with ML/OCR experience)
**Goal:** Implement OCR-based dimension recognition from rectified blueprint images using ML Kit Text Recognition v2. Auto-populate node positions and member dimensions from detected measurements. Provide manual correction UI with confidence scores. THIS IS THE FINAL SPRINT - delivers MVP to production.

**Key Deliverables:**
- `feature-ocr` module with ML Kit Text Recognition v2
- Rectified image OCR with text detection and recognition
- Dimension pattern matching (e.g., "2500mm", "2.5m", "8'-2\"")
- Spatial clustering to associate dimensions with nearby geometry
- BitmapRegionDecoder singleton pattern (prevent I/O thrashing)
- Confidence-based filtering (reject low-confidence OCR)
- Manual correction UI (tap dimension to edit)
- Auto-population of node coordinates from dimension lines
- Member length validation (OCR vs calculated)
- Production readiness: Error handling, analytics, crash reporting
- App Store deployment preparation

**Critical Architecture Applied:**
- ARCH-PERF-002: BitmapRegionDecoder singleton for OCR batch processing
- ARCH-MATH-001: Double for coordinates, proper unit conversion
- ML Kit on-device processing (no cloud API, works offline)
- Spatial hash grid for O(1) nearest-neighbor queries

**THIS IS THE FINAL SPRINT**: After completion, app is production-ready for Google Play Store release.

---

## Pack A: OCR Module Foundation

### PR-01: Module `feature-ocr` Setup

**Context (Human):** Create OCR feature module with ML Kit dependencies. This is the last feature module before production release.

<ac-block id="S6-PR01-AC1">
**Acceptance Criteria for PR01 (Module Setup)**:
- [ ] Create module `feature-ocr` in `/feature/ocr/` directory
- [ ] Module path in settings.gradle.kts: `include(":feature:ocr")`
- [ ] Dependencies in build.gradle.kts:
  - `com.google.android.gms:play-services-mlkit-text-recognition:19.0.0` (ML Kit Text Recognition v2)
  - `com.google.mlkit:text-recognition:16.0.0` (alternative, check latest)
  - AndroidX Core, Lifecycle, ViewModel, Hilt
  - `:core:drawing2d`, `:core:converter`, `:feature:drawing-import` (for rectified images)
  - Kotlin Coroutines (for async OCR processing)
- [ ] ML Kit model downloaded on first run (auto-download or bundled)
- [ ] Module compiles: `./gradlew :feature:ocr:assembleDebug`
- [ ] Test: Empty instrumentation test runs
- [ ] File location: `/feature/ocr/build.gradle.kts`
</ac-block>

---

### PR-02: ML Kit Text Recognition Setup

**Context (Human):** Initialize ML Kit Text Recognition API. This is on-device OCR (no cloud, works offline).

<ac-block id="S6-PR02-AC1">
**Acceptance Criteria for PR02 (ML Kit Setup)**:
- [ ] Create class: `class TextRecognizer(context: Context)`
- [ ] Method: `suspend fun recognizeText(bitmap: Bitmap): Text` that:
```kotlin
  suspend fun recognizeText(bitmap: Bitmap): Text = suspendCoroutine { continuation ->
      val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
      val image = InputImage.fromBitmap(bitmap, 0)
      
      recognizer.process(image)
          .addOnSuccessListener { text ->
              continuation.resume(text)
          }
          .addOnFailureListener { exception ->
              continuation.resumeWithException(exception)
          }
  }
```
- [ ] ML Kit model: Use DEFAULT_OPTIONS (Latin script, on-device)
- [ ] Handles multiple text blocks, lines, and elements
- [ ] Returns bounding boxes for each detected text element
- [ ] Test: Recognize text from sample blueprint image
- [ ] Test: Handles empty image (no text) gracefully
- [ ] Test: Handles ML Kit initialization failure
- [ ] File location: `feature/ocr/src/main/kotlin/.../ml/TextRecognizer.kt`
</ac-block>

---

### PR-03: BitmapRegionDecoder Singleton (ARCH-PERF-002)

**Context (Human):** CRITICAL PERFORMANCE: BitmapRegionDecoder is expensive to create. Creating 100 instances for 100 text regions causes disk I/O thrashing. Use singleton pattern: create once per OCR batch.

<ac-block id="S6-PR03-AC1">
**Acceptance Criteria for PR03 (Decoder Singleton)**:
- [ ] Create class: `class OcrBitmapLoader(imageFile: File)`
- [ ] Pattern:
```kotlin
  class OcrBitmapLoader(private val imageFile: File) : AutoCloseable {
      // CRITICAL: Single decoder instance for entire batch
      private val decoder: BitmapRegionDecoder by lazy {
          BitmapRegionDecoder.newInstance(imageFile.inputStream(), false)
      }
      
      fun loadRegion(rect: Rect): Bitmap {
          val options = BitmapFactory.Options().apply {
              inPreferredConfig = Bitmap.Config.ARGB_8888
          }
          return decoder.decodeRegion(rect, options)
      }
      
      override fun close() {
          decoder.recycle()
      }
  }
```
- [ ] Usage:
```kotlin
  OcrBitmapLoader(rectifiedImage).use { loader ->
      // Process 100 text regions with SAME decoder
      for (region in textRegions) {
          val bitmap = loader.loadRegion(region.boundingBox)
          val text = recognizer.recognizeText(bitmap)
          // ...
      }
      // Decoder automatically recycled on close
  }
```
- [ ] Rationale: Prevents disk I/O thrashing (100 decoder instances → 1 decoder)
- [ ] Test: Loading 100 regions uses single decoder instance
- [ ] Test: Decoder recycled after use (no memory leak)
- [ ] Performance: 100-region OCR completes in <10 seconds (vs >60s without singleton)
- [ ] Documentation: Explain ARCH-PERF-002 rationale
- [ ] File location: `feature/ocr/src/main/kotlin/.../image/OcrBitmapLoader.kt`
</ac-block>

---

### PR-04: OCR Pipeline Orchestrator

**Context (Human):** Orchestrate the full OCR pipeline: load image, detect text blocks, recognize text, parse dimensions. This is the main entry point.

<ac-block id="S6-PR04-AC1">
**Acceptance Criteria for PR04 (Pipeline)**:
- [ ] Create class: `class OcrPipeline(textRecognizer: TextRecognizer)`
- [ ] Method: `suspend fun processImage(imageFile: File): OcrResult` that:
  1. Load image with MemoryGuard (from Sprint 2) for inSampleSize
  2. Run ML Kit text recognition on full image
  3. Extract text blocks with bounding boxes
  4. Parse dimensions from text (e.g., "2500mm", "8'-2\"")
  5. Calculate confidence scores
  6. Return structured result
- [ ] Data classes:
```kotlin
  data class OcrResult(
      val dimensions: List<DetectedDimension>,
      val textBlocks: List<TextBlock>,
      val processingTimeMs: Long
  )
  
  data class DetectedDimension(
      val text: String,
      val valueMillimeters: Double,
      val boundingBox: Rect,
      val confidence: Float,
      val unit: DimensionUnit
  )
  
  enum class DimensionUnit { MM, CM, M, INCHES, FEET }
```
- [ ] Test: Process sample blueprint, detect at least 5 dimensions
- [ ] Test: Pipeline completes in <15 seconds for typical blueprint
- [ ] Test: Handles OCR failure gracefully (returns empty result, not crash)
- [ ] File location: `feature/ocr/src/main/kotlin/.../pipeline/OcrPipeline.kt`
</ac-block>

---

## Pack B: Dimension Pattern Matching

### PR-05: DimensionParser (Regex Patterns)

**Context (Human):** Parse dimension text from OCR output. Support multiple formats: metric (mm, cm, m), imperial (inches, feet-inches), with various notations.

<ac-block id="S6-PR05-AC1">
**Acceptance Criteria for PR05 (Dimension Parser)**:
- [ ] Create class: `class DimensionParser`
- [ ] Method: `fun parse(text: String): DimensionValue?` that:
```kotlin
  fun parse(text: String): DimensionValue? {
      // Try each pattern in order
      return tryMetric(text)
          ?: tryImperial(text)
          ?: tryDecimal(text)
  }
  
  private fun tryMetric(text: String): DimensionValue? {
      // Patterns: "2500mm", "2500 mm", "250cm", "2.5m"
      val patterns = listOf(
          Regex("""(\d+\.?\d*)\s*mm""", RegexOption.IGNORE_CASE),
          Regex("""(\d+\.?\d*)\s*cm""", RegexOption.IGNORE_CASE),
          Regex("""(\d+\.?\d*)\s*m""", RegexOption.IGNORE_CASE)
      )
      
      for (pattern in patterns) {
          val match = pattern.find(text) ?: continue
          val value = match.groupValues[1].toDoubleOrNull() ?: continue
          
          val mm = when {
              text.contains("mm", ignoreCase = true) -> value
              text.contains("cm", ignoreCase = true) -> value * 10.0
              text.contains("m", ignoreCase = true) -> value * 1000.0
              else -> continue
          }
          
          return DimensionValue(mm, DimensionUnit.MM)
      }
      
      return null
  }
  
  private fun tryImperial(text: String): DimensionValue? {
      // Patterns: 8'-2", 8'2", 8 ft 2 in, 98"
      val feetInches = Regex("""(\d+)'\s*-?\s*(\d+)"?""").find(text)
      if (feetInches != null) {
          val feet = feetInches.groupValues[1].toIntOrNull() ?: return null
          val inches = feetInches.groupValues[2].toIntOrNull() ?: 0
          val totalInches = feet * 12 + inches
          val mm = totalInches * 25.4
          return DimensionValue(mm, DimensionUnit.FEET)
      }
      
      val inches = Regex("""(\d+\.?\d*)\"?""").find(text)
      if (inches != null) {
          val value = inches.groupValues[1].toDoubleOrNull() ?: return null
          val mm = value * 25.4
          return DimensionValue(mm, DimensionUnit.INCHES)
      }
      
      return null
  }
```
- [ ] Test: Parse "2500mm" → 2500.0 mm
- [ ] Test: Parse "2.5m" → 2500.0 mm
- [ ] Test: Parse "250cm" → 2500.0 mm
- [ ] Test: Parse "8'-2\"" → 2489.2 mm (8*12 + 2 = 98 inches = 2489.2mm)
- [ ] Test: Parse "98\"" → 2489.2 mm
- [ ] Test: Parse "8 ft 2 in" → 2489.2 mm
- [ ] Test: Parse invalid text → null
- [ ] Test: Handle OCR errors (e.g., "25OOmm" with letter O instead of zero)
- [ ] Code coverage >90%
- [ ] File location: `feature/ocr/src/main/kotlin/.../parsing/DimensionParser.kt`
</ac-block>

---

### PR-06: Unit Conversion (All to Millimeters)

**Context (Human):** Normalize all dimensions to millimeters (internal representation). Ensure conversion accuracy.

<ac-block id="S6-PR06-AC1">
**Acceptance Criteria for PR06 (Unit Conversion)**:
- [ ] Create object: `object UnitConverter`
- [ ] Method: `fun toMillimeters(value: Double, unit: DimensionUnit): Double` that:
```kotlin
  fun toMillimeters(value: Double, unit: DimensionUnit): Double {
      return when (unit) {
          DimensionUnit.MM -> value
          DimensionUnit.CM -> value * 10.0
          DimensionUnit.M -> value * 1000.0
          DimensionUnit.INCHES -> value * 25.4
          DimensionUnit.FEET -> value * 304.8
      }
  }
```
- [ ] Use Double precision (ARCH-MATH-001)
- [ ] Test: 1m = 1000mm
- [ ] Test: 1cm = 10mm
- [ ] Test: 1 inch = 25.4mm
- [ ] Test: 1 foot = 304.8mm
- [ ] Test: Conversion is accurate to 4 decimal places
- [ ] Code coverage 100%
- [ ] File location: `feature/ocr/src/main/kotlin/.../parsing/UnitConverter.kt`
</ac-block>

---

### PR-07: Confidence Filtering

**Context (Human):** ML Kit returns confidence scores (0.0 to 1.0) for each text element. Filter out low-confidence results to reduce false positives.

<ac-block id="S6-PR07-AC1">
**Acceptance Criteria for PR07 (Confidence Filter)**:
- [ ] Define threshold: `const val MIN_CONFIDENCE = 0.7f` (70%)
- [ ] Method: `fun filterByConfidence(dimensions: List<DetectedDimension>, threshold: Float = MIN_CONFIDENCE): List<DetectedDimension>` that:
```kotlin
  fun filterByConfidence(dimensions: List<DetectedDimension>, threshold: Float): List<DetectedDimension> {
      return dimensions.filter { it.confidence >= threshold }
  }
```
- [ ] Rationale: Low-confidence OCR often wrong (e.g., "25OO" instead of "2500")
- [ ] Threshold tunable (user setting or A/B test to find optimal value)
- [ ] Test: Confidence 0.9 passes filter (>0.7)
- [ ] Test: Confidence 0.5 rejected (<0.7)
- [ ] Test: Empty list if all below threshold
- [ ] Test: Threshold configurable (can be changed without code change)
- [ ] File location: `feature/ocr/src/main/kotlin/.../filtering/ConfidenceFilter.kt`
</ac-block>

---

## Pack C: Spatial Association

### PR-08: Spatial Hash Grid (Fast Nearest Neighbor)

**Context (Human):** Associate detected dimensions with nearby geometric entities (nodes, members). Use spatial hash grid for O(1) lookups instead of O(N²) pairwise distance.

<ac-block id="S6-PR08-AC1">
**Acceptance Criteria for PR08 (Spatial Grid)**:
- [ ] Create class: `class SpatialHashGrid<T>(cellSize: Double)`
- [ ] Methods:
```kotlin
  class SpatialHashGrid<T>(private val cellSize: Double) {
      private val grid = mutableMapOf<Pair<Int, Int>, MutableList<T>>()
      
      fun insert(x: Double, y: Double, item: T) {
          val cellX = (x / cellSize).toInt()
          val cellY = (y / cellSize).toInt()
          val key = cellX to cellY
          
          grid.getOrPut(key) { mutableListOf() }.add(item)
      }
      
      fun findNearby(x: Double, y: Double, radius: Double): List<T> {
          val results = mutableListOf<T>()
          val cellRadius = (radius / cellSize).toInt() + 1
          val centerCellX = (x / cellSize).toInt()
          val centerCellY = (y / cellSize).toInt()
          
          for (dx in -cellRadius..cellRadius) {
              for (dy in -cellRadius..cellRadius) {
                  val key = (centerCellX + dx) to (centerCellY + dy)
                  grid[key]?.let { results.addAll(it) }
              }
          }
          
          return results
      }
  }
```
- [ ] Cell size: 1000.0 (1 meter in millimeters) - tunable for blueprint density
- [ ] Test: Insert 1000 items, find nearby in <1ms
- [ ] Test: Nearby query returns only items within radius
- [ ] Test: Empty cell returns empty list
- [ ] Performance: O(1) average case (vs O(N) linear search)
- [ ] Code coverage >85%
- [ ] File location: `feature/ocr/src/main/kotlin/.../spatial/SpatialHashGrid.kt`
</ac-block>

---

### PR-09: Dimension-to-Entity Association

**Context (Human):** Match detected dimensions to nearby nodes or members. Use spatial proximity + heuristics (e.g., dimension line orientation).

<ac-block id="S6-PR09-AC1">
**Acceptance Criteria for PR09 (Association)**:
- [ ] Create class: `class DimensionAssociator`
- [ ] Method: `fun associate(dimensions: List<DetectedDimension>, nodes: List<Node2D>, members: List<Member2D>): List<Association>` that:
```kotlin
  data class Association(
      val dimension: DetectedDimension,
      val targetType: TargetType,
      val targetId: String,
      val distance: Double
  )
  
  enum class TargetType { NODE, MEMBER, UNKNOWN }
  
  fun associate(dimensions: List<DetectedDimension>, nodes: List<Node2D>, members: List<Member2D>): List<Association> {
      // Build spatial hash grid for fast lookup
      val nodeGrid = SpatialHashGrid<Node2D>(cellSize = 1000.0)
      nodes.forEach { nodeGrid.insert(it.x, it.y, it) }
      
      val memberGrid = SpatialHashGrid<Member2D>(cellSize = 1000.0)
      members.forEach { member ->
          val startNode = nodes.find { it.id == member.startNodeId }
          val endNode = nodes.find { it.id == member.endNodeId }
          if (startNode != null && endNode != null) {
              val midX = (startNode.x + endNode.x) / 2.0
              val midY = (startNode.y + endNode.y) / 2.0
              memberGrid.insert(midX, midY, member)
          }
      }
      
      val associations = mutableListOf<Association>()
      
      for (dimension in dimensions) {
          val centerX = dimension.boundingBox.centerX().toDouble()
          val centerY = dimension.boundingBox.centerY().toDouble()
          
          // Find nearby nodes (within 500mm)
          val nearbyNodes = nodeGrid.findNearby(centerX, centerY, radius = 500.0)
          val closestNode = nearbyNodes.minByOrNull { node ->
              euclideanDistance(centerX, centerY, node.x, node.y)
          }
          
          // Find nearby members
          val nearbyMembers = memberGrid.findNearby(centerX, centerY, radius = 1000.0)
          val closestMember = nearbyMembers.minByOrNull { member ->
              val startNode = nodes.find { it.id == member.startNodeId }!!
              val endNode = nodes.find { it.id == member.endNodeId }!!
              val midX = (startNode.x + endNode.x) / 2.0
              val midY = (startNode.y + endNode.y) / 2.0
              euclideanDistance(centerX, centerY, midX, midY)
          }
          
          // Choose closest target
          val nodeDistance = closestNode?.let { euclideanDistance(centerX, centerY, it.x, it.y) } ?: Double.MAX_VALUE
          val memberDistance = closestMember?.let { member ->
              val startNode = nodes.find { it.id == member.startNodeId }!!
              val endNode = nodes.find { it.id == member.endNodeId }!!
              val midX = (startNode.x + endNode.x) / 2.0
              val midY = (startNode.y + endNode.y) / 2.0
              euclideanDistance(centerX, centerY, midX, midY)
          } ?: Double.MAX_VALUE
          
          when {
              nodeDistance < memberDistance && nodeDistance < 500.0 ->
                  associations.add(Association(dimension, TargetType.NODE, closestNode!!.id, nodeDistance))
              memberDistance < nodeDistance && memberDistance < 1000.0 ->
                  associations.add(Association(dimension, TargetType.MEMBER, closestMember!!.id, memberDistance))
              else ->
                  associations.add(Association(dimension, TargetType.UNKNOWN, "", Double.MAX_VALUE))
          }
      }
      
      return associations
  }
```
- [ ] Test: Dimension near node (100mm away) associates with node
- [ ] Test: Dimension near member (500mm away) associates with member
- [ ] Test: Dimension far from all entities (2000mm) → UNKNOWN
- [ ] Test: Multiple dimensions associate with correct targets
- [ ] Performance: 100 dimensions, 500 nodes, 1000 members → association in <500ms
- [ ] Code coverage >85%
- [ ] File location: `feature/ocr/src/main/kotlin/.../association/DimensionAssociator.kt`
</ac-block>

---

## Pack D: Auto-Population

### PR-10: Node Coordinate Auto-Population

**Context (Human):** Use OCR dimensions to auto-populate node coordinates. For example, if dimension says "2500mm" and it's associated with a member, we know the member length.

<ac-block id="S6-PR10-AC1">
**Acceptance Criteria for PR10 (Node Auto-Population)**:
- [ ] Create class: `class NodeAutoPopulator`
- [ ] Method: `fun populateNodeCoordinates(associations: List<Association>, drawing: Drawing2D): Drawing2D` that:
```kotlin
  fun populateNodeCoordinates(associations: List<Association>, drawing: Drawing2D): Drawing2D {
      val updatedNodes = drawing.nodes.toMutableList()
      
      for (association in associations) {
          if (association.targetType == TargetType.MEMBER) {
              val member = drawing.members.find { it.id == association.targetId } ?: continue
              val startNode = updatedNodes.find { it.id == member.startNodeId } ?: continue
              val endNode = updatedNodes.find { it.id == member.endNodeId } ?: continue
              
              // Calculate expected distance from dimension
              val expectedLength = association.dimension.valueMillimeters
              
              // Calculate actual distance
              val actualLength = euclideanDistance(startNode.x, startNode.y, endNode.x, endNode.y)
              
              // If actual length close to expected, no adjustment needed
              if (abs(actualLength - expectedLength) < 10.0) continue // 10mm tolerance
              
              // Otherwise, adjust end node position to match expected length
              val direction = normalize(Vec2(endNode.x - startNode.x, endNode.y - startNode.y))
              val newEndPos = Point2D(
                  startNode.x + direction.x * expectedLength,
                  startNode.y + direction.y * expectedLength
              )
              
              // Update end node
              val nodeIndex = updatedNodes.indexOfFirst { it.id == endNode.id }
              if (nodeIndex >= 0) {
                  updatedNodes[nodeIndex] = endNode.copy(x = newEndPos.x, y = newEndPos.y)
              }
          }
      }
      
      return drawing.copy(nodes = updatedNodes)
  }
```
- [ ] Only adjust if discrepancy >10mm (avoid tiny adjustments from OCR noise)
- [ ] Preserve start node, adjust end node (consistent direction)
- [ ] Test: Member with OCR dimension 2500mm, actual length 2450mm → end node adjusted
- [ ] Test: Member with OCR dimension 2500mm, actual length 2505mm → no adjustment (within tolerance)
- [ ] Test: Multiple members adjusted correctly
- [ ] Code coverage >85%
- [ ] File location: `feature/ocr/src/main/kotlin/.../population/NodeAutoPopulator.kt`
</ac-block>

---

### PR-11: Member Length Validation

**Context (Human):** Compare OCR-detected dimensions with calculated member lengths. Flag discrepancies for user review.

<ac-block id="S6-PR11-AC1">
**Acceptance Criteria for PR11 (Length Validation)**:
- [ ] Create class: `class MemberLengthValidator`
- [ ] Method: `fun validate(associations: List<Association>, drawing: Drawing2D): List<ValidationIssue>` that:
```kotlin
  data class ValidationIssue(
      val memberId: String,
      val ocrLength: Double,
      val calculatedLength: Double,
      val discrepancy: Double,
      val severity: Severity
  )
  
  enum class Severity { INFO, WARNING, ERROR }
  
  fun validate(associations: List<Association>, drawing: Drawing2D): List<ValidationIssue> {
      val issues = mutableListOf<ValidationIssue>()
      
      for (association in associations) {
          if (association.targetType != TargetType.MEMBER) continue
          
          val member = drawing.members.find { it.id == association.targetId } ?: continue
          val startNode = drawing.nodes.find { it.id == member.startNodeId } ?: continue
          val endNode = drawing.nodes.find { it.id == member.endNodeId } ?: continue
          
          val calculatedLength = euclideanDistance(startNode.x, startNode.y, endNode.x, endNode.y)
          val ocrLength = association.dimension.valueMillimeters
          val discrepancy = abs(calculatedLength - ocrLength)
          
          val severity = when {
              discrepancy < 10.0 -> Severity.INFO // <10mm: Acceptable
              discrepancy < 100.0 -> Severity.WARNING // 10-100mm: Review recommended
              else -> Severity.ERROR // >100mm: Likely error
          }
          
          if (discrepancy >= 10.0) { // Only report significant discrepancies
              issues.add(ValidationIssue(member.id, ocrLength, calculatedLength, discrepancy, severity))
          }
      }
      
      return issues
  }
```
- [ ] Test: Discrepancy 5mm → INFO (not reported)
- [ ] Test: Discrepancy 50mm → WARNING
- [ ] Test: Discrepancy 500mm → ERROR
- [ ] Test: Multiple members validated correctly
- [ ] Code coverage >90%
- [ ] File location: `feature/ocr/src/main/kotlin/.../validation/MemberLengthValidator.kt`
</ac-block>

---

## Pack E: Manual Correction UI

### PR-12: OCR Review Screen

**Context (Human):** Show detected dimensions with confidence scores. User can tap to edit incorrect values.

<ac-block id="S6-PR12-AC1">
**Acceptance Criteria for PR12 (Review UI)**:
- [ ] Create `OcrReviewFragment` with RecyclerView showing:
  - Detected dimension text
  - Parsed value (millimeters)
  - Confidence score (color-coded: green >0.8, yellow 0.6-0.8, red <0.6)
  - Associated entity (node/member ID)
  - Distance to entity
  - Edit button
- [ ] Layout for each item:
```xml
  <LinearLayout>
      <TextView android:id="@+id/dimensionText" text="2500mm" />
      <TextView android:id="@+id/parsedValue" text="2500.0mm" />
      <TextView android:id="@+id/confidence" text="Confidence: 92%" color="green" />
      <TextView android:id="@+id/association" text="Member: member-123" />
      <Button android:id="@+id/editButton" text="Edit" />
  </LinearLayout>
```
- [ ] Tap "Edit" opens dialog:
  - Text input for corrected value
  - Unit selector (mm, cm, m, inches, feet)
  - Save/Cancel buttons
- [ ] Test: List displays all detected dimensions
- [ ] Test: Confidence color-coded correctly
- [ ] Test: Edit dialog opens on tap
- [ ] Test: Edited value saved and reflected in list
- [ ] UI test: Espresso test for edit flow
- [ ] File location: `feature/ocr/src/main/kotlin/.../ui/OcrReviewFragment.kt`
</ac-block>

---

### PR-13: Dimension Overlay on Image

**Context (Human):** Show detected dimensions overlaid on rectified blueprint image. Helps user understand what OCR detected.

<ac-block id="S6-PR13-AC1">
**Acceptance Criteria for PR13 (Image Overlay)**:
- [ ] Create custom view: `class DimensionOverlayView : View`
- [ ] Renders:
  - Background: Rectified blueprint image
  - Overlay: Bounding boxes for each detected dimension
  - Labels: Dimension text next to bounding boxes
  - Color-coding: Green (high confidence), Yellow (medium), Red (low)
- [ ] Implementation:
```kotlin
  override fun onDraw(canvas: Canvas) {
      super.onDraw(canvas)
      
      // Draw blueprint image
      blueprintBitmap?.let { canvas.drawBitmap(it, 0f, 0f, null) }
      
      // Draw dimension overlays
      for (dimension in dimensions) {
          val paint = Paint().apply {
              color = when {
                  dimension.confidence > 0.8 -> Color.GREEN
                  dimension.confidence > 0.6 -> Color.YELLOW
                  else -> Color.RED
              }
              style = Paint.Style.STROKE
              strokeWidth = 3f
          }
          
          canvas.drawRect(dimension.boundingBox, paint)
          
          // Draw text label
          val textPaint = Paint().apply {
              color = Color.WHITE
              textSize = 40f
              style = Paint.Style.FILL
          }
          canvas.drawText(
              "${dimension.text} (${(dimension.confidence * 100).toInt()}%)",
              dimension.boundingBox.left.toFloat(),
              dimension.boundingBox.top.toFloat() - 10,
              textPaint
          )
      }
  }
```
- [ ] Test: Overlay renders correctly
- [ ] Test: Bounding boxes align with text in image
- [ ] Test: Color-coding visible
- [ ] Performance: Rendering 100 dimensions at 60 FPS
- [ ] File location: `feature/ocr/src/main/kotlin/.../ui/DimensionOverlayView.kt`
</ac-block>

---

### PR-14: Batch Accept/Reject

**Context (Human):** Allow user to quickly accept all high-confidence dimensions or reject all low-confidence ones.

<ac-block id="S6-PR14-AC1">
**Acceptance Criteria for PR14 (Batch Actions)**:
- [ ] Add toolbar buttons:
  - "Accept All High" (confidence >0.8)
  - "Reject All Low" (confidence <0.6)
  - "Accept All" (all dimensions)
  - "Reject All" (clear all)
- [ ] Actions:
```kotlin
  fun acceptHighConfidence() {
      val accepted = dimensions.filter { it.confidence > 0.8 }
      applyDimensions(accepted)
      navigateToEditor()
  }
  
  fun rejectLowConfidence() {
      dimensions = dimensions.filter { it.confidence >= 0.6 }.toMutableList()
      notifyDataSetChanged()
  }
```
- [ ] Show count before action: "Accept 15 high-confidence dimensions?"
- [ ] Undo support (optional enhancement)
- [ ] Test: Accept high confidence filters correctly
- [ ] Test: Reject low confidence removes items
- [ ] Test: Accept all proceeds to editor
- [ ] File location: Update `OcrReviewFragment.kt`
</ac-block>

---

## Pack F: Integration with Editor

### PR-15: Apply OCR Dimensions to Drawing

**Context (Human):** Take accepted OCR dimensions and apply them to the drawing in editor. This is the final step of OCR pipeline.

<ac-block id="S6-PR15-AC1">
**Acceptance Criteria for PR15 (Apply to Drawing)**:
- [ ] Create class: `class OcrApplier`
- [ ] Method: `fun applyToDrawing(dimensions: List<DetectedDimension>, associations: List<Association>, drawing: Drawing2D): Drawing2D` that:
  1. Auto-populate node coordinates (PR-10)
  2. Validate member lengths (PR-11)
  3. Store OCR metadata in drawing (for audit trail)
  4. Return updated drawing
- [ ] OCR metadata:
```kotlin
  data class OcrMetadata(
      val appliedDimensions: List<DetectedDimension>,
      val validationIssues: List<ValidationIssue>,
      val timestamp: Long,
      val ocrVersion: String
  )
```
- [ ] Store metadata in `drawing.metadata["ocrMetadata"]` as JSON
- [ ] Test: Applied dimensions update node coordinates
- [ ] Test: Metadata stored correctly
- [ ] Test: Drawing can be saved/loaded with metadata
- [ ] File location: `feature/ocr/src/main/kotlin/.../application/OcrApplier.kt`
</ac-block>

---

### PR-16: OCR Button in Drawing Import Flow

**Context (Human):** Add "Auto-Detect Dimensions" button after rectification (Sprint 2). User can choose to use OCR or skip it.

<ac-block id="S6-PR16-AC1">
**Acceptance Criteria for PR16 (UI Integration)**:
- [ ] In `CaptureResultFragment` (from Sprint 2), after rectification:
  - Show button: "Auto-Detect Dimensions (OCR)"
  - Show button: "Skip, Add Manually"
- [ ] Button actions:
  - "Auto-Detect" → Navigate to `OcrReviewFragment`
  - "Skip" → Navigate directly to `EditorFragment`
- [ ] Show loading indicator during OCR processing (can take 10-15 seconds)
- [ ] Handle OCR failure: Show error message, offer "Try Again" or "Skip"
- [ ] Test: Button appears after rectification
- [ ] Test: OCR button starts OCR pipeline
- [ ] Test: Skip button bypasses OCR
- [ ] Test: Loading indicator shows during processing
- [ ] UI test: Navigation flow works correctly
- [ ] File location: Update `feature/drawing-import/.../CaptureResultFragment.kt`
</ac-block>

---

## Pack G: Production Readiness

### PR-17: Error Handling & User Feedback

**Context (Human):** PRODUCTION REQUIREMENT: Robust error handling for all failure modes. User should never see a crash or generic error.

<ac-block id="S6-PR17-AC1">
**Acceptance Criteria for PR17 (Error Handling)**:
- [ ] Handle all error cases:
  - ML Kit initialization failure: "OCR not available on this device"
  - Image loading failure: "Could not load image, try again"
  - OCR processing failure: "OCR failed, please try manual entry"
  - Low memory (OOM): "Image too large, reducing quality"
  - No dimensions detected: "No dimensions found, try manual entry"
  - Parse failure: "Could not understand dimension format"
- [ ] All errors show user-friendly messages (not technical jargon)
- [ ] All errors offer recovery actions:
  - "Try Again" button
  - "Skip OCR" button
  - "Report Problem" button (links to support)
- [ ] Log errors to analytics (Firebase Crashlytics or similar):
```kotlin
  try {
      val result = ocrPipeline.processImage(image)
  } catch (e: Exception) {
      Crashlytics.log("OCR failed: ${e.message}")
      Crashlytics.recordException(e)
      showErrorDialog("OCR failed. You can still add dimensions manually.")
  }
```
- [ ] Test: Each error case triggers correct UI flow
- [ ] Test: Error messages are clear and actionable
- [ ] Test: No crashes in error paths
- [ ] File location: Update all OCR classes with proper error handling
</ac-block>

---

### PR-18: Analytics & Telemetry

**Context (Human):** PRODUCTION REQUIREMENT: Track OCR usage and success rates to improve the feature over time.

<ac-block id="S6-PR18-AC1">
**Acceptance Criteria for PR18 (Analytics)**:
- [ ] Track events (Firebase Analytics or similar):
  - `ocr_started`: User tapped "Auto-Detect Dimensions"
  - `ocr_completed`: OCR processing finished (duration, dimension count)
  - `ocr_failed`: OCR processing failed (error type)
  - `ocr_dimension_detected`: Dimension detected (confidence, unit)
  - `ocr_dimension_accepted`: User accepted dimension
  - `ocr_dimension_rejected`: User rejected dimension
  - `ocr_dimension_edited`: User edited dimension (old value, new value)
  - `ocr_review_completed`: User finished review (accept count, reject count, edit count)
- [ ] Track metrics:
  - OCR success rate (dimensions detected / blueprints processed)
  - Average confidence score
  - User acceptance rate (accepted / detected)
  - User edit rate (edited / accepted)
  - Processing time (percentiles: p50, p95, p99)
- [ ] Analytics payload:
```kotlin
  analytics.logEvent("ocr_completed", bundleOf(
      "duration_ms" to durationMs,
      "dimension_count" to dimensions.size,
      "high_confidence_count" to dimensions.count { it.confidence > 0.8 },
      "medium_confidence_count" to dimensions.count { it.confidence in 0.6..0.8 },
      "low_confidence_count" to dimensions.count { it.confidence < 0.6 }
  ))
```
- [ ] Privacy: No PII (personally identifiable information) in analytics
- [ ] Test: Events logged correctly
- [ ] Test: Metrics calculated correctly
- [ ] Documentation: Analytics schema documented
- [ ] File location: `feature/ocr/src/main/kotlin/.../analytics/OcrAnalytics.kt`
</ac-block>

---

### PR-19: Performance Optimization

**Context (Human):** PRODUCTION REQUIREMENT: OCR must complete in reasonable time (<20 seconds). Optimize for production performance.

<ac-block id="S6-PR19-AC1">
**Acceptance Criteria for PR19 (Performance)**:
- [ ] Optimizations:
  1. BitmapRegionDecoder singleton (already in PR-03)
  2. Parallel text recognition (process multiple regions concurrently)
  3. Image downsampling (reduce resolution if >4000px)
  4. Early termination (stop if no text detected in first 10 regions)
  5. Dimension parsing caching (cache regex matches)
- [ ] Parallel processing:
```kotlin
  suspend fun processImageParallel(imageFile: File): OcrResult = coroutineScope {
      val textBlocks = detectTextBlocks(imageFile)
      
      // Process blocks in parallel (max 4 concurrent)
      val dimensions = textBlocks
          .chunked(4)
          .flatMap { chunk ->
              chunk.map { block ->
                  async(Dispatchers.Default) {
                      recognizeAndParse(block)
                  }
              }.awaitAll()
          }
      
      OcrResult(dimensions, ...)
  }
```
- [ ] Benchmarks (on mid-range device):
  - Typical blueprint (10 dimensions): <10 seconds
  - Complex blueprint (50 dimensions): <20 seconds
  - Simple blueprint (3 dimensions): <5 seconds
- [ ] Test: Parallel processing faster than sequential
- [ ] Test: Performance meets targets on test devices
- [ ] Performance profiling: Identify bottlenecks if too slow
- [ ] File location: Update `OcrPipeline.kt` with optimizations
</ac-block>

---

### PR-20: Offline Support

**Context (Human):** PRODUCTION REQUIREMENT: OCR must work offline (ML Kit is on-device, already offline-capable).

<ac-block id="S6-PR20-AC1">
**Acceptance Criteria for PR20 (Offline)**:
- [ ] Verify ML Kit model bundled with app (not downloaded at runtime):
```kotlin
  dependencies {
      // Bundled model (works offline)
      implementation 'com.google.mlkit:text-recognition:16.0.0'
      
      // NOT this (requires download):
      // implementation 'com.google.android.gms:play-services-mlkit-text-recognition:19.0.0'
  }
```
- [ ] Test: OCR works in airplane mode (no network)
- [ ] Test: OCR works on first launch (no prior model download)
- [ ] APK size: Check impact (<50MB increase acceptable for bundled model)
- [ ] Documentation: Explain offline capability in user-facing docs
- [ ] File location: Update `build.gradle.kts`, verify model bundled
</ac-block>

---

## Pack H: Final Testing & Deployment

### PR-21: End-to-End Testing

**Context (Human):** FINAL VALIDATION: Complete end-to-end test of entire app flow.

<ac-block id="S6-PR21-AC1">
**Acceptance Criteria for PR21 (E2E Tests)**:
- [ ] E2E test scenario:
  1. Capture blueprint photo (Sprint 2)
  2. Rectify image (Sprint 2)
  3. Run OCR auto-detect (Sprint 6)
  4. Review and accept dimensions (Sprint 6)
  5. Open editor (Sprint 3)
  6. Add nodes and members (Sprint 3)
  7. Convert to 3D model (Sprint 4)
  8. Sync to backend (Sprint 4.5)
  9. View in AR (Sprint 5)
- [ ] Test on multiple devices:
  - High-end (Pixel 8, Samsung S24)
  - Mid-range (Pixel 6a, Samsung A54)
  - Low-end (budget device with 2GB RAM)
- [ ] Test on different Android versions:
  - Android 14 (latest)
  - Android 12 (common)
  - Android 10 (minimum supported)
- [ ] Performance targets:
  - End-to-end flow completes in <5 minutes
  - No ANRs (Application Not Responding)
  - No crashes
  - Memory usage <512MB peak
- [ ] Test: Complete flow succeeds on all devices
- [ ] Test: Performance meets targets
- [ ] Test: No memory leaks (Leak Canary clean)
- [ ] File location: `app/src/androidTest/kotlin/.../E2EFlowTest.kt`
</ac-block>

---

### PR-22: App Store Assets

**Context (Human):** PRODUCTION REQUIREMENT: Prepare all assets for Google Play Store listing.

<ac-block id="S6-PR22-AC1">
**Acceptance Criteria for PR22 (Store Assets)**:
- [ ] Screenshots (required sizes):
  - Phone: 1080x1920 (at least 2, max 8)
  - 7-inch tablet: 1920x1200 (at least 2)
  - 10-inch tablet: 2560x1600 (at least 2)
  - Feature graphic: 1024x500
- [ ] Screenshots show:
  - Camera capture screen
  - Rectified blueprint
  - OCR review screen (highlight feature)
  - Manual editor
  - AR visualization
- [ ] App icon:
  - Adaptive icon (foreground + background layers)
  - Size: 512x512
  - Format: PNG with transparency
  - Guidelines: Material Design 3
- [ ] Promotional video (optional but recommended):
  - Duration: 30-60 seconds
  - Show complete workflow
  - Professional voiceover
- [ ] Store listing copy:
  - Title: "Drawing Blueprint - AR 3D Viewer" (max 50 chars)
  - Short description: Compelling 80-char pitch
  - Long description: Features, benefits, use cases (max 4000 chars)
  - Keywords: blueprint, structural, AR, 3D, construction, architecture
- [ ] Privacy policy URL (required)
- [ ] Support email (required)
- [ ] Content rating questionnaire completed
- [ ] Test: Screenshots render correctly in store listing preview
- [ ] Test: App icon looks good at all sizes
- [ ] File location: `/app/store-assets/`
</ac-block>

---

### PR-23: Release Build Configuration

**Context (Human):** PRODUCTION REQUIREMENT: Configure release build with proper signing, obfuscation, optimization.

<ac-block id="S6-PR23-AC1">
**Acceptance Criteria for PR23 (Release Build)**:
- [ ] Configure release build in `build.gradle.kts`:
```kotlin
  android {
      buildTypes {
          release {
              isMinifyEnabled = true
              isShrinkResources = true
              proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
              
              signingConfig = signingConfigs.getByName("release")
              
              // Disable logging in release
              buildConfigField("boolean", "DEBUG_LOGGING", "false")
          }
      }
      
      signingConfigs {
          create("release") {
              // Use environment variables for security
              storeFile = file(System.getenv("KEYSTORE_PATH") ?: "release.keystore")
              storePassword = System.getenv("KEYSTORE_PASSWORD")
              keyAlias = System.getenv("KEY_ALIAS")
              keyPassword = System.getenv("KEY_PASSWORD")
          }
      }
  }
```
- [ ] Proguard rules:
```proguard
  # Keep Filament native methods
  -keep class com.google.android.filament.** { *; }
  
  # Keep ML Kit models
  -keep class com.google.mlkit.** { *; }
  
  # Keep data classes for JSON serialization
  -keep class com.yourapp.*.model.** { *; }
  
  # Keep Hilt generated classes
  -keep class dagger.hilt.** { *; }
```
- [ ] Version management:
  - versionCode: Integer (e.g., 1)
  - versionName: Semantic versioning (e.g., "1.0.0")
- [ ] Create keystore (development + production):
```bash
  keytool -genkey -v -keystore release.keystore -alias release -keyalg RSA -keysize 2048 -validity 10000
```
- [ ] Test: Release build compiles successfully
- [ ] Test: Release APK size <100MB
- [ ] Test: Proguard doesn't break functionality
- [ ] Test: Release build installs and runs correctly
- [ ] File location: `/app/build.gradle.kts`, `/app/proguard-rules.pro`
</ac-block>

---

### PR-24: Sprint 6 Closeout & Production Launch

**Context (Human):** FINAL PR: Complete all remaining tasks for production launch.

<ac-block id="S6-PR24-AC1">
**Acceptance Criteria for PR24 (Production Launch)**:
- [ ] All 24 PRs merged to main branch
- [ ] Code coverage: Overall app >75%
- [ ] Ktlint check passes: `./gradlew ktlintCheck` with 0 errors
- [ ] All unit tests pass: `./gradlew test`
- [ ] All instrumentation tests pass: `./gradlew connectedAndroidTest`
- [ ] E2E test passes on 3+ device types
- [ ] Manual QA complete:
  - Complete user flow 10 times (no issues)
  - Tested on 5+ different blueprints
  - OCR accuracy >80% on test blueprints
  - AR rendering works on 3+ AR-compatible devices
  - Sync works correctly (offline → online → sync)
- [ ] Performance validated:
  - App cold start <3 seconds
  - OCR completes <20 seconds
  - Editor 60 FPS with 1000 entities
  - AR 60 FPS with 100-member models
- [ ] Security validated:
  - No hardcoded credentials
  - All API calls use HTTPS
  - User data encrypted at rest (if storing sensitive data)
  - Permissions justified and minimal
- [ ] Accessibility validated:
  - TalkBack support (screen reader)
  - Minimum touch target size (48dp)
  - Color contrast ratios meet WCAG AA
  - Text scalable (supports large fonts)
- [ ] Crashlytics configured (crash reporting)
- [ ] Analytics configured (usage tracking)
- [ ] App Store listing complete (PR-22)
- [ ] Release build signed (PR-23)
- [ ] Internal testing (closed alpha) completed:
  - Distributed to 10+ internal testers
  - Collected feedback
  - Fixed critical bugs
- [ ] Open beta completed:
  - Distributed to 100+ beta testers via Google Play
  - No critical bugs reported
  - User feedback positive
- [ ] Legal review:
  - Privacy policy approved
  - Terms of service approved
  - Open source licenses documented
- [ ] Documentation complete:
  - User manual (in-app help)
  - Developer documentation (README, architecture docs)
  - API documentation (if relevant)
- [ ] Production deployment:
  - Upload signed APK to Google Play Console
  - Configure staged rollout (10% → 50% → 100%)
  - Monitor crash reports during rollout
  - No critical bugs in first 24 hours
- [ ] Post-launch monitoring:
  - Crash-free rate >99.5%
  - Average rating >4.0 stars
  - User retention >50% (Day 7)
- [ ] Update root README.md with:
  - Sprint 6 deliverables
  - Production launch date
  - Google Play Store link
  - User documentation link
- [ ] Git tag created: `git tag v1.0.0` (production release)
- [ ] Celebrate! 🎉 App is live!
</ac-block>

---

## Sprint 6 Success Metrics

**Definition of Done (Sprint Level + Production Launch):**
- ✅ All 24 PRs completed and merged
- ✅ OCR module functional with ML Kit
- ✅ Dimension pattern matching working
- ✅ Spatial association algorithm implemented
- ✅ Auto-population of node coordinates working
- ✅ Manual correction UI functional
- ✅ Integration with editor complete
- ✅ Error handling robust (no crashes)
- ✅ Analytics tracking implemented
- ✅ Performance optimized (<20s OCR)
- ✅ Offline support validated
- ✅ E2E tests passing
- ✅ Store assets prepared
- ✅ Release build configured
- ✅ **APP LAUNCHED TO GOOGLE PLAY STORE**

**Key Deliverables:**
1. `feature-ocr` module with ML Kit integration
2. Text recognition with ML Kit Text Recognition v2
3. BitmapRegionDecoder singleton (ARCH-PERF-002)
4. Dimension parser (metric + imperial)
5. Unit conversion to millimeters
6. Confidence filtering (70% threshold)
7. Spatial hash grid for fast association
8. Dimension-to-entity association
9. Node coordinate auto-population
10. Member length validation
11. OCR review UI with manual correction
12. Dimension overlay on image
13. Integration with drawing import flow
14. Error handling & user feedback
15. Analytics & telemetry
16. Performance optimization
17. Offline support
18. E2E testing
19. App Store assets
20. Release build configuration
21. **PRODUCTION LAUNCH**

**Technical Debt:**
- OCR accuracy depends on blueprint quality (80% typical, can be lower for poor scans)
- Imperial unit parsing limited (basic feet-inches, no complex fractions)
- No support for angled dimension lines (only horizontal/vertical)
- Spatial association heuristic-based (not ML model)
- No support for stacked/overlapping dimensions

**Architectural Decisions Applied:**
- ARCH-PERF-002: BitmapRegionDecoder singleton prevents I/O thrashing
- ARCH-MATH-001: Double for coordinates, proper unit conversion
- ML Kit on-device (offline capable, no cloud API)
- Spatial hash grid for O(1) nearest-neighbor queries
- Confidence filtering to reduce false positives
- Batch processing with parallel coroutines
- Robust error handling (no crashes in production)
- Comprehensive analytics for continuous improvement

---

## Final Notes for Production

**Congratulations!** Sprint 6 is the FINAL sprint. After completion, the app will be:
- ✅ Feature-complete (MVP delivered)
- ✅ Production-ready (quality gates passed)
- ✅ Published to Google Play Store (users can download)

**Post-Launch Plan:**
1. **Week 1**: Monitor crash reports, fix critical bugs
2. **Week 2-4**: Collect user feedback, prioritize improvements
3. **Month 2**: Plan next features based on user requests
4. **Ongoing**: Maintain and improve based on analytics

**Success Criteria for Launch:**
- Crash-free rate >99.5%
- Average rating >4.0 stars (Google Play)
- User retention >50% (Day 7)
- OCR usage rate >60% (users try OCR feature)
- OCR acceptance rate >70% (users accept OCR results)

**Critical Launch Checklist:**
- [ ] All sprints completed (1-6)
- [ ] E2E testing passed
- [ ] Beta testing completed (no critical bugs)
- [ ] Store listing approved
- [ ] Release build signed and uploaded
- [ ] Staged rollout configured (10% → 50% → 100%)
- [ ] Crash reporting active (Crashlytics)
- [ ] Analytics active (Firebase Analytics)
- [ ] Support email monitored
- [ ] Social media announcement ready
- [ ] Press release (if applicable)
- [ ] Team celebrates! 🎉

**What's Next After Launch:**
Based on user feedback and analytics, prioritize:
- **P0**: Fix critical bugs reported in first week
- **P1**: Improve OCR accuracy (fine-tune confidence threshold, add more patterns)
- **P2**: Add missing features from user requests
- **P3**: Optimize performance further
- **P4**: Expand to new use cases (electrical, plumbing, etc.)

---

*Last updated: 2026-02-27*
*Sprint status: Ready for implementation - FINAL SPRINT*
*Previous sprint: Sprint 5 - AR Rendering*
*This is the FINAL sprint - app launches to production after completion*
