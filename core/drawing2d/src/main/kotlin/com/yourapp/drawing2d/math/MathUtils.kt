package com.yourapp.drawing2d.math

import com.yourapp.domain.Precision
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.abs

object MathUtils {
    /**
     * Rounds [value] to [decimals] decimal places using HALF_UP rounding.
     *
     * @throws IllegalArgumentException if decimals not in 0..10 or abs(value) >= 1e9
     */
    fun round(
        value: Double,
        decimals: Int = Precision.DEFAULT_DECIMALS,
    ): Double {
        require(decimals in 0..10) {
            "decimals must be in range 0..10, was $decimals"
        }
        require(abs(value) < 1e9) {
            "Value too large for safe rounding: $value (max: 1e9)"
        }

        val bd = BigDecimal(value)
        return if (value >= 0) {
            // Positive: HALF_UP (5.5 → 6)
            bd.setScale(decimals, RoundingMode.HALF_UP).toDouble()
        } else {
            // Negative: HALF_DOWN (-5.5 → -5, rounds toward zero)
            bd.setScale(decimals, RoundingMode.HALF_DOWN).toDouble()
        }
    }

    /**
     * Like [round] but never throws. Returns the original [value] unchanged when guards fail.
     */
    fun roundSafe(
        value: Double,
        decimals: Int = Precision.DEFAULT_DECIMALS,
    ): Double {
        return try {
            round(value, decimals)
        } catch (_: IllegalArgumentException) {
            value
        }
    }
}
