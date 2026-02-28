package com.yourapp.drawing2d.model

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import java.security.MessageDigest

class Drawing2DTest : FunSpec({

    context("Drawing2D – basic construction") {

        test("default values set correctly") {
            val drawing =
                Drawing2D(
                    id = "d1",
                    name = "Test Drawing",
                )

            drawing.schemaVersion shouldBe 1
            drawing.entities shouldBe emptyList()
            drawing.annotations shouldBe emptyList()
            drawing.metadata shouldBe emptyMap()
        }

        test("with entities and annotations") {
            val entity =
                EntityV1.Line(
                    id = "e1",
                    layer = null,
                    start = Point2D(0.0, 0.0),
                    end = Point2D(10.0, 10.0),
                    style = LineStyle(color = "#000000", width = 1.0, dashPattern = null),
                )

            val annotation =
                AnnotationV1.Text(
                    id = "a1",
                    targetId = "e1",
                    position = Point2D(5.0, 5.0),
                    content = "Test",
                    fontSize = 12.0,
                    rotation = 0.0,
                )

            val drawing =
                Drawing2D(
                    id = "d1",
                    name = "Test",
                    entities = listOf(entity),
                    annotations = listOf(annotation),
                )

            drawing.entities.size shouldBe 1
            drawing.annotations.size shouldBe 1
        }
    }

    context("toJsonStable – determinism") {

        test("AC: calling toJsonStable() twice produces identical strings") {
            val drawing =
                Drawing2D(
                    id = "d1",
                    name = "Test Drawing",
                    entities =
                        listOf(
                            EntityV1.Line(
                                "e1",
                                null,
                                Point2D(0.0, 0.0),
                                Point2D(1.0, 1.0),
                                LineStyle("#000", 1.0, null),
                            ),
                        ),
                )

            val json1 = drawing.toJsonStable()
            val json2 = drawing.toJsonStable()

            json1 shouldBe json2
        }

        test("AC: SHA256 hash is identical across multiple calls") {
            val drawing =
                Drawing2D(
                    id = "d1",
                    name = "Test",
                    entities =
                        listOf(
                            EntityV1.Line(
                                "e1",
                                null,
                                Point2D(0.0, 0.0),
                                Point2D(1.0, 1.0),
                                LineStyle("#000", 1.0, null),
                            ),
                        ),
                )

            val hash1 = drawing.toJsonStable().sha256()
            val hash2 = drawing.toJsonStable().sha256()
            val hash3 = drawing.toJsonStable().sha256()

            hash1 shouldBe hash2
            hash2 shouldBe hash3
        }

        test("AC: entity order doesn't affect hash (collections sorted)") {
            val entity1 =
                EntityV1.Line(
                    "e1",
                    null,
                    Point2D(0.0, 0.0),
                    Point2D(1.0, 1.0),
                    LineStyle("#000", 1.0, null),
                )
            val entity2 =
                EntityV1.Line(
                    "e2",
                    null,
                    Point2D(2.0, 2.0),
                    Point2D(3.0, 3.0),
                    LineStyle("#000", 1.0, null),
                )

            val drawing1 =
                Drawing2D(
                    id = "d1",
                    name = "Test",
                    entities = listOf(entity1, entity2),
                )

            val drawing2 =
                Drawing2D(
                    id = "d1",
                    name = "Test",
                    entities = listOf(entity2, entity1),
                )

            val hash1 = drawing1.toJsonStable().sha256()
            val hash2 = drawing2.toJsonStable().sha256()

            hash1 shouldBe hash2
        }
    }

    context("toJsonStable – serialization round-trip") {

        test("AC: round-trip preserves all data") {
            val original =
                Drawing2D(
                    id = "d1",
                    name = "Test Drawing",
                    entities =
                        listOf(
                            EntityV1.Circle(
                                id = "e1",
                                layer = "layer1",
                                center = Point2D(10.0, 20.0),
                                radius = 5.0,
                                style = LineStyle("#FF0000", 2.0, null),
                            ),
                        ),
                    annotations =
                        listOf(
                            AnnotationV1.Tag(
                                id = "a1",
                                targetId = "e1",
                                label = "Important",
                                category = "marker",
                            ),
                        ),
                    metadata = mapOf("author" to "John Doe", "version" to "1.0"),
                )

            val json = original.toJsonStable()
            val deserialized = Json.decodeFromString<Drawing2D>(json)

            deserialized shouldBe original
        }
    }
})

private fun String.sha256(): String {
    val bytes = MessageDigest.getInstance("SHA-256").digest(this.toByteArray())
    return bytes.joinToString("") { "%02x".format(it) }
}
