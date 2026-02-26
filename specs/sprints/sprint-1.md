<ac-block id="S1-PR1.5-AC1">
**Acceptance Criteria for PR1.5 (MathUtils)**:
- [ ] Function `round(value: Double, decimals: Int)` uses `toLong()` (NOT `roundToInt()`)
- [ ] Guard: `require(abs(value) < 1e9)` to prevent overflow
- [ ] Test: `round(1.123456789, 4) === 1.1235`
- [ ] Test: Large values (250,000mm) handled correctly
- [ ] Test: Extreme values (>1e9) throw exception with clear message
- [ ] `roundSafe()` never throws exceptions
- [ ] Code coverage >95% for MathUtils.kt
- [ ] No `Float` types used (ARCH-MATH-001 compliance)
- [ ] Works identically on ARM and x86 (deterministic)
</ac-block>