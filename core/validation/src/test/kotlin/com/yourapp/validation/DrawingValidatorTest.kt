package com.yourapp.validation

import com.yourapp.drawing2d.model.AnnotationV1
import com.yourapp.drawing2d.model.Drawing2D
import com.yourapp.drawing2d.model.EntityV1
import com.yourapp.drawing2d.model.LineStyle
import com.yourapp.drawing2d.model.Page
import com.yourapp.drawing2d.model.Point2D
import com.yourapp.drawing2d.model.Units
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class DrawingValidatorTest : FunSpec({

    val validator = DrawingValidator()

    context("Valid drawings") {
        test("AC: Valid drawing returns empty violation list") {
            val drawing =
                Drawing2D(
                    id = "d1",
                    name = "Valid Drawing",
                    page = Page(width = 1000.0, height = 800.0),
                    entities =
                        listOf(
                            EntityV1.Line(
                                id = "line1",
                                layer = null,
                                start = Point2D(0.0, 0.0),
                                end = Point2D(100.0, 100.0),
                                style = LineStyle("#000", 1.0, null),
                            ),
                            EntityV1.Circle(
                                id = "circle1",
                                layer = null,
                                center = Point2D(50.0, 50.0),
                                radius = 25.0,
                                style = LineStyle("#000", 1.0, null),
                            ),
                            EntityV1.Polyline(
                                id = "polyline1",
                                layer = null,
                                points = listOf(Point2D(0.0, 0.0), Point2D(10.0, 10.0)),
                                style = LineStyle("#000", 1.0, null),
                            ),
                            EntityV1.Arc(
                                id = "arc1",
                                layer = null,
                                center = Point2D(0.0, 0.0),
                                radius = 10.0,
                                startAngle = 0.0,
                                endAngle = 90.0,
                                style = LineStyle("#000", 1.0, null),
                            ),
                        ),
                )

            val violations = validator.validate(drawing)

            violations.shouldBeEmpty()
        }
    }

    context("Schema version validation") {
        test("schemaVersion != 1 returns Custom violation") {
            val drawing =
                Drawing2D(
                    // schemaVersion 2 is invalid (only 1 is supported)
                    schemaVersion = 2,
                    id = "d1",
                    name = "Test",
                    page = Page(width = 1000.0, height = 800.0),
                )

            val violations = validator.validate(drawing)

            violations shouldHaveSize 1
            violations[0].shouldBeInstanceOf<Violation.Custom>()
            violations[0].severity shouldBe Severity.ERROR
            violations[0].message shouldContain "Unsupported schema version: 2"
        }
    }

    context("Hard limits") {
        test("AC: Drawing with 150,000 entities returns violation about exceeding limit") {
            val entities =
                (1..150_000).map {
                    EntityV1.Line(
                        id = "line$it",
                        layer = null,
                        start = Point2D(0.0, 0.0),
                        end = Point2D(1.0, 1.0),
                        style = LineStyle("#000", 1.0, null),
                    )
                }

            val drawing =
                Drawing2D(
                    id = "d1",
                    name = "Large Drawing",
                    page = Page(width = 1000.0, height = 800.0),
                    entities = entities,
                )

            val violations = validator.validate(drawing)

            violations.any { it.message.contains("Too many entities") } shouldBe true
            violations.any { it.message.contains("150000") } shouldBe true
            violations.any { it.message.contains("max: 100000") } shouldBe true
        }

        test("Drawing with 150,000 annotations returns violation") {
            val annotations =
                (1..150_000).map {
                    AnnotationV1.Text(
                        id = "text$it",
                        targetId = null,
                        position = Point2D(0.0, 0.0),
                        content = "tag",
                        fontSize = 12.0,
                        rotation = 0.0,
                    )
                }

            val drawing =
                Drawing2D(
                    id = "d1",
                    name = "Large Drawing",
                    page = Page(width = 1000.0, height = 800.0),
                    annotations = annotations,
                )

            val violations = validator.validate(drawing)

            violations.any { it.message.contains("Too many annotations") } shouldBe true
        }
    }

    context("Entity ID validation") {
        test("Blank entity ID returns InvalidValue violation") {
            val drawing =
                Drawing2D(
                    id = "d1",
                    name = "Test",
                    page = Page(width = 1000.0, height = 800.0),
                    entities =
                        listOf(
                            EntityV1.Line(
                                // Blank ID
                                id = "",
                                layer = null,
                                start = Point2D(0.0, 0.0),
                                end = Point2D(1.0, 1.0),
                                style = LineStyle("#000", 1.0, null),
                            ),
                        ),
                )

            val violations = validator.validate(drawing)

            violations shouldHaveSize 1
            violations[0].shouldBeInstanceOf<Violation.InvalidValue>()
            violations[0].path shouldBe "drawing.entities[0]"
            (violations[0] as Violation.InvalidValue).fieldName shouldBe "id"
        }
    }

    context("Circle validation") {
        // Note: EntityV1.Circle.init enforces radius > 0 && isFinite(), so invalid
        // circles cannot be constructed directly. These tests use validateSafe() with
        // crafted JSON to exercise the error handling path.

        test("AC: Circle with radius = -5.0 via JSON returns Failure without throwing") {
            val validDrawing =
                Drawing2D(
                    id = "d1",
                    name = "Test",
                    page = Page(width = 1000.0, height = 800.0),
                    entities =
                        listOf(
                            EntityV1.Circle(
                                id = "c1",
                                layer = null,
                                center = Point2D(50.0, 50.0),
                                radius = 25.0,
                                style = LineStyle("#000", 1.0, null),
                            ),
                        ),
                )

            val validJson = Json.encodeToString(validDrawing)
            val invalidJson = validJson.replace("\"radius\":25.0", "\"radius\":-5.0")

            val result = validator.validateSafe(invalidJson)

            result.isFailure shouldBe true
            val exception = result.exceptionOrNull()
            exception.shouldBeInstanceOf<ValidationException>()
        }

        test("Circle with radius = 0.0 (non-positive) via JSON returns Failure") {
            val validDrawing =
                Drawing2D(
                    id = "d1",
                    name = "Test",
                    page = Page(width = 1000.0, height = 800.0),
                    entities =
                        listOf(
                            EntityV1.Circle(
                                id = "c1",
                                layer = null,
                                center = Point2D(50.0, 50.0),
                                radius = 25.0,
                                style = LineStyle("#000", 1.0, null),
                            ),
                        ),
                )

            val validJson = Json.encodeToString(validDrawing)
            val invalidJson = validJson.replace("\"radius\":25.0", "\"radius\":0.0")

            val result = validator.validateSafe(invalidJson)

            result.isFailure shouldBe true
            result.exceptionOrNull().shouldBeInstanceOf<ValidationException>()
        }
    }

    context("Polyline validation") {
        // Note: EntityV1.Polyline.init enforces points.size >= 2, so invalid polylines
        // cannot be constructed directly. Tests use validateSafe() with crafted JSON.

        test("Polyline with 1 point via JSON returns Failure") {
            val validDrawing =
                Drawing2D(
                    id = "d1",
                    name = "Test",
                    page = Page(width = 1000.0, height = 800.0),
                    entities =
                        listOf(
                            EntityV1.Polyline(
                                id = "p1",
                                layer = null,
                                points = listOf(Point2D(0.0, 0.0), Point2D(1.0, 1.0)),
                                style = LineStyle("#000", 1.0, null),
                            ),
                        ),
                )

            val validJson = Json.encodeToString(validDrawing)
            // Remove one point from the two-point array, leaving only one point
            val invalidJson =
                validJson.replace(
                    "[{\"x\":0.0,\"y\":0.0},{\"x\":1.0,\"y\":1.0}]",
                    "[{\"x\":0.0,\"y\":0.0}]",
                )

            val result = validator.validateSafe(invalidJson)

            result.isFailure shouldBe true
            result.exceptionOrNull().shouldBeInstanceOf<ValidationException>()
        }

        test("Polyline with 0 points via JSON returns Failure") {
            val validDrawing =
                Drawing2D(
                    id = "d1",
                    name = "Test",
                    page = Page(width = 1000.0, height = 800.0),
                    entities =
                        listOf(
                            EntityV1.Polyline(
                                id = "p1",
                                layer = null,
                                points = listOf(Point2D(0.0, 0.0), Point2D(1.0, 1.0)),
                                style = LineStyle("#000", 1.0, null),
                            ),
                        ),
                )

            val validJson = Json.encodeToString(validDrawing)
            val invalidJson =
                validJson.replace(
                    "[{\"x\":0.0,\"y\":0.0},{\"x\":1.0,\"y\":1.0}]",
                    "[]",
                )

            val result = validator.validateSafe(invalidJson)

            result.isFailure shouldBe true
            result.exceptionOrNull().shouldBeInstanceOf<ValidationException>()
        }
    }

    context("Annotation validation") {
        test("AC: Annotation with non-existent targetId returns BrokenReference violation") {
            val drawing =
                Drawing2D(
                    id = "d1",
                    name = "Test",
                    page = Page(width = 1000.0, height = 800.0),
                    entities = emptyList(),
                    annotations =
                        listOf(
                            AnnotationV1.Dimension(
                                id = "dim1",
                                // entity-123 does not exist in entities list
                                targetId = "entity-123",
                                value = 100.0,
                                units = Units.MM,
                                position = Point2D(50.0, 50.0),
                            ),
                        ),
                )

            val violations = validator.validate(drawing)

            violations shouldHaveSize 1
            violations[0].shouldBeInstanceOf<Violation.BrokenReference>()
            violations[0].path shouldBe "drawing.annotations[0].targetId"
            (violations[0] as Violation.BrokenReference).referenceId shouldBe "entity-123"
            (violations[0] as Violation.BrokenReference).targetType shouldBe "Entity"
        }

        test("Annotation with null targetId is valid") {
            val drawing =
                Drawing2D(
                    id = "d1",
                    name = "Test",
                    page = Page(width = 1000.0, height = 800.0),
                    annotations =
                        listOf(
                            AnnotationV1.Text(
                                id = "text1",
                                // null targetId is allowed — no reference check performed
                                targetId = null,
                                position = Point2D(10.0, 10.0),
                                content = "Hello",
                                fontSize = 12.0,
                                rotation = 0.0,
                            ),
                        ),
                )

            val violations = validator.validate(drawing)

            violations.shouldBeEmpty()
        }

        test("Annotation with existing targetId is valid") {
            val drawing =
                Drawing2D(
                    id = "d1",
                    name = "Test",
                    page = Page(width = 1000.0, height = 800.0),
                    entities =
                        listOf(
                            EntityV1.Line(
                                id = "line1",
                                layer = null,
                                start = Point2D(0.0, 0.0),
                                end = Point2D(100.0, 100.0),
                                style = LineStyle("#000", 1.0, null),
                            ),
                        ),
                    annotations =
                        listOf(
                            AnnotationV1.Tag(
                                id = "tag1",
                                // line1 exists in the entities list above
                                targetId = "line1",
                                label = "Wall",
                                category = null,
                            ),
                        ),
                )

            val violations = validator.validate(drawing)

            violations.shouldBeEmpty()
        }

        test("Blank annotation ID returns InvalidValue violation") {
            val drawing =
                Drawing2D(
                    id = "d1",
                    name = "Test",
                    page = Page(width = 1000.0, height = 800.0),
                    annotations =
                        listOf(
                            AnnotationV1.Text(
                                // Blank ID
                                id = "",
                                targetId = null,
                                position = Point2D(0.0, 0.0),
                                content = "Hello",
                                fontSize = 12.0,
                                rotation = 0.0,
                            ),
                        ),
                )

            val violations = validator.validate(drawing)

            violations shouldHaveSize 1
            violations[0].shouldBeInstanceOf<Violation.InvalidValue>()
            violations[0].path shouldBe "drawing.annotations[0]"
            (violations[0] as Violation.InvalidValue).fieldName shouldBe "id"
        }
    }

    context("validateSafe - JSON parsing") {
        test("AC: validateSafe with invalid JSON returns Failure without throwing") {
            val badJson = "{invalid json"

            val result = validator.validateSafe(badJson)

            result.isFailure shouldBe true
            val exception = result.exceptionOrNull()
            exception.shouldBeInstanceOf<ValidationException>()
            exception.message shouldContain "Invalid JSON"
        }

        test("validateSafe with valid JSON and valid drawing returns Success") {
            val drawing =
                Drawing2D(
                    id = "d1",
                    name = "Test",
                    page = Page(width = 1000.0, height = 800.0),
                )

            val json = Json.encodeToString(drawing)
            val result = validator.validateSafe(json)

            result.isSuccess shouldBe true
            result.getOrNull()!!.id shouldBe "d1"
        }

        test("validateSafe with valid JSON but invalid drawing (schemaVersion=2) returns Failure") {
            val drawing =
                Drawing2D(
                    schemaVersion = 2,
                    id = "d1",
                    name = "Test",
                    page = Page(width = 1000.0, height = 800.0),
                )

            val json = Json.encodeToString(drawing)
            val result = validator.validateSafe(json)

            result.isFailure shouldBe true
            val exception = result.exceptionOrNull()
            exception.shouldBeInstanceOf<ValidationException>()
            exception.violations shouldHaveSize 1
        }

        test("validateSafe returns Success even when no violations present") {
            val drawing =
                Drawing2D(
                    id = "d1",
                    name = "Test",
                    page = Page(width = 1000.0, height = 800.0),
                )

            val json = Json.encodeToString(drawing)
            val result = validator.validateSafe(json)

            // Should succeed when there are zero violations (no ERRORs block success)
            result.isSuccess shouldBe true
        }
    }
})
