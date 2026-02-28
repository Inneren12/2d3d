package com.yourapp.drawing2d.model

import com.yourapp.drawing2d.math.MathUtils
import kotlinx.serialization.Serializable
import kotlin.math.sqrt

/**
 * An immutable 2-D coordinate.
 *
 * Coordinates are stored as [Double] in compliance with ARCH-MATH-001.
 * [toJsonSafe] returns a copy with every field rounded to 4 decimal places
 * via [MathUtils.round], ensuring deterministic serialization.
 */
@Serializable
data class Point2D(val x: Double, val y: Double) {
    operator fun plus(other: Point2D): Point2D = Point2D(x + other.x, y + other.y)

    operator fun minus(other: Point2D): Point2D = Point2D(x - other.x, y - other.y)

    operator fun times(scalar: Double): Point2D = Point2D(x * scalar, y * scalar)

    /** Euclidean distance from this point to [other]. */
    fun distanceTo(other: Point2D): Double {
        val dx = x - other.x
        val dy = y - other.y
        return sqrt(dx * dx + dy * dy)
    }

    /**
     * Returns a copy of this point with coordinates rounded to 4 decimal places
     * using [MathUtils.round] for deterministic serialization (ARCH-MATH-001).
     */
    fun toJsonSafe(): Point2D =
        Point2D(
            x = MathUtils.round(x, 4),
            y = MathUtils.round(y, 4),
        )
}

/**
 * An immutable 2-D direction/displacement vector.
 *
 * Components are stored as [Double] in compliance with ARCH-MATH-001.
 */
@Serializable
data class Vector2D(val x: Double, val y: Double) {
    operator fun plus(other: Vector2D): Vector2D = Vector2D(x + other.x, y + other.y)

    operator fun minus(other: Vector2D): Vector2D = Vector2D(x - other.x, y - other.y)

    operator fun times(scalar: Double): Vector2D = Vector2D(x * scalar, y * scalar)

    /** Euclidean magnitude (length) of this vector. */
    fun length(): Double = sqrt(x * x + y * y)

    /**
     * Returns a copy of this vector with components rounded to 4 decimal places
     * using [MathUtils.round] for deterministic serialization (ARCH-MATH-001).
     */
    fun toJsonSafe(): Vector2D =
        Vector2D(
            x = MathUtils.round(x, 4),
            y = MathUtils.round(y, 4),
        )
}
