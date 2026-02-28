package com.yourapp.drawing2d.math

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe

class MathUtilsTest : FunSpec({

    // ── round() ──────────────────────────────────────────────────────────────

    context("round – basic correctness") {

        test("AC: round(1.123456789, 4) == 1.1235") {
            MathUtils.round(1.123456789, 4) shouldBe 1.1235
        }

        test("AC: round(1.123449999, 4) == 1.1234 (rounds down)") {
            MathUtils.round(1.123449999, 4) shouldBe 1.1234
        }

        test("default decimals parameter is 4") {
            MathUtils.round(1.123456789) shouldBe 1.1235
        }

        test("AC: round(5.5, 0) == 6.0 (half-up)") {
            MathUtils.round(5.5, 0) shouldBe 6.0
        }

        test("AC: round(-5.5, 0) == -5.0 (half-up rounds toward zero for negatives)") {
            MathUtils.round(-5.5, 0) shouldBe -5.0
        }

        test("rounds 1.5 to 0 decimals -> 2.0 (half-up)") {
            MathUtils.round(1.5, 0) shouldBe 2.0
        }

        test("rounds 1.4 to 0 decimals -> 1.0") {
            MathUtils.round(1.4, 0) shouldBe 1.0
        }

        test("AC: round(1.9, 0) == 2.0 (rounding not truncation)") {
            MathUtils.round(1.9, 0) shouldBe 2.0
        }
    }

    context("round – large values (250 000 mm scale)") {

        test("AC: 250000.0 handled correctly at 4 decimals") {
            MathUtils.round(250000.123456, 4) shouldBe 250000.1235
        }

        test("AC: round(250000.123456, 4) == 250000.1235 (large value with decimals)") {
            MathUtils.round(250_000.123456, 4) shouldBe 250_000.1235
        }
    }

    context("round – negative values") {

        test("AC: negative value rounds symmetrically to -1.1235") {
            MathUtils.round(-1.123456789, 4) shouldBe -1.1235
        }

        test("negative 250000.123456 rounds correctly") {
            MathUtils.round(-250000.123456, 4) shouldBe -250000.1235
        }

        test("negative half (-1.5) rounds to -2.0 (half-up away from zero)") {
            MathUtils.round(-1.5, 0) shouldBe -2.0
        }
    }

    context("round – boundary decimals (0 and 10)") {

        test("decimals=0 boundary: allowed") {
            MathUtils.round(3.7, 0) shouldBe 4.0
        }

        test("decimals=10 boundary: allowed") {
            MathUtils.round(1.12345678901, 10) shouldBe (1.1234567890 plusOrMinus 1e-9)
        }
    }

    context("round – guard: extreme values >1e9 throw") {

        test("AC: value >= 1e9 throws IllegalArgumentException") {
            shouldThrow<IllegalArgumentException> {
                MathUtils.round(1e9, 4)
            }
        }

        test("value 1_000_000_001.0 throws IllegalArgumentException") {
            shouldThrow<IllegalArgumentException> {
                MathUtils.round(1_000_000_001.0, 4)
            }
        }

        test("negative value <= -1e9 throws IllegalArgumentException") {
            shouldThrow<IllegalArgumentException> {
                MathUtils.round(-1_000_000_001.0, 4)
            }
        }

        test("exception message mentions the offending value and max") {
            val ex = shouldThrow<IllegalArgumentException> {
                MathUtils.round(2e9, 4)
            }
            ex.message shouldBe "Value too large for safe rounding: 2.0E9 (max: 1e9)"
        }

        test("AC: round(1e10, 4) throws IllegalArgumentException") {
            shouldThrow<IllegalArgumentException> {
                MathUtils.round(1e10, 4)
            }
        }
    }

    context("round – guard: invalid decimals throw") {

        test("decimals = -1 throws IllegalArgumentException") {
            shouldThrow<IllegalArgumentException> {
                MathUtils.round(1.0, -1)
            }
        }

        test("decimals = 11 throws IllegalArgumentException") {
            shouldThrow<IllegalArgumentException> {
                MathUtils.round(1.0, 11)
            }
        }
    }

    // ── roundSafe() ───────────────────────────────────────────────────────────

    context("roundSafe – valid input behaves like round()") {

        test("AC: roundSafe(1.123456789, 4) == 1.1235") {
            MathUtils.roundSafe(1.123456789, 4) shouldBe 1.1235
        }

        test("large value 250000.123456 rounds correctly") {
            MathUtils.roundSafe(250000.123456, 4) shouldBe 250000.1235
        }

        test("negative value rounds correctly") {
            MathUtils.roundSafe(-1.123456789, 4) shouldBe -1.1235
        }
    }

    context("roundSafe – never throws, returns original value on guard failure") {

        test("AC: extreme value >1e9 returns original value without throwing") {
            MathUtils.roundSafe(2e9, 4) shouldBe 2e9
        }

        test("AC: roundSafe(1e10, 4) == 1e10 (no throw)") {
            MathUtils.roundSafe(1e10, 4) shouldBe 1e10
        }

        test("negative extreme value <-1e9 returns original value without throwing") {
            MathUtils.roundSafe(-2e9, 4) shouldBe -2e9
        }

        test("invalid decimals (negative) returns original value without throwing") {
            MathUtils.roundSafe(1.0, -1) shouldBe 1.0
        }

        test("invalid decimals (>10) returns original value without throwing") {
            MathUtils.roundSafe(1.0, 11) shouldBe 1.0
        }
    }

    // ── determinism ───────────────────────────────────────────────────────────

    context("round – determinism") {

        test("AC: repeated calls with same input produce identical result") {
            val first  = MathUtils.round(1.123456789, 4)
            val second = MathUtils.round(1.123456789, 4)
            val third  = MathUtils.round(1.123456789, 4)
            first shouldBe second
            second shouldBe third
        }

        test("determinism holds for large values with decimals") {
            val first  = MathUtils.round(250_000.123456, 4)
            val second = MathUtils.round(250_000.123456, 4)
            val third  = MathUtils.round(250_000.123456, 4)
            first shouldBe 250_000.1235
            second shouldBe 250_000.1235
            third shouldBe 250_000.1235
            first shouldBe second
            second shouldBe third
        }
    }
})
