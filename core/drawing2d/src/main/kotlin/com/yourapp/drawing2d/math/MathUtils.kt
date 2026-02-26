package com.yourapp.drawing2d.math

import kotlin.math.abs

object MathUtils {

    /**
     * Rounds [value] to [decimals] decimal places using deterministic toLong() scaling.
     * Complies with ARCH-MATH-001 (Double only) and ARCH-SAFE-001 (toLong, not roundToInt).
     *
     * @throws IllegalArgumentException if decimals not in 0..10 or abs(value) >= 1e9
     */
    fun round(value: Double, decimals: Int = 4): Double {
        require(decimals in 0..10) {
            "decimals must be in range 0..10, was $decimals"
        }
        require(abs(value) < 1e9) {
            "abs(value) must be < 1e9 to prevent overflow, was $value"
        }
        val factor = tenPow(decimals)
        val sign = if (value >= 0.0) 0.5 else -0.5
        return (value * factor + sign).toLong().toDouble() / factor
    }

    /**
     * Like [round] but never throws. Returns the original [value] unchanged when guards fail.
     */
    fun roundSafe(value: Double, decimals: Int = 4): Double {
        return try {
            round(value, decimals)
        } catch (_: IllegalArgumentException) {
            value
        }
    }

    /** Avoids Math.pow() for all decimals values in the valid range 0..10. */
    private fun tenPow(n: Int): Double = when (n) {
        0  -> 1.0
        1  -> 10.0
        2  -> 100.0
        3  -> 1_000.0
        4  -> 10_000.0
        5  -> 100_000.0
        6  -> 1_000_000.0
        7  -> 10_000_000.0
        8  -> 100_000_000.0
        9  -> 1_000_000_000.0
        10 -> 10_000_000_000.0
        else -> Math.pow(10.0, n.toDouble()) // unreachable given 0..10 guard above
    }
}
