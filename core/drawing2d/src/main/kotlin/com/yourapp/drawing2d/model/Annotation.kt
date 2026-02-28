package com.yourapp.drawing2d.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Measurement units supported by [AnnotationV1.Dimension].
 *
 * Serialized as plain enum name strings ("MM", "CM", etc.).
 */
@Serializable
enum class Units {
    MM,
    CM,
    M,
    INCHES,
    FEET,
}

/**
 * Polymorphic sealed hierarchy of annotation types.
 *
 * Annotations form the metadata layer over drawing entities — labels,
 * measurements, categorical tags, and logical groupings.
 *
 * [targetId] is nullable for types that may stand alone ([Text], [Group])
 * and non-nullable for types that must reference an entity ([Dimension], [Tag]).
 *
 * All numeric fields use [Double] per ARCH-MATH-001.
 * All subclasses are [@Serializable][Serializable] with [@SerialName][SerialName]
 * discriminators for polymorphic JSON round-trips.
 */
@Serializable
sealed class AnnotationV1 {
    abstract val id: String
    abstract val targetId: String?

    /**
     * A free-form text label positioned in drawing space.
     *
     * [targetId] is nullable — a [Text] annotation may float independently or
     * be attached to a specific entity.
     */
    @Serializable
    @SerialName("text")
    data class Text(
        override val id: String,
        override val targetId: String?,
        val position: Point2D,
        val content: String,
        val fontSize: Double,
        val rotation: Double,
    ) : AnnotationV1()

    /**
     * A measured value with units, always referencing a drawing entity.
     *
     * Invariant (enforced in `init`):
     * - `value >= 0` (negative measurements are physically invalid)
     */
    @Serializable
    @SerialName("dimension")
    data class Dimension(
        override val id: String,
        override val targetId: String,
        val value: Double,
        val units: Units,
        val position: Point2D,
    ) : AnnotationV1() {
        init {
            require(value >= 0) { "Dimension value must be >= 0, but was $value" }
        }
    }

    /**
     * A categorical metadata tag attached to a drawing entity.
     *
     * [category] is optional — a tag may carry only a [label] with no broader
     * category grouping.
     */
    @Serializable
    @SerialName("tag")
    data class Tag(
        override val id: String,
        override val targetId: String,
        val label: String,
        val category: String? = null,
    ) : AnnotationV1()

    /**
     * A logical grouping of drawing entities identified by [memberIds].
     *
     * [targetId] is nullable — a [Group] may stand alone or be associated with
     * a parent entity.
     *
     * Invariant (enforced in `init`):
     * - `memberIds.isNotEmpty()` (a group with zero members is meaningless)
     */
    @Serializable
    @SerialName("group")
    data class Group(
        override val id: String,
        override val targetId: String?,
        val name: String,
        val memberIds: List<String>,
    ) : AnnotationV1() {
        init {
            require(memberIds.isNotEmpty()) { "Group must have at least 1 member" }
        }
    }
}
