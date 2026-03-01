package com.yourapp.validation

import kotlinx.serialization.Serializable

/**
 * Severity level for validation violations.
 */
@Serializable
enum class Severity {
    /** Fatal error - operation cannot proceed */
    ERROR,

    /** Potential problem - review recommended */
    WARNING,

    /** Informational note - no action required */
    INFO,
}

/**
 * Base class for structured validation violations.
 *
 * Instead of throwing exceptions with string messages, we return
 * these typed errors which can be:
 * - Serialized to JSON for API responses
 * - Analyzed programmatically
 * - Localized for different languages
 * - Presented with actionable UI
 */
@Serializable
sealed class Violation {
    abstract val path: String
    abstract val severity: Severity
    abstract val message: String

    /**
     * Missing required field.
     *
     * Example: Drawing has no `id` field.
     */
    @Serializable
    data class MissingField(
        override val path: String,
        val fieldName: String,
    ) : Violation() {
        override val severity = Severity.ERROR
        override val message = "Missing required field: $fieldName"
    }

    /**
     * Invalid field value.
     *
     * Example: Circle radius is negative.
     */
    @Serializable
    data class InvalidValue(
        override val path: String,
        val fieldName: String,
        val value: String,
        val constraint: String,
    ) : Violation() {
        override val severity = Severity.ERROR
        override val message = "Invalid value for $fieldName: '$value' ($constraint)"
    }

    /**
     * Broken reference to another entity.
     *
     * Example: Annotation references non-existent entity ID.
     */
    @Serializable
    data class BrokenReference(
        override val path: String,
        val referenceId: String,
        val targetType: String,
    ) : Violation() {
        override val severity = Severity.ERROR
        override val message = "Reference $referenceId to $targetType not found"
    }

    /**
     * Custom violation with arbitrary message.
     *
     * Use for domain-specific validation rules.
     */
    @Serializable
    data class Custom(
        override val path: String,
        override val severity: Severity,
        override val message: String,
    ) : Violation()
}
