# drawing2d Module — AI Router Index

> Hyper-concise file map for agent orientation. No logic explanations.

## core/drawing2d — main sources

| Symbol | File |
|--------|------|
| `Point2D`, `Vector2D` | `core/drawing2d/src/main/kotlin/com/yourapp/drawing2d/model/Primitives.kt` |
| `EntityV1` (sealed base) | `core/drawing2d/src/main/kotlin/com/yourapp/drawing2d/model/Entity.kt` |
| `EntityV1.Line` | `core/drawing2d/src/main/kotlin/com/yourapp/drawing2d/model/Entity.kt` |
| `EntityV1.Circle` | `core/drawing2d/src/main/kotlin/com/yourapp/drawing2d/model/Entity.kt` |
| `EntityV1.Polyline` | `core/drawing2d/src/main/kotlin/com/yourapp/drawing2d/model/Entity.kt` |
| `EntityV1.Arc` | `core/drawing2d/src/main/kotlin/com/yourapp/drawing2d/model/Entity.kt` |
| `LineStyle` | `core/drawing2d/src/main/kotlin/com/yourapp/drawing2d/model/Entity.kt` |
| `MathUtils` | `core/drawing2d/src/main/kotlin/com/yourapp/drawing2d/math/MathUtils.kt` |
| `AnnotationV1` (sealed base) | `core/drawing2d/src/main/kotlin/com/yourapp/drawing2d/model/Annotation.kt` |
| `AnnotationV1.Text` | `core/drawing2d/src/main/kotlin/com/yourapp/drawing2d/model/Annotation.kt` |
| `AnnotationV1.Dimension` | `core/drawing2d/src/main/kotlin/com/yourapp/drawing2d/model/Annotation.kt` |
| `AnnotationV1.Tag` | `core/drawing2d/src/main/kotlin/com/yourapp/drawing2d/model/Annotation.kt` |
| `AnnotationV1.Group` | `core/drawing2d/src/main/kotlin/com/yourapp/drawing2d/model/Annotation.kt` |
| `Units` (enum) | `core/drawing2d/src/main/kotlin/com/yourapp/drawing2d/model/Annotation.kt` |
| `Drawing2D` | `core/drawing2d/src/main/kotlin/com/yourapp/drawing2d/model/Drawing2D.kt` |

## core/drawing2d — test sources

| Test Class | File |
|------------|------|
| `EntityTest` | `core/drawing2d/src/test/kotlin/com/yourapp/drawing2d/model/EntityTest.kt` |
| `PrimitivesTest` | `core/drawing2d/src/test/kotlin/com/yourapp/drawing2d/model/PrimitivesTest.kt` |
| `MathUtilsTest` | `core/drawing2d/src/test/kotlin/com/yourapp/drawing2d/math/MathUtilsTest.kt` |
| `AnnotationTest` | `core/drawing2d/src/test/kotlin/com/yourapp/drawing2d/model/AnnotationTest.kt` |
| `Drawing2DTest` | `core/drawing2d/src/test/kotlin/com/yourapp/drawing2d/model/Drawing2DTest.kt` |

## Build

- Module: `core/drawing2d/build.gradle.kts`
- Plugins: `kotlin("jvm")`, `kotlin("plugin.serialization")`
- Key deps: `kotlinx-serialization-json:1.6.3`, `kotest-runner-junit5:5.8.1`
