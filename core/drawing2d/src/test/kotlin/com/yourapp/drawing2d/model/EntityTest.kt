package com.yourapp.drawing2d.model

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

// ── Shared fixtures ───────────────────────────────────────────────────────────

private val origin = Point2D(0.0, 0.0)
private val p1 = Point2D(1.0, 2.0)
private val p2 = Point2D(3.0, 4.0)
private val p3 = Point2D(5.0, 6.0)
private val solidBlack = LineStyle(color = "#000000", width = 1.0)
private val dashedRed = LineStyle(color = "#FF0000", width = 2.5, dashPattern = listOf(4.0, 2.0))

private val json = Json { classDiscriminator = "type" }

// ── Tests ─────────────────────────────────────────────────────────────────────

class EntityTest : FunSpec({

    // ── LineStyle ─────────────────────────────────────────────────────────────

    context("LineStyle") {

        test("solid line has null dashPattern by default") {
            solidBlack.dashPattern.shouldBeNull()
        }

        test("dashed line stores dashPattern") {
            dashedRed.dashPattern shouldBe listOf(4.0, 2.0)
        }

        test("data class equality holds") {
            LineStyle("#000000", 1.0) shouldBe LineStyle("#000000", 1.0)
        }

        test("serialization round-trip preserves dashPattern") {
            val encoded = json.encodeToString(dashedRed)
            val decoded = json.decodeFromString<LineStyle>(encoded)
            decoded shouldBe dashedRed
        }

        // ── Immutability-exploit regression ───────────────────────────────────

        test("SECURITY: mutating the caller's MutableList after construction does not corrupt LineStyle.dashPattern") {
            // Arrange — hand a MutableList as dashPattern.
            val mutableDash = mutableListOf(4.0, 2.0)
            val style = LineStyle(color = "#FF0000", width = 1.5, dashPattern = mutableDash)

            // Sanity-check: pattern was stored correctly.
            style.dashPattern shouldBe listOf(4.0, 2.0)

            // Act — attacker clears the original list.
            mutableDash.clear()

            // Assert — internal list is a separate copy; dashPattern is unchanged.
            style.dashPattern shouldBe listOf(4.0, 2.0)
            style.dashPattern!!.size shouldBe 2
        }
    }

    // ── EntityV1.Line ─────────────────────────────────────────────────────────

    context("EntityV1.Line") {

        test("constructs with required fields") {
            val line = EntityV1.Line(id = "l1", start = origin, end = p1, style = solidBlack)
            line.id shouldBe "l1"
            line.layer.shouldBeNull()
            line.start shouldBe origin
            line.end shouldBe p1
            line.style shouldBe solidBlack
        }

        test("layer is assignable") {
            val line = EntityV1.Line(id = "l2", layer = "walls", start = origin, end = p2, style = solidBlack)
            line.layer shouldBe "walls"
        }

        test("can be assigned to EntityV1 reference") {
            val entity: EntityV1 = EntityV1.Line(id = "l3", start = origin, end = p1, style = solidBlack)
            (entity is EntityV1.Line).shouldBeTrue()
        }

        test("serialization round-trip preserves all fields") {
            val line = EntityV1.Line(id = "l4", layer = "doors", start = p1, end = p2, style = dashedRed)
            val encoded = json.encodeToString<EntityV1>(line)
            val decoded = json.decodeFromString<EntityV1>(encoded)
            decoded shouldBe line
        }

        test("JSON discriminator is 'line'") {
            val encoded =
                json.encodeToString<EntityV1>(
                    EntityV1.Line(id = "l5", start = origin, end = p1, style = solidBlack),
                )
            encoded shouldContain "\"type\":\"line\""
        }
    }

    // ── EntityV1.Circle ───────────────────────────────────────────────────────

    context("EntityV1.Circle") {

        test("AC: radius = -5.0 throws IllegalArgumentException") {
            val ex =
                shouldThrow<IllegalArgumentException> {
                    EntityV1.Circle(id = "c1", center = origin, radius = -5.0, style = solidBlack)
                }
            ex.message shouldContain "-5.0"
        }

        test("AC: radius = 0.0 throws IllegalArgumentException") {
            shouldThrow<IllegalArgumentException> {
                EntityV1.Circle(id = "c2", center = origin, radius = 0.0, style = solidBlack)
            }
        }

        test("AC: radius = Double.POSITIVE_INFINITY throws IllegalArgumentException") {
            val ex =
                shouldThrow<IllegalArgumentException> {
                    EntityV1.Circle(id = "c3", center = origin, radius = Double.POSITIVE_INFINITY, style = solidBlack)
                }
            ex.message shouldContain "Infinity"
        }

        test("radius = Double.NEGATIVE_INFINITY throws IllegalArgumentException") {
            shouldThrow<IllegalArgumentException> {
                EntityV1.Circle(id = "c4", center = origin, radius = Double.NEGATIVE_INFINITY, style = solidBlack)
            }
        }

        test("radius = Double.NaN throws IllegalArgumentException") {
            shouldThrow<IllegalArgumentException> {
                EntityV1.Circle(id = "c5", center = origin, radius = Double.NaN, style = solidBlack)
            }
        }

        test("valid circle constructs correctly") {
            val circle = EntityV1.Circle(id = "c6", center = p1, radius = 10.0, style = solidBlack)
            circle.radius shouldBe 10.0
            circle.center shouldBe p1
            circle.layer.shouldBeNull()
        }

        test("layer is assignable") {
            val circle = EntityV1.Circle(id = "c7", layer = "foundation", center = origin, radius = 5.0, style = solidBlack)
            circle.layer shouldBe "foundation"
        }

        test("serialization round-trip preserves all fields") {
            val circle = EntityV1.Circle(id = "c8", layer = "arcs", center = p2, radius = 7.5, style = dashedRed)
            val encoded = json.encodeToString<EntityV1>(circle)
            val decoded = json.decodeFromString<EntityV1>(encoded)
            decoded shouldBe circle
        }

        test("JSON discriminator is 'circle'") {
            val encoded =
                json.encodeToString<EntityV1>(
                    EntityV1.Circle(id = "c9", center = origin, radius = 1.0, style = solidBlack),
                )
            encoded shouldContain "\"type\":\"circle\""
        }
    }

    // ── EntityV1.Polyline ─────────────────────────────────────────────────────

    context("EntityV1.Polyline") {

        test("AC: 1 point throws IllegalArgumentException with correct message") {
            val ex =
                shouldThrow<IllegalArgumentException> {
                    EntityV1.Polyline(id = "pl1", points = listOf(origin), style = solidBlack)
                }
            ex.message shouldBe "Polyline must have at least 2 points"
        }

        test("empty list throws IllegalArgumentException") {
            val ex =
                shouldThrow<IllegalArgumentException> {
                    EntityV1.Polyline(id = "pl2", points = emptyList(), style = solidBlack)
                }
            ex.message shouldBe "Polyline must have at least 2 points"
        }

        test("2 points constructs successfully") {
            val poly = EntityV1.Polyline(id = "pl3", points = listOf(origin, p1), style = solidBlack)
            poly.points.size shouldBe 2
            poly.closed.shouldBeFalse()
        }

        test("3+ points constructs successfully") {
            val poly = EntityV1.Polyline(id = "pl4", points = listOf(origin, p1, p2, p3), style = solidBlack)
            poly.points.size shouldBe 4
        }

        test("closed flag defaults to false") {
            val poly = EntityV1.Polyline(id = "pl5", points = listOf(p1, p2), style = solidBlack)
            poly.closed.shouldBeFalse()
        }

        test("closed flag can be set to true") {
            val poly = EntityV1.Polyline(id = "pl6", points = listOf(p1, p2, p3), closed = true, style = solidBlack)
            poly.closed.shouldBeTrue()
        }

        test("layer is assignable") {
            val poly = EntityV1.Polyline(id = "pl7", layer = "outline", points = listOf(p1, p2), style = solidBlack)
            poly.layer shouldBe "outline"
        }

        test("serialization round-trip preserves all fields including closed=true") {
            val poly =
                EntityV1.Polyline(
                    id = "pl8",
                    layer = "roof",
                    points = listOf(origin, p1, p2),
                    closed = true,
                    style = dashedRed,
                )
            val encoded = json.encodeToString<EntityV1>(poly)
            val decoded = json.decodeFromString<EntityV1>(encoded)
            decoded shouldBe poly
        }

        test("JSON discriminator is 'polyline'") {
            val encoded =
                json.encodeToString<EntityV1>(
                    EntityV1.Polyline(id = "pl9", points = listOf(origin, p1), style = solidBlack),
                )
            encoded shouldContain "\"type\":\"polyline\""
        }

        // ── Immutability-exploit regression ───────────────────────────────────

        test("SECURITY: mutating the caller's MutableList after construction does not corrupt Polyline") {
            // Arrange — build a MutableList with two valid points and hand it to Polyline.
            val mutablePoints = mutableListOf(origin, p1, p2)
            val poly = EntityV1.Polyline(id = "exploit-pl1", points = mutablePoints, style = solidBlack)

            // Sanity-check: the polyline was constructed successfully with 3 points.
            poly.points.size shouldBe 3

            // Act — the attacker clears the original list AFTER construction.
            mutablePoints.clear()

            // Assert — the Polyline's internal list is independent; it still holds
            // its original 3 points, and the invariant (size >= 2) is unbroken.
            poly.points.size shouldBe 3
            poly.points shouldBe listOf(origin, p1, p2)
        }

        test("SECURITY: mutating caller's MutableList cannot produce a sub-2-point Polyline") {
            // Construct with the minimum valid size.
            val mutablePoints = mutableListOf(p1, p2)
            val poly = EntityV1.Polyline(id = "exploit-pl2", points = mutablePoints, style = solidBlack)

            // Act — strip the original list down to 0 elements.
            mutablePoints.clear()

            // Assert — internal snapshot is still exactly 2 points; invariant holds.
            poly.points.size shouldBe 2
            poly.points shouldBe listOf(p1, p2)
        }
    }

    // ── EntityV1.Arc ──────────────────────────────────────────────────────────

    context("EntityV1.Arc") {

        test("valid arc constructs correctly") {
            val arc =
                EntityV1.Arc(
                    id = "a1",
                    center = origin,
                    radius = 5.0,
                    startAngle = 0.0,
                    endAngle = 90.0,
                    style = solidBlack,
                )
            arc.radius shouldBe 5.0
            arc.startAngle shouldBe 0.0
            arc.endAngle shouldBe 90.0
        }

        test("negative radius throws IllegalArgumentException") {
            val ex =
                shouldThrow<IllegalArgumentException> {
                    EntityV1.Arc(id = "a2", center = origin, radius = -1.0, startAngle = 0.0, endAngle = 90.0, style = solidBlack)
                }
            ex.message shouldContain "-1.0"
        }

        test("zero radius throws IllegalArgumentException") {
            shouldThrow<IllegalArgumentException> {
                EntityV1.Arc(id = "a3", center = origin, radius = 0.0, startAngle = 0.0, endAngle = 90.0, style = solidBlack)
            }
        }

        test("infinite radius throws IllegalArgumentException") {
            shouldThrow<IllegalArgumentException> {
                EntityV1.Arc(
                    id = "a4",
                    center = origin,
                    radius = Double.POSITIVE_INFINITY,
                    startAngle = 0.0,
                    endAngle = 90.0,
                    style = solidBlack,
                )
            }
        }

        test("NaN radius throws IllegalArgumentException") {
            shouldThrow<IllegalArgumentException> {
                EntityV1.Arc(
                    id = "a5",
                    center = origin,
                    radius = Double.NaN,
                    startAngle = 0.0,
                    endAngle = 90.0,
                    style = solidBlack,
                )
            }
        }

        test("non-finite startAngle throws IllegalArgumentException") {
            shouldThrow<IllegalArgumentException> {
                EntityV1.Arc(
                    id = "a6",
                    center = origin,
                    radius = 5.0,
                    startAngle = Double.NaN,
                    endAngle = 90.0,
                    style = solidBlack,
                )
            }
        }

        test("non-finite endAngle throws IllegalArgumentException") {
            shouldThrow<IllegalArgumentException> {
                EntityV1.Arc(
                    id = "a7",
                    center = origin,
                    radius = 5.0,
                    startAngle = 0.0,
                    endAngle = Double.POSITIVE_INFINITY,
                    style = solidBlack,
                )
            }
        }

        test("layer is assignable") {
            val arc =
                EntityV1.Arc(
                    id = "a8",
                    layer = "curves",
                    center = origin,
                    radius = 3.0,
                    startAngle = 45.0,
                    endAngle = 180.0,
                    style = solidBlack,
                )
            arc.layer shouldBe "curves"
        }

        test("full-circle arc (0 to 360) is valid") {
            val arc =
                EntityV1.Arc(
                    id = "a9",
                    center = origin,
                    radius = 1.0,
                    startAngle = 0.0,
                    endAngle = 360.0,
                    style = solidBlack,
                )
            arc.endAngle shouldBe 360.0
        }

        test("serialization round-trip preserves all fields") {
            val arc =
                EntityV1.Arc(
                    id = "a10",
                    layer = "plans",
                    center = p1,
                    radius = 12.5,
                    startAngle = 30.0,
                    endAngle = 270.0,
                    style = dashedRed,
                )
            val encoded = json.encodeToString<EntityV1>(arc)
            val decoded = json.decodeFromString<EntityV1>(encoded)
            decoded shouldBe arc
        }

        test("JSON discriminator is 'arc'") {
            val encoded =
                json.encodeToString<EntityV1>(
                    EntityV1.Arc(id = "a11", center = origin, radius = 1.0, startAngle = 0.0, endAngle = 90.0, style = solidBlack),
                )
            encoded shouldContain "\"type\":\"arc\""
        }
    }

    // ── Polymorphic deserialization ───────────────────────────────────────────

    context("Polymorphic deserialization") {

        test("list of mixed entity types round-trips correctly") {
            val entities: List<EntityV1> =
                listOf(
                    EntityV1.Line(id = "l1", start = origin, end = p1, style = solidBlack),
                    EntityV1.Circle(id = "c1", center = p2, radius = 5.0, style = solidBlack),
                    EntityV1.Polyline(id = "pl1", points = listOf(origin, p1, p2), style = dashedRed),
                    EntityV1.Arc(id = "a1", center = origin, radius = 3.0, startAngle = 0.0, endAngle = 90.0, style = solidBlack),
                )
            val encoded = json.encodeToString(entities)
            val decoded = json.decodeFromString<List<EntityV1>>(encoded)
            decoded shouldBe entities
        }
    }
})
