package com.yourapp.validation

import com.yourapp.drawing2d.model.AnnotationV1
import com.yourapp.drawing2d.model.Drawing2D
import com.yourapp.drawing2d.model.EntityV1
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlin.math.sqrt

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
        const val MIN_LINE_LENGTH = 1e-6
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
            violations.addAll(validateGeometry(entity, index))
        }

        // Build entity ID set once (O(N) instead of O(N*M))
        val entityIds = drawing.entities.asSequence()
            .map { it.id }
            .toHashSet()

        // Validate each annotation
        drawing.annotations.forEachIndexed { index, annotation ->
            violations.addAll(validateAnnotation(annotation, index, entityIds))
        }

        return violations
    }

    /**
     * Validates a single entity.
     *
     * Performs both ID validation and type-specific value validation.
     * Note: Init blocks enforce most geometric constraints, so these checks are
     * defensive — they fire if an entity somehow bypasses the constructor (e.g.
     * deserialization with lenient config or future refactoring).
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

        // Type-specific validation
        when (entity) {
            is EntityV1.Circle -> {
                // Radius must be positive
                if (entity.radius <= 0) {
                    violations.add(
                        Violation.InvalidValue(
                            path = "$path.radius",
                            fieldName = "radius",
                            value = entity.radius.toString(),
                            constraint = "must be positive",
                        ),
                    )
                }

                // Radius must be finite
                if (!entity.radius.isFinite()) {
                    violations.add(
                        Violation.InvalidValue(
                            path = "$path.radius",
                            fieldName = "radius",
                            value = entity.radius.toString(),
                            constraint = "must be finite",
                        ),
                    )
                }
            }

            is EntityV1.Polyline -> {
                // Must have at least 2 points
                if (entity.points.size < 2) {
                    violations.add(
                        Violation.InvalidValue(
                            path = "$path.points",
                            fieldName = "points",
                            value = "size=${entity.points.size}",
                            constraint = "minimum 2 points required",
                        ),
                    )
                }
            }

            is EntityV1.Arc -> {
                // Radius must be positive
                if (entity.radius <= 0) {
                    violations.add(
                        Violation.InvalidValue(
                            path = "$path.radius",
                            fieldName = "radius",
                            value = entity.radius.toString(),
                            constraint = "must be positive",
                        ),
                    )
                }

                // Radius must be finite
                if (!entity.radius.isFinite()) {
                    violations.add(
                        Violation.InvalidValue(
                            path = "$path.radius",
                            fieldName = "radius",
                            value = entity.radius.toString(),
                            constraint = "must be finite",
                        ),
                    )
                }
            }

            else -> {
                // Line: no additional validation needed
            }
        }

        return violations
    }

    /**
     * Validates geometric properties of an entity.
     *
     * Checks for:
     * - Non-finite coordinates (NaN, Infinity)
     * - Non-finite radius values
     * - Non-finite angles
     * - Degenerate geometry (zero-length lines)
     */
    private fun validateGeometry(
        entity: EntityV1,
        index: Int,
    ): List<Violation> {
        val violations = mutableListOf<Violation>()
        val path = "drawing.entities[$index]"

        when (entity) {
            is EntityV1.Line -> {
                if (!entity.start.x.isFinite()) {
                    violations.add(
                        Violation.InvalidValue(
                            path = "$path.start.x",
                            fieldName = "x",
                            value = entity.start.x.toString(),
                            constraint = "must be finite",
                        ),
                    )
                }
                if (!entity.start.y.isFinite()) {
                    violations.add(
                        Violation.InvalidValue(
                            path = "$path.start.y",
                            fieldName = "y",
                            value = entity.start.y.toString(),
                            constraint = "must be finite",
                        ),
                    )
                }
                if (!entity.end.x.isFinite()) {
                    violations.add(
                        Violation.InvalidValue(
                            path = "$path.end.x",
                            fieldName = "x",
                            value = entity.end.x.toString(),
                            constraint = "must be finite",
                        ),
                    )
                }
                if (!entity.end.y.isFinite()) {
                    violations.add(
                        Violation.InvalidValue(
                            path = "$path.end.y",
                            fieldName = "y",
                            value = entity.end.y.toString(),
                            constraint = "must be finite",
                        ),
                    )
                }

                // Check for degenerate line (zero length)
                val dx = entity.end.x - entity.start.x
                val dy = entity.end.y - entity.start.y
                val length = sqrt(dx * dx + dy * dy)

                if (length < MIN_LINE_LENGTH) {
                    violations.add(
                        Violation.Custom(
                            path = "$path.length",
                            severity = Severity.WARNING,
                            message = "Line has zero length (length=$length, min=$MIN_LINE_LENGTH)",
                        ),
                    )
                }
            }

            is EntityV1.Circle -> {
                if (!entity.center.x.isFinite()) {
                    violations.add(
                        Violation.InvalidValue(
                            path = "$path.center.x",
                            fieldName = "x",
                            value = entity.center.x.toString(),
                            constraint = "must be finite",
                        ),
                    )
                }
                if (!entity.center.y.isFinite()) {
                    violations.add(
                        Violation.InvalidValue(
                            path = "$path.center.y",
                            fieldName = "y",
                            value = entity.center.y.toString(),
                            constraint = "must be finite",
                        ),
                    )
                }
            }

            is EntityV1.Polyline -> {
                entity.points.forEachIndexed { pointIndex, point ->
                    if (!point.x.isFinite()) {
                        violations.add(
                            Violation.InvalidValue(
                                path = "$path.points[$pointIndex].x",
                                fieldName = "x",
                                value = point.x.toString(),
                                constraint = "must be finite",
                            ),
                        )
                    }
                    if (!point.y.isFinite()) {
                        violations.add(
                            Violation.InvalidValue(
                                path = "$path.points[$pointIndex].y",
                                fieldName = "y",
                                value = point.y.toString(),
                                constraint = "must be finite",
                            ),
                        )
                    }
                }
            }

            is EntityV1.Arc -> {
                if (!entity.center.x.isFinite()) {
                    violations.add(
                        Violation.InvalidValue(
                            path = "$path.center.x",
                            fieldName = "x",
                            value = entity.center.x.toString(),
                            constraint = "must be finite",
                        ),
                    )
                }
                if (!entity.center.y.isFinite()) {
                    violations.add(
                        Violation.InvalidValue(
                            path = "$path.center.y",
                            fieldName = "y",
                            value = entity.center.y.toString(),
                            constraint = "must be finite",
                        ),
                    )
                }
                if (!entity.startAngle.isFinite()) {
                    violations.add(
                        Violation.InvalidValue(
                            path = "$path.startAngle",
                            fieldName = "startAngle",
                            value = entity.startAngle.toString(),
                            constraint = "must be finite",
                        ),
                    )
                }
                if (!entity.endAngle.isFinite()) {
                    violations.add(
                        Violation.InvalidValue(
                            path = "$path.endAngle",
                            fieldName = "endAngle",
                            value = entity.endAngle.toString(),
                            constraint = "must be finite",
                        ),
                    )
                }
            }
        }

        return violations
    }

    /**
     * Validates a single annotation.
     */
    private fun validateAnnotation(
        annotation: AnnotationV1,
        index: Int,
        entityIds: Set<String>,
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
            if (targetId !in entityIds) {
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
        } catch (e: IllegalArgumentException) {
            // Init block validation failure (e.g., negative radius)
            Result.failure(
                ValidationException(
                    message = "Invalid entity data: ${e.message}",
                    violations =
                        listOf(
                            Violation.Custom(
                                path = "entity",
                                severity = Severity.ERROR,
                                message = "Entity validation failed during construction: ${e.message}",
                            ),
                        ),
                ),
            )
        } catch (e: Exception) {
            // Unexpected error
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
