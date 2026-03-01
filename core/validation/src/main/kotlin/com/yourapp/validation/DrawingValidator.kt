package com.yourapp.validation

import com.yourapp.drawing2d.model.AnnotationV1
import com.yourapp.drawing2d.model.Drawing2D
import com.yourapp.drawing2d.model.EntityV1
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * Validates Drawing2D structure and returns structured violations.
 *
 * CRITICAL: This validator NEVER throws exceptions. All errors are
 * returned as Violation objects for programmatic handling.
 */
class DrawingValidator {
    companion object {
        const val MAX_ENTITIES = 100_000
        const val MAX_ANNOTATIONS = 100_000
    }

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Validates a Drawing2D and returns list of violations.
     *
     * Returns empty list if drawing is valid.
     */
    fun validate(drawing: Drawing2D): List<Violation> {
        val violations = mutableListOf<Violation>()

        // Check schema version
        if (drawing.schemaVersion != 1) {
            violations.add(
                Violation.Custom(
                    path = "drawing",
                    severity = Severity.ERROR,
                    message = "Unsupported schema version: ${drawing.schemaVersion} (expected: 1)",
                ),
            )
        }

        // Hard limit: entities
        if (drawing.entities.size > MAX_ENTITIES) {
            violations.add(
                Violation.Custom(
                    path = "drawing.entities",
                    severity = Severity.ERROR,
                    message = "Too many entities: ${drawing.entities.size} (max: $MAX_ENTITIES)",
                ),
            )
        }

        // Hard limit: annotations
        if (drawing.annotations.size > MAX_ANNOTATIONS) {
            violations.add(
                Violation.Custom(
                    path = "drawing.annotations",
                    severity = Severity.ERROR,
                    message = "Too many annotations: ${drawing.annotations.size} (max: $MAX_ANNOTATIONS)",
                ),
            )
        }

        // Validate each entity
        drawing.entities.forEachIndexed { index, entity ->
            violations.addAll(validateEntity(entity, index))
        }

        // Validate each annotation
        drawing.annotations.forEachIndexed { index, annotation ->
            violations.addAll(validateAnnotation(annotation, index, drawing))
        }

        return violations
    }

    /**
     * Validates a single entity.
     *
     * Note: Geometric constraints (Circle/Arc radius > 0 and finite, Polyline >= 2 points)
     * are enforced by the model's own `init` blocks, so those invariants are guaranteed
     * on any successfully-constructed entity. Invalid JSON inputs that would violate these
     * constraints are caught by [validateSafe]'s serialization exception handling.
     */
    private fun validateEntity(
        entity: EntityV1,
        index: Int,
    ): List<Violation> {
        val violations = mutableListOf<Violation>()
        val path = "drawing.entities[$index]"

        // Check ID is non-blank
        if (entity.id.isBlank()) {
            violations.add(
                Violation.InvalidValue(
                    path = path,
                    fieldName = "id",
                    value = "\"${entity.id}\"",
                    constraint = "must not be blank",
                ),
            )
        }

        return violations
    }

    /**
     * Validates a single annotation.
     */
    private fun validateAnnotation(
        annotation: AnnotationV1,
        index: Int,
        drawing: Drawing2D,
    ): List<Violation> {
        val violations = mutableListOf<Violation>()
        val path = "drawing.annotations[$index]"

        // Check ID is non-blank
        if (annotation.id.isBlank()) {
            violations.add(
                Violation.InvalidValue(
                    path = path,
                    fieldName = "id",
                    value = "\"${annotation.id}\"",
                    constraint = "must not be blank",
                ),
            )
        }

        // Check targetId reference exists (if not null)
        val targetId = annotation.targetId
        if (targetId != null) {
            val entityExists = drawing.entities.any { it.id == targetId }
            if (!entityExists) {
                violations.add(
                    Violation.BrokenReference(
                        path = "$path.targetId",
                        referenceId = targetId,
                        targetType = "Entity",
                    ),
                )
            }
        }

        return violations
    }

    /**
     * Safely validates a JSON string and returns Result.
     *
     * CRITICAL: This method NEVER throws exceptions. All errors
     * (parse errors, validation errors) are returned as Failure.
     *
     * Returns Success only if:
     * - JSON parses successfully
     * - Drawing2D has zero ERROR-severity violations
     */
    fun validateSafe(jsonString: String): Result<Drawing2D> {
        return try {
            // Parse JSON
            val drawing = json.decodeFromString<Drawing2D>(jsonString)

            // Validate drawing
            val violations = validate(drawing)

            // Check for ERROR-severity violations
            val errors = violations.filter { it.severity == Severity.ERROR }
            if (errors.isNotEmpty()) {
                return Result.failure(
                    ValidationException(
                        message = "Drawing has ${errors.size} validation error(s)",
                        violations = violations,
                    ),
                )
            }

            // Success
            Result.success(drawing)
        } catch (e: SerializationException) {
            // JSON parse error
            Result.failure(
                ValidationException(
                    message = "Invalid JSON: ${e.message}",
                    violations =
                        listOf(
                            Violation.Custom(
                                path = "json",
                                severity = Severity.ERROR,
                                message = "JSON parse error: ${e.message}",
                            ),
                        ),
                ),
            )
        } catch (e: Exception) {
            // Unexpected error (e.g. IllegalArgumentException from model init blocks)
            Result.failure(
                ValidationException(
                    message = "Validation error: ${e.message}",
                    violations =
                        listOf(
                            Violation.Custom(
                                path = "unknown",
                                severity = Severity.ERROR,
                                message = "Unexpected error: ${e.message}",
                            ),
                        ),
                ),
            )
        }
    }
}

/**
 * Exception thrown when validation fails.
 *
 * Contains structured violations for programmatic handling.
 */
class ValidationException(
    message: String,
    val violations: List<Violation>,
) : Exception(message)
