# core-drawing2d: MathUtils — Deterministic Rounding

> **NotebookLM RAG target.** Do not read this file during PR tasks.

---

## Purpose

`MathUtils` provides byte-for-byte deterministic rounding for the drawing2d module.
Determinism is required so that golden-test snapshots (serialised coordinate values)
match identically across JVM versions, OS platforms (x86 / ARM), and repeated test runs.

---

## Why Double, not Float (ARCH-MATH-001)

| | Float (32-bit) | Double (64-bit) |
|---|---|---|
| Precision | ~7 significant digits | ~15 significant digits |
| Max safe integer | 16,777,216 | 9,007,199,254,740,992 |
| Drawing at 250 m scale | coordinate 250,000 mm — **precision loss** | safe to 4 decimal places |

Rule: `Float` is forbidden in `core/drawing2d/**/*.kt`.
Exception: rendering / canvas layers may receive `Float` for GPU APIs.

---

## Why `toLong()` not `roundToInt()` (ARCH-SAFE-001)

`kotlin.math.roundToInt()` returns `Int` (32-bit, max ≈ 2.1 × 10⁹).
A drawing coordinate of 2,500,000 mm (2.5 km) multiplied by factor 10,000 = 2.5 × 10¹⁰,
which silently overflows `Int`, corrupting all rounded values without throwing.

`toLong()` uses a 64-bit integer (max ≈ 9.2 × 10¹⁸), safe for all coordinate values
permitted by the `abs(value) < 1e9` guard.

---

## Mathematical Rounding Explained (half-up rule)

**Half-up** means: when the dropped digit is exactly 5, round away from zero.

| Value | Scaled (×10⁴) | + sign | toLong() | Result |
|-------|--------------|--------|----------|--------|
| 1.123456789 | 11234.56789 | +0.5 → 11235.06789 | 11235 | **1.1235** |
| 1.123449999 | 11234.49999 | +0.5 → 11234.99999 | 11234 | **1.1234** |
| 5.5 | 5.5 | +0.5 → 6.0 | 6 | **6.0** |
| -1.5 | -1.5 | -0.5 → -2.0 | -2 | **-2.0** |
| -1.123456789 | -11234.56789 | -0.5 → -11235.06789 | -11235 | **-1.1235** |

Formula:
```kotlin
val scaled = value * factor          // factor = 10^decimals
val sign   = if (scaled >= 0) 0.5 else -0.5
val result = (scaled + sign).toLong().toDouble() / factor
```

---

## Fast Path + BigDecimal Fallback

```kotlin
return try {
    // Fast O(1) path — handles >99.99% of cases
    val sign = if (scaled >= 0.0) 0.5 else -0.5
    (scaled + sign).toLong().toDouble() / factor
} catch (_: Exception) {
    // Fallback for any floating-point edge case
    BigDecimal(value).setScale(decimals, RoundingMode.HALF_UP).toDouble()
}
```

The fast path is used for all values within the `abs(value) < 1e9` guard.
`BigDecimal` provides a mathematically exact fallback should the fast path encounter
any platform-specific floating-point anomaly.

---

## `tenPow()` Lookup Table Rationale

```kotlin
private fun tenPow(n: Int): Double = when (n) {
    0  -> 1.0
    1  -> 10.0
    // ...
    10 -> 10_000_000_000.0
    else -> Math.pow(10.0, n.toDouble())
}
```

`Math.pow()` involves transcendental math and can return slightly imprecise values
(e.g., `pow(10, 4)` may return `9999.999...` in edge cases on some JVMs).
The lookup table returns **exact** `Double` literals, ensuring consistent factor values
across all platforms.

---

## Edge Cases and Limits

| Condition | Behaviour |
|-----------|-----------|
| `abs(value) >= 1e9` | `IllegalArgumentException("Value too large for safe rounding: $value (max: 1e9)")` |
| `decimals < 0 or > 10` | `IllegalArgumentException("decimals must be in range 0..10, was $decimals")` |
| `NaN` / `Infinity` | Fails the `abs(value) < 1e9` guard (comparison returns false) → throws |
| `roundSafe()` on any error | Returns original `value` unchanged, never throws |

---

## Usage Examples

```kotlin
// Standard 4-decimal rounding (default)
MathUtils.round(1.123456789)          // → 1.1235
MathUtils.round(1.123449999)          // → 1.1234

// Explicit decimal count
MathUtils.round(3.14159, 2)           // → 3.14
MathUtils.round(250000.123456, 4)     // → 250000.1235

// Safe variant — never throws
MathUtils.roundSafe(1e10, 4)          // → 1e10  (guard triggered, original returned)
MathUtils.roundSafe(1.5, 0)           // → 2.0   (normal rounding)
```

---

## Common Errors and Solutions

| Error | Root cause | Fix |
|-------|-----------|-----|
| `IllegalArgumentException: Value too large…` | Coordinate > 1 billion mm (1,000 km) | Check data source; use `roundSafe()` if value may legitimately be large |
| `IllegalArgumentException: decimals must be…` | `decimals` outside 0–10 | Clamp before calling: `decimals.coerceIn(0, 10)` |
| Golden test flakiness | Using `Float` or `roundToInt()` elsewhere | Audit all math paths; enforce ARCH-MATH-001 / ARCH-SAFE-001 |
| Unexpected `-2.0` for `-1.5` | Expected "toward zero" but implementation is "away from zero" | This is intentional — half-up is symmetric away from zero |

---

## File Locations

| Artifact | Path |
|----------|------|
| Implementation | `core/drawing2d/src/main/kotlin/com/yourapp/drawing2d/math/MathUtils.kt` |
| Tests | `core/drawing2d/src/test/kotlin/com/yourapp/drawing2d/math/MathUtilsTest.kt` |
| Arch rule | `specs/arch/domain_core.json` (ARCH-MATH-001, ARCH-SAFE-001) |
| AC spec | `specs/sprints/sprint-1.md` (block `S1-PR1.5-AC1`) |
