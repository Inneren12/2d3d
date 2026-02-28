package com.yourapp.drawing2d.model

import com.yourapp.drawing2d.math.MathUtils
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.encoding.encodeStructure
import kotlin.math.sqrt

/**
 * An immutable 2-D coordinate.
 *
 * Coordinates are stored as [Double] in compliance with ARCH-MATH-001.
 * [toJsonSafe] returns a copy with every field rounded to 4 decimal places
 * via [MathUtils.round], ensuring deterministic serialization.
 */
@Serializable(with = Point2DSerializer::class)
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
 * Custom serializer that ensures Point2D coordinates are always
 * rounded to 4 decimal places during serialization.
 *
 * This guarantees:
 * - Deterministic JSON output (same coordinates → same JSON)
 * - Smaller JSON files (no excessive decimal places)
 * - Cross-platform consistency (ARM/x86)
 */
object Point2DSerializer : KSerializer<Point2D> {
    override val descriptor =
        buildClassSerialDescriptor("Point2D") {
            element<Double>("x")
            element<Double>("y")
        }

    override fun serialize(
        encoder: Encoder,
        value: Point2D,
    ) {
        val safe = value.toJsonSafe()
        encoder.encodeStructure(descriptor) {
            encodeDoubleElement(descriptor, 0, safe.x)
            encodeDoubleElement(descriptor, 1, safe.y)
        }
    }

    override fun deserialize(decoder: Decoder): Point2D =
        decoder.decodeStructure(descriptor) {
            var x = 0.0
            var y = 0.0
            while (true) {
                when (val index = decodeElementIndex(descriptor)) {
                    0 -> x = decodeDoubleElement(descriptor, 0)
                    1 -> y = decodeDoubleElement(descriptor, 1)
                    CompositeDecoder.DECODE_DONE -> break
                    else -> error("Unexpected index: $index")
                }
            }
            Point2D(x, y)
        }
}

/**
 * An immutable 2-D direction/displacement vector.
 *
 * Components are stored as [Double] in compliance with ARCH-MATH-001.
 */
@Serializable(with = Vector2DSerializer::class)
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

/**
 * Custom serializer that ensures Vector2D components are always
 * rounded to 4 decimal places during serialization.
 *
 * Same guarantees as [Point2DSerializer].
 */
object Vector2DSerializer : KSerializer<Vector2D> {
    override val descriptor =
        buildClassSerialDescriptor("Vector2D") {
            element<Double>("x")
            element<Double>("y")
        }

    override fun serialize(
        encoder: Encoder,
        value: Vector2D,
    ) {
        val safe = value.toJsonSafe()
        encoder.encodeStructure(descriptor) {
            encodeDoubleElement(descriptor, 0, safe.x)
            encodeDoubleElement(descriptor, 1, safe.y)
        }
    }

    override fun deserialize(decoder: Decoder): Vector2D =
        decoder.decodeStructure(descriptor) {
            var x = 0.0
            var y = 0.0
            while (true) {
                when (val index = decodeElementIndex(descriptor)) {
                    0 -> x = decodeDoubleElement(descriptor, 0)
                    1 -> y = decodeDoubleElement(descriptor, 1)
                    CompositeDecoder.DECODE_DONE -> break
                    else -> error("Unexpected index: $index")
                }
            }
            Vector2D(x, y)
        }
}
