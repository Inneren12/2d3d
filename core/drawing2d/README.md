# core:drawing2d

Pure-JVM module that provides the foundational 2D drawing primitives, math
utilities, and serialization models for the 2d3d application.

## Purpose

This module is the **mathematical and data backbone** of all 2D drawing
operations. It is intentionally free of any Android dependencies so that its
logic can be unit-tested on the JVM without a device or emulator, reused from
command-line tools or server-side processes, and validated in isolation from the
UI and framework layers.

## Architectural Constraints

| Rule | Constraint |
|---|---|
| **ARCH-MATH-001** | All internal coordinates use `Double`. `Float` is forbidden until the rendering layer. |
| **ARCH-SAFE-001** | `round()` accepts `Double` and uses `toLong()` scaling — never `roundToInt()` — to prevent integer overflow for values approaching ±2 × 10⁹. |
| **Pure JVM** | Zero `android.*` imports are permitted anywhere under `src/`. Verified by CI grep. |

## Module Structure

```
core/drawing2d/
├── build.gradle.kts          # Pure JVM Gradle configuration
├── README.md                 # This file
└── src/
    ├── main/kotlin/
    │   └── com/yourapp/drawing2d/
    │       └── math/
    │           └── MathUtils.kt   # Deterministic rounding utilities
    └── test/kotlin/
        └── com/yourapp/drawing2d/
            └── math/
                └── MathUtilsTest.kt
```

## Dependencies

| Dependency | Scope | Purpose |
|---|---|---|
| `:core:domain` | `implementation` | Shared constants (e.g. `Precision.DEFAULT_DECIMALS`) |
| `kotlinx-serialization-json` | `implementation` | JSON serialisation of drawing models |
| `junit-jupiter` | `testImplementation` | JUnit 5 test engine |
| `kotest-runner-junit5` | `testImplementation` | Kotest spec runner on JUnit 5 platform |
| `kotest-assertions-core` | `testImplementation` | Kotest fluent assertion DSL |

## Running Tests

```bash
./gradlew :core:drawing2d:test
```

## Verifying No Android Imports

```bash
grep -r "import android" core/drawing2d/src/main/
# Must return no output (exit code 1)
```
