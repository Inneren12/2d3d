package com.yourapp.drawing2d.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Stroke/fill style shared by all entity types.
 *
 * [color] is a CSS-style hex string (e.g. "#FF0000").
 * [width] must be stored as [Double] per ARCH-MATH-001.
 * [dashPattern] is null for a solid line; otherwise a list of on/off lengths.
 */
@Serializable
data class LineStyle(
    val color: String,
    val width: Double,
    val dashPattern: List<Double>? = null
)

/**
 * Polymorphic sealed hierarchy of 2-D drawing entities.
 *
 * All concrete subclasses are [kotlinx.serialization.Serializable] and use
 * @SerialName discriminators so the JSON type field survives round-trips.
 * Coordinates are [Double] only — [Float] is forbidden (ARCH-MATH-001).
 *
 * Geometric constraints are enforced eagerly in each subclass `init` block
 * so that an invalid entity can never be constructed.
 */
@Serializable
sealed class EntityV1 {
    abstract val id: String
    abstract val layer: String?

    /**
     * A straight line segment from [start] to [end].
     */
    @Serializable
    @SerialName("line")
    data class Line(
        override val id: String,
        override val layer: String? = null,
        val start: Point2D,
        val end: Point2D,
        val style: LineStyle
    ) : EntityV1()

    /**
     * A circle defined by [center] and [radius].
     *
     * Invariants (enforced in `init`):
     * - `radius > 0`
     * - `radius.isFinite()` (NaN and ±Infinity are rejected)
     */
    @Serializable
    @SerialName("circle")
    data class Circle(
        override val id: String,
        override val layer: String? = null,
        val center: Point2D,
        val radius: Double,
        val style: LineStyle
    ) : EntityV1() {
        init {
            require(radius > 0 && radius.isFinite()) {
                "Circle radius must be finite and > 0, but was $radius"
            }
        }
    }

    /**
     * An open or closed polyline defined by an ordered list of [points].
     *
     * Invariants (enforced in `init`):
     * - `points.size >= 2` (a single point is not a line)
     */
    @Serializable
    @SerialName("polyline")
    data class Polyline(
        override val id: String,
        override val layer: String? = null,
        val points: List<Point2D>,
        val closed: Boolean = false,
        val style: LineStyle
    ) : EntityV1() {
        init {
            require(points.size >= 2) {
                "Polyline must have at least 2 points"
            }
        }
    }

    /**
     * A circular arc defined by [center], [radius], and angular extent from
     * [startAngle] to [endAngle] (both in degrees, measured counter-clockwise
     * from the positive X axis).
     *
     * Invariants (enforced in `init`):
     * - `radius > 0 && radius.isFinite()`
     * - `startAngle.isFinite()` and `endAngle.isFinite()`
     */
    @Serializable
    @SerialName("arc")
    data class Arc(
        override val id: String,
        override val layer: String? = null,
        val center: Point2D,
        val radius: Double,
        val startAngle: Double,
        val endAngle: Double,
        val style: LineStyle
    ) : EntityV1() {
        init {
            require(radius > 0 && radius.isFinite()) {
                "Arc radius must be finite and > 0, but was $radius"
            }
            require(startAngle.isFinite()) {
                "Arc startAngle must be finite, but was $startAngle"
            }
            require(endAngle.isFinite()) {
                "Arc endAngle must be finite, but was $endAngle"
            }
        }
    }
}
