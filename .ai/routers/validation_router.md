# Validation Router

## core/validation — main sources

| Symbol | Location |
|--------|----------|
| `Severity` (enum) | `core/validation/src/main/kotlin/com/yourapp/validation/Violation.kt` |
| `Violation` (sealed) | `core/validation/src/main/kotlin/com/yourapp/validation/Violation.kt` |
| `Violation.MissingField` | `core/validation/src/main/kotlin/com/yourapp/validation/Violation.kt` |
| `Violation.InvalidValue` | `core/validation/src/main/kotlin/com/yourapp/validation/Violation.kt` |
| `Violation.BrokenReference` | `core/validation/src/main/kotlin/com/yourapp/validation/Violation.kt` |
| `Violation.Custom` | `core/validation/src/main/kotlin/com/yourapp/validation/Violation.kt` |

| `DrawingValidator` | `core/validation/src/main/kotlin/com/yourapp/validation/DrawingValidator.kt` |
| `ValidationException` | `core/validation/src/main/kotlin/com/yourapp/validation/DrawingValidator.kt` |

## core/validation — test sources

| Test Class | Location |
|------------|----------|
| `ViolationTest` | `core/validation/src/test/kotlin/com/yourapp/validation/ViolationTest.kt` |
| `DrawingValidatorTest` | `core/validation/src/test/kotlin/com/yourapp/validation/DrawingValidatorTest.kt` |

## Build

```bash
./gradlew :core:validation:build
./gradlew :core:validation:test
./gradlew :core:validation:jacocoTestReport
```
