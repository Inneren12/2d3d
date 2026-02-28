package com.yourapp.drawing2d.model

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
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
                    page = Page(width = 1000.0, height = 800.0),
                )

            drawing.schemaVersion shouldBe 1
            drawing.layers shouldBe emptyList()
            drawing.entities shouldBe emptyList()
            drawing.annotations shouldBe emptyList()
            drawing.metadata shouldBe emptyMap()

            // Sync field defaults
            drawing.syncId shouldBe null
            drawing.syncStatus shouldBe SyncStatus.LOCAL
            drawing.updatedAt shouldBe 0L
            drawing.version shouldBe 1
        }

        test("sync fields have correct defaults") {
            val drawing =
                Drawing2D(
                    id = "d1",
                    name = "Test",
                    page = Page(width = 1000.0, height = 800.0),
                )

            drawing.syncId shouldBe null
            drawing.syncStatus shouldBe SyncStatus.LOCAL
            drawing.updatedAt shouldBe 0L
            drawing.version shouldBe 1
        }

        test("sync fields can be set explicitly") {
            val drawing =
                Drawing2D(
                    id = "d1",
                    name = "Test",
                    page = Page(width = 1000.0, height = 800.0),
                    syncId = "sync-123",
                    syncStatus = SyncStatus.SYNCED,
                    updatedAt = 1234567890L,
                    version = 5,
                )

            drawing.syncId shouldBe "sync-123"
            drawing.syncStatus shouldBe SyncStatus.SYNCED
            drawing.updatedAt shouldBe 1234567890L
            drawing.version shouldBe 5
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
                    page = Page(width = 1000.0, height = 800.0),
                    entities = listOf(entity),
                    annotations = listOf(annotation),
                )

            drawing.entities.size shouldBe 1
            drawing.annotations.size shouldBe 1
        }
    }

    context("Page and Layer") {

        test("Page constructed with dimensions") {
            val page =
                Page(
                    width = 297.0, // A4 width in mm
                    height = 210.0, // A4 height in mm
                    units = Units.MM,
                )

            page.width shouldBe 297.0
            page.height shouldBe 210.0
            page.units shouldBe Units.MM
        }

        test("Layer constructed with id and name") {
            val layer =
                Layer(
                    id = "layer1",
                    name = "Structural Elements",
                    visible = true,
                )

            layer.id shouldBe "layer1"
            layer.name shouldBe "Structural Elements"
            layer.visible shouldBe true
        }

        test("Drawing2D with page and layers") {
            val page = Page(width = 1000.0, height = 800.0)
            val layer1 = Layer(id = "l1", name = "Layer 1")
            val layer2 = Layer(id = "l2", name = "Layer 2")

            val drawing =
                Drawing2D(
                    id = "d1",
                    name = "Test",
                    page = page,
                    layers = listOf(layer1, layer2),
                )

            drawing.page shouldBe page
            drawing.layers.size shouldBe 2
        }
    }

    context("toJsonStable – determinism") {

        test("AC: calling toJsonStable() twice produces identical strings") {
            val drawing =
                Drawing2D(
                    id = "d1",
                    name = "Test Drawing",
                    page = Page(width = 1000.0, height = 800.0),
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
                    page = Page(width = 1000.0, height = 800.0),
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
                    page = Page(width = 1000.0, height = 800.0),
                    entities = listOf(entity1, entity2),
                )

            val drawing2 =
                Drawing2D(
                    id = "d1",
                    name = "Test",
                    page = Page(width = 1000.0, height = 800.0),
                    entities = listOf(entity2, entity1),
                )

            val hash1 = drawing1.toJsonStable().sha256()
            val hash2 = drawing2.toJsonStable().sha256()

            hash1 shouldBe hash2
        }

        test("AC: metadata key order doesn't affect hash (map sorted)") {
            val drawing1 =
                Drawing2D(
                    id = "d1",
                    name = "Test",
                    page = Page(width = 1000.0, height = 800.0),
                    metadata = linkedMapOf("zebra" to "z", "alpha" to "a", "mid" to "m"),
                )

            val drawing2 =
                Drawing2D(
                    id = "d1",
                    name = "Test",
                    page = Page(width = 1000.0, height = 800.0),
                    metadata = linkedMapOf("alpha" to "a", "mid" to "m", "zebra" to "z"),
                )

            val hash1 = drawing1.toJsonStable().sha256()
            val hash2 = drawing2.toJsonStable().sha256()

            hash1 shouldBe hash2
        }

        test("AC: layer order doesn't affect hash (layers sorted)") {
            val layer1 = Layer(id = "l1", name = "Layer 1")
            val layer2 = Layer(id = "l2", name = "Layer 2")
            val page = Page(width = 1000.0, height = 800.0)

            val drawing1 =
                Drawing2D(
                    id = "d1",
                    name = "Test",
                    page = page,
                    layers = listOf(layer1, layer2),
                )

            val drawing2 =
                Drawing2D(
                    id = "d1",
                    name = "Test",
                    page = page,
                    layers = listOf(layer2, layer1), // Reversed
                )

            val hash1 = drawing1.toJsonStable().sha256()
            val hash2 = drawing2.toJsonStable().sha256()

            hash1 shouldBe hash2
        }
    }

    context("Page.units – enum type safety") {

        test("AC: Page.units is a Units enum, not a String") {
            val page = Page(width = 297.0, height = 210.0, units = Units.INCHES)
            page.units shouldBe Units.INCHES
        }

        test("AC: Units enum contains all expected values") {
            Units.values().toList().map { it.name } shouldContainExactly
                listOf("MM", "CM", "M", "INCHES", "FEET")
        }

        test("AC: Page default units is Units.MM") {
            val page = Page(width = 100.0, height = 100.0)
            page.units shouldBe Units.MM
        }
    }

    context("SyncStatus – enum type safety") {

        test("AC: SyncStatus enum contains all expected values") {
            SyncStatus.values().toList().map { it.name } shouldContainExactly
                listOf("LOCAL", "SYNCING", "SYNCED")
        }

        test("AC: Drawing2D default syncStatus is SyncStatus.LOCAL") {
            val drawing =
                Drawing2D(
                    id = "d1",
                    name = "Test",
                    page = Page(width = 100.0, height = 100.0),
                )
            drawing.syncStatus shouldBe SyncStatus.LOCAL
        }

        test("AC: syncStatus can be set to all enum values") {
            for (status in SyncStatus.values()) {
                val drawing =
                    Drawing2D(
                        id = "d1",
                        name = "Test",
                        page = Page(width = 100.0, height = 100.0),
                        syncStatus = status,
                    )
                drawing.syncStatus shouldBe status
            }
        }
    }

    context("toJsonStable – serialization round-trip") {

        test("AC: round-trip preserves all data") {
            val original =
                Drawing2D(
                    id = "d1",
                    name = "Test Drawing",
                    page = Page(width = 297.0, height = 210.0, units = Units.MM),
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
