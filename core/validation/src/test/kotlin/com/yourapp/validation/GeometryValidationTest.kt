package com.yourapp.validation

import com.yourapp.drawing2d.model.Drawing2D
import com.yourapp.drawing2d.model.EntityV1
import com.yourapp.drawing2d.model.LineStyle
import com.yourapp.drawing2d.model.Page
import com.yourapp.drawing2d.model.Point2D
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class GeometryValidationTest : FunSpec({

    val validator = DrawingValidator()

    context("Line coordinate validation") {
        test("AC: Line with start = Point2D(NaN, 0.0) returns ERROR violation") {
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
                                start = Point2D(Double.NaN, 0.0),
                                end = Point2D(100.0, 100.0),
                                style = LineStyle("#000", 1.0, null),
                            ),
                        ),
                )

            val violations = validator.validate(drawing)

            violations.any { it.severity == Severity.ERROR } shouldBe true
            violations.any { it.message.contains("must be finite") } shouldBe true
        }

        test("Line with end.x = Infinity returns ERROR violation") {
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
                                end = Point2D(Double.POSITIVE_INFINITY, 100.0),
                                style = LineStyle("#000", 1.0, null),
                            ),
                        ),
                )

            val violations = validator.validate(drawing)

            violations.any { v ->
                v.severity == Severity.ERROR &&
                    v.message.contains("must be finite")
            } shouldBe true
        }

        test("Line with all coordinates finite is valid") {
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
                )

            val violations = validator.validate(drawing)

            violations.shouldBeEmpty()
        }
    }

    context("Degenerate line detection") {
        test("AC: Line with identical start/end points returns WARNING violation") {
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
                                start = Point2D(50.0, 50.0),
                                end = Point2D(50.0, 50.0),
                                style = LineStyle("#000", 1.0, null),
                            ),
                        ),
                )

            val violations = validator.validate(drawing)

            violations shouldHaveSize 1
            violations[0].severity shouldBe Severity.WARNING
            violations[0].message shouldContain "zero length"
        }

        test("Line with length 1e-9 (below threshold) returns WARNING") {
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
                                end = Point2D(1e-9, 0.0),
                                style = LineStyle("#000", 1.0, null),
                            ),
                        ),
                )

            val violations = validator.validate(drawing)

            violations.any { it.severity == Severity.WARNING } shouldBe true
            violations.any { it.message.contains("zero length") } shouldBe true
        }

        test("Line with length 1e-5 (above threshold) has no WARNING") {
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
                                end = Point2D(1e-5, 0.0),
                                style = LineStyle("#000", 1.0, null),
                            ),
                        ),
                )

            val violations = validator.validate(drawing)

            violations.shouldBeEmpty()
        }
    }

    context("Circle coordinate validation") {
        test("AC: Circle with radius = Infinity via JSON returns ERROR violation") {
            // Circle init block enforces radius.isFinite(), so we use
            // validateSafe() with crafted JSON to exercise the error path.
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
            val invalidJson = validJson.replace("\"radius\":25.0", "\"radius\":Infinity")

            val result = validator.validateSafe(invalidJson)

            result.isFailure shouldBe true
            val exception = result.exceptionOrNull()
            exception.shouldBeInstanceOf<ValidationException>()
        }

        test("Circle with center.x = NaN returns ERROR violation") {
            val drawing =
                Drawing2D(
                    id = "d1",
                    name = "Test",
                    page = Page(width = 1000.0, height = 800.0),
                    entities =
                        listOf(
                            EntityV1.Circle(
                                id = "c1",
                                layer = null,
                                center = Point2D(Double.NaN, 50.0),
                                radius = 25.0,
                                style = LineStyle("#000", 1.0, null),
                            ),
                        ),
                )

            val violations = validator.validate(drawing)

            violations.any { v ->
                v.severity == Severity.ERROR &&
                    v.message.contains("must be finite")
            } shouldBe true
        }
    }

    context("Polyline coordinate validation") {
        test("Polyline with point containing NaN returns ERROR") {
            val drawing =
                Drawing2D(
                    id = "d1",
                    name = "Test",
                    page = Page(width = 1000.0, height = 800.0),
                    entities =
                        listOf(
                            EntityV1.Polyline(
                                id = "p1",
                                layer = null,
                                points =
                                    listOf(
                                        Point2D(0.0, 0.0),
                                        Point2D(Double.NaN, 50.0),
                                        Point2D(100.0, 100.0),
                                    ),
                                style = LineStyle("#000", 1.0, null),
                            ),
                        ),
                )

            val violations = validator.validate(drawing)

            violations.any { it.severity == Severity.ERROR } shouldBe true
            violations.any { it.path.contains("points[1]") } shouldBe true
        }

        test("Polyline with all finite coordinates is valid") {
            val drawing =
                Drawing2D(
                    id = "d1",
                    name = "Test",
                    page = Page(width = 1000.0, height = 800.0),
                    entities =
                        listOf(
                            EntityV1.Polyline(
                                id = "p1",
                                layer = null,
                                points =
                                    listOf(
                                        Point2D(0.0, 0.0),
                                        Point2D(50.0, 50.0),
                                        Point2D(100.0, 100.0),
                                    ),
                                style = LineStyle("#000", 1.0, null),
                            ),
                        ),
                )

            val violations = validator.validate(drawing)

            violations.shouldBeEmpty()
        }
    }

    context("Arc validation") {
        test("Arc with startAngle = NaN via JSON returns ERROR") {
            // Arc init block enforces startAngle.isFinite(), so we use
            // validateSafe() with crafted JSON to exercise the error path.
            val validDrawing =
                Drawing2D(
                    id = "d1",
                    name = "Test",
                    page = Page(width = 1000.0, height = 800.0),
                    entities =
                        listOf(
                            EntityV1.Arc(
                                id = "a1",
                                layer = null,
                                center = Point2D(50.0, 50.0),
                                radius = 25.0,
                                startAngle = 0.0,
                                endAngle = 90.0,
                                style = LineStyle("#000", 1.0, null),
                            ),
                        ),
                )

            val validJson = Json.encodeToString(validDrawing)
            val invalidJson = validJson.replace("\"startAngle\":0.0", "\"startAngle\":NaN")

            val result = validator.validateSafe(invalidJson)

            result.isFailure shouldBe true
            val exception = result.exceptionOrNull()
            exception.shouldBeInstanceOf<ValidationException>()
        }

        test("Arc with endAngle = Infinity via JSON returns ERROR") {
            // Arc init block enforces endAngle.isFinite(), so we use
            // validateSafe() with crafted JSON to exercise the error path.
            val validDrawing =
                Drawing2D(
                    id = "d1",
                    name = "Test",
                    page = Page(width = 1000.0, height = 800.0),
                    entities =
                        listOf(
                            EntityV1.Arc(
                                id = "a1",
                                layer = null,
                                center = Point2D(50.0, 50.0),
                                radius = 25.0,
                                startAngle = 0.0,
                                endAngle = 90.0,
                                style = LineStyle("#000", 1.0, null),
                            ),
                        ),
                )

            val validJson = Json.encodeToString(validDrawing)
            val invalidJson = validJson.replace("\"endAngle\":90.0", "\"endAngle\":Infinity")

            val result = validator.validateSafe(invalidJson)

            result.isFailure shouldBe true
            val exception = result.exceptionOrNull()
            exception.shouldBeInstanceOf<ValidationException>()
        }

        test("Arc with center.x = NaN returns ERROR violation") {
            val drawing =
                Drawing2D(
                    id = "d1",
                    name = "Test",
                    page = Page(width = 1000.0, height = 800.0),
                    entities =
                        listOf(
                            EntityV1.Arc(
                                id = "a1",
                                layer = null,
                                center = Point2D(Double.NaN, 50.0),
                                radius = 25.0,
                                startAngle = 0.0,
                                endAngle = 90.0,
                                style = LineStyle("#000", 1.0, null),
                            ),
                        ),
                )

            val violations = validator.validate(drawing)

            violations.any { v ->
                v.severity == Severity.ERROR &&
                    v.path.contains("center.x") &&
                    v.message.contains("must be finite")
            } shouldBe true
        }

        test("Arc with all finite values is valid") {
            val drawing =
                Drawing2D(
                    id = "d1",
                    name = "Test",
                    page = Page(width = 1000.0, height = 800.0),
                    entities =
                        listOf(
                            EntityV1.Arc(
                                id = "a1",
                                layer = null,
                                center = Point2D(50.0, 50.0),
                                radius = 25.0,
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

    context("Multiple validation errors") {
        test("Entity with multiple coordinate issues returns multiple violations") {
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
                                start = Point2D(Double.NaN, Double.POSITIVE_INFINITY),
                                end = Point2D(Double.NEGATIVE_INFINITY, 100.0),
                                style = LineStyle("#000", 1.0, null),
                            ),
                        ),
                )

            val violations = validator.validate(drawing)

            // Should have 3 ERROR violations (NaN, +Inf, -Inf)
            violations.filter { it.severity == Severity.ERROR }.size shouldBe 3
        }
    }
})
