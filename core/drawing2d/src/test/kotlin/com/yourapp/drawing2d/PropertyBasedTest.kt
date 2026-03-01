package com.yourapp.drawing2d

import com.yourapp.drawing2d.model.AnnotationV1
import com.yourapp.drawing2d.model.Drawing2D
import com.yourapp.drawing2d.model.EntityV1
import com.yourapp.drawing2d.model.Layer
import com.yourapp.drawing2d.model.LineStyle
import com.yourapp.drawing2d.model.Page
import com.yourapp.drawing2d.model.Point2D
import com.yourapp.drawing2d.model.Units
import com.yourapp.drawing2d.model.Vector2D
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.MessageDigest

class PropertyBasedTest : FunSpec({

    context("Serialization determinism") {

        test("AC: toJsonStable() SHA256 is deterministic (100 calls)") {
            val drawing =
                Drawing2D(
                    id = "test",
                    name = "Determinism Test",
                    page = Page(width = 1000.0, height = 800.0),
                    entities =
                        listOf(
                            EntityV1.Line(
                                id = "line1",
                                layer = null,
                                start = Point2D(0.0, 0.0),
                                end = Point2D(100.0, 100.0),
                                style = LineStyle("#000000", 1.0),
                            ),
                        ),
                )

            val hashes =
                (1..100).map {
                    drawing.toJsonStable().sha256()
                }

            // All hashes should be identical
            hashes.distinct().size shouldBe 1
        }

        test("AC: Round-trip Drawing2D → JSON → Drawing2D preserves data") {
            val original =
                Drawing2D(
                    id = "roundtrip",
                    name = "Round Trip Test",
                    page = Page(width = 297.0, height = 210.0, units = Units.MM),
                    layers =
                        listOf(
                            Layer(id = "l1", name = "Layer 1", visible = true),
                            Layer(id = "l2", name = "Layer 2", visible = false),
                        ),
                    entities =
                        listOf(
                            EntityV1.Circle(
                                id = "c1",
                                layer = "l1",
                                center = Point2D(50.0, 50.0),
                                radius = 25.0,
                                style = LineStyle("#FF0000", 2.0),
                            ),
                            EntityV1.Line(
                                id = "l1-line",
                                layer = null,
                                start = Point2D(0.0, 0.0),
                                end = Point2D(10.0, 10.0),
                                style = LineStyle("#000000", 1.0),
                            ),
                        ),
                    annotations =
                        listOf(
                            AnnotationV1.Tag(
                                id = "t1",
                                targetId = "c1",
                                label = "Important",
                                category = "structural",
                            ),
                        ),
                    metadata = mapOf("author" to "Test", "version" to "1.0"),
                )

            val json = Json.encodeToString(Drawing2D.serializer(), original)
            val deserialized = Json.decodeFromString(Drawing2D.serializer(), json)

            deserialized shouldBe original
        }

        test("AC: Entity order doesn't affect toJsonStable() hash") {
            val entity1 =
                EntityV1.Line(
                    id = "a",
                    layer = null,
                    start = Point2D(0.0, 0.0),
                    end = Point2D(10.0, 10.0),
                    style = LineStyle("#000000", 1.0),
                )
            val entity2 =
                EntityV1.Line(
                    id = "b",
                    layer = null,
                    start = Point2D(20.0, 20.0),
                    end = Point2D(30.0, 30.0),
                    style = LineStyle("#000000", 1.0),
                )

            val drawing1 =
                Drawing2D(
                    id = "test",
                    name = "Test",
                    page = Page(width = 1000.0, height = 800.0),
                    entities = listOf(entity1, entity2),
                )

            val drawing2 =
                Drawing2D(
                    id = "test",
                    name = "Test",
                    page = Page(width = 1000.0, height = 800.0),
                    entities = listOf(entity2, entity1),
                )

            val hash1 = drawing1.toJsonStable().sha256()
            val hash2 = drawing2.toJsonStable().sha256()

            hash1 shouldBe hash2
        }
    }

    context("Serialization determinism – extended") {

        test("AC: Annotation order doesn't affect toJsonStable() hash") {
            val ann1 =
                AnnotationV1.Tag(
                    id = "a-tag",
                    targetId = "e1",
                    label = "First",
                )
            val ann2 =
                AnnotationV1.Tag(
                    id = "b-tag",
                    targetId = "e1",
                    label = "Second",
                )

            val base =
                Drawing2D(
                    id = "test",
                    name = "Test",
                    page = Page(width = 100.0, height = 100.0),
                    entities =
                        listOf(
                            EntityV1.Line(
                                id = "e1",
                                start = Point2D(0.0, 0.0),
                                end = Point2D(1.0, 1.0),
                                style = LineStyle("#000", 1.0),
                            ),
                        ),
                )

            val d1 = base.copy(annotations = listOf(ann1, ann2))
            val d2 = base.copy(annotations = listOf(ann2, ann1))

            d1.toJsonStable().sha256() shouldBe d2.toJsonStable().sha256()
        }

        test("AC: Layer order doesn't affect toJsonStable() hash") {
            val l1 = Layer(id = "alpha", name = "Alpha")
            val l2 = Layer(id = "beta", name = "Beta")

            val d1 =
                Drawing2D(
                    id = "test",
                    name = "Test",
                    page = Page(width = 100.0, height = 100.0),
                    layers = listOf(l1, l2),
                )
            val d2 =
                Drawing2D(
                    id = "test",
                    name = "Test",
                    page = Page(width = 100.0, height = 100.0),
                    layers = listOf(l2, l1),
                )

            d1.toJsonStable().sha256() shouldBe d2.toJsonStable().sha256()
        }

        test("AC: Metadata key order doesn't affect toJsonStable() hash") {
            val d1 =
                Drawing2D(
                    id = "test",
                    name = "Test",
                    page = Page(width = 100.0, height = 100.0),
                    metadata = linkedMapOf("zebra" to "z", "alpha" to "a"),
                )
            val d2 =
                Drawing2D(
                    id = "test",
                    name = "Test",
                    page = Page(width = 100.0, height = 100.0),
                    metadata = linkedMapOf("alpha" to "a", "zebra" to "z"),
                )

            d1.toJsonStable().sha256() shouldBe d2.toJsonStable().sha256()
        }

        test("AC: Complex drawing with all types produces deterministic hash") {
            val drawing =
                Drawing2D(
                    id = "complex",
                    name = "Complex Determinism",
                    page = Page(width = 2000.0, height = 1500.0),
                    layers =
                        listOf(
                            Layer("l1", "Layer 1"),
                            Layer("l2", "Layer 2", visible = false),
                        ),
                    entities =
                        listOf(
                            EntityV1.Line(
                                id = "e1",
                                layer = "l1",
                                start = Point2D(0.0, 0.0),
                                end = Point2D(100.0, 100.0),
                                style = LineStyle("#000000", 1.0),
                            ),
                            EntityV1.Circle(
                                id = "e2",
                                layer = "l2",
                                center = Point2D(50.0, 50.0),
                                radius = 25.0,
                                style = LineStyle("#FF0000", 2.0, listOf(5.0, 3.0)),
                            ),
                            EntityV1.Polyline(
                                id = "e3",
                                points =
                                    listOf(
                                        Point2D(0.0, 0.0),
                                        Point2D(50.0, 50.0),
                                        Point2D(100.0, 0.0),
                                    ),
                                closed = true,
                                style = LineStyle("#0000FF", 1.5),
                            ),
                            EntityV1.Arc(
                                id = "e4",
                                center = Point2D(200.0, 200.0),
                                radius = 50.0,
                                startAngle = 0.0,
                                endAngle = 90.0,
                                style = LineStyle("#00FF00", 1.0),
                            ),
                        ),
                    annotations =
                        listOf(
                            AnnotationV1.Text(
                                id = "a1",
                                targetId = null,
                                position = Point2D(10.0, 10.0),
                                content = "Label",
                                fontSize = 12.0,
                                rotation = 0.0,
                            ),
                            AnnotationV1.Dimension(
                                id = "a2",
                                targetId = "e1",
                                value = 141.42,
                                units = Units.MM,
                                position = Point2D(50.0, 50.0),
                            ),
                        ),
                    metadata = mapOf("author" to "Test", "version" to "1.0"),
                )

            val hashes = (1..50).map { drawing.toJsonStable().sha256() }
            hashes.distinct().size shouldBe 1
        }
    }

    context("Primitive serialization round-trips") {

        test("AC: Vector2D round-trips through JSON") {
            val original = Vector2D(3.14159, 2.71828)
            val json = Json.encodeToString(original)
            val deserialized = Json.decodeFromString<Vector2D>(json)

            // Coordinates are rounded to 4 decimals by Vector2DSerializer
            deserialized shouldBe Vector2D(3.1416, 2.7183)
        }

        test("AC: Point2D round-trips through JSON") {
            val original = Point2D(1.23456, 7.89012)
            val json = Json.encodeToString(original)
            val deserialized = Json.decodeFromString<Point2D>(json)

            deserialized shouldBe Point2D(1.2346, 7.8901)
        }

        test("AC: LineStyle with dashPattern round-trips through JSON") {
            val original = LineStyle("#FF0000", 2.5, listOf(5.0, 3.0, 1.0))
            val json = Json.encodeToString(original)
            val deserialized = Json.decodeFromString<LineStyle>(json)

            deserialized shouldBe original
        }
    }
})

private fun String.sha256(): String {
    val bytes = MessageDigest.getInstance("SHA-256").digest(this.toByteArray())
    return bytes.joinToString("") { "%02x".format(it) }
}
