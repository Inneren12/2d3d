package com.yourapp.drawing2d

import com.yourapp.drawing2d.model.AnnotationV1
import com.yourapp.drawing2d.model.Drawing2D
import com.yourapp.drawing2d.model.EntityV1
import com.yourapp.drawing2d.model.SyncStatus
import com.yourapp.drawing2d.model.Units
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeBlank
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * Golden fixture tests — validate that reference JSON files load
 * and produce the expected structures. These fixtures NEVER change
 * once committed, protecting against accidental serialization regressions.
 *
 * Note: Validation tests (using DrawingValidator) live in the
 * :core:validation module. This test focuses on serialization fidelity.
 */
class GoldenFixtureTest : FunSpec({

    val json = Json { ignoreUnknownKeys = true }

    context("Valid golden fixtures") {

        test("AC: valid_simple.json loads and has 1 line entity") {
            val drawing = json.decodeFromString<Drawing2D>(loadFixture("valid_simple.json"))

            drawing.id shouldBe "drawing-simple"
            drawing.name shouldBe "Simple Valid Drawing"
            drawing.schemaVersion shouldBe 1
            drawing.page.width shouldBe 1000.0
            drawing.page.height shouldBe 800.0
            drawing.page.units shouldBe Units.MM
            drawing.entities shouldHaveSize 1

            val line = drawing.entities[0] as EntityV1.Line
            line.id shouldBe "line1"
            line.start.x shouldBe 0.0
            line.end.x shouldBe 100.0
            inlineValidate(drawing).shouldBeEmpty()
        }

        test("AC: valid_complex.json loads with all entity and annotation types") {
            val drawing = json.decodeFromString<Drawing2D>(loadFixture("valid_complex.json"))

            drawing.id shouldBe "drawing-complex"
            drawing.entities shouldHaveSize 4
            drawing.annotations shouldHaveSize 4
            drawing.layers shouldHaveSize 1
            drawing.metadata.size shouldBe 2

            // Verify all entity types present
            drawing.entities.filterIsInstance<EntityV1.Line>() shouldHaveSize 1
            drawing.entities.filterIsInstance<EntityV1.Circle>() shouldHaveSize 1
            drawing.entities.filterIsInstance<EntityV1.Polyline>() shouldHaveSize 1
            drawing.entities.filterIsInstance<EntityV1.Arc>() shouldHaveSize 1

            // Verify all annotation types present
            drawing.annotations.filterIsInstance<AnnotationV1.Text>() shouldHaveSize 1
            drawing.annotations.filterIsInstance<AnnotationV1.Dimension>() shouldHaveSize 1
            drawing.annotations.filterIsInstance<AnnotationV1.Tag>() shouldHaveSize 1
            drawing.annotations.filterIsInstance<AnnotationV1.Group>() shouldHaveSize 1

            inlineValidate(drawing).shouldBeEmpty()
        }

        test("AC: valid_all_features.json loads with layers, metadata, and sync fields") {
            val drawing = json.decodeFromString<Drawing2D>(loadFixture("valid_all_features.json"))

            drawing.id shouldBe "drawing-features"
            drawing.layers shouldHaveSize 2
            drawing.entities shouldHaveSize 2
            drawing.annotations shouldHaveSize 1
            drawing.metadata["key"] shouldBe "value"
            drawing.syncId shouldBe "sync-123"
            drawing.syncStatus shouldBe SyncStatus.SYNCED
            drawing.updatedAt shouldBe 1234567890L
            drawing.version shouldBe 5

            // Verify dash pattern loaded
            val circle = drawing.entities.filterIsInstance<EntityV1.Circle>().first()
            circle.style.dashPattern shouldBe listOf(5.0, 3.0)

            inlineValidate(drawing).shouldBeEmpty()
        }

        test("AC: valid_large.json loads with multiple entities") {
            val drawing = json.decodeFromString<Drawing2D>(loadFixture("valid_large.json"))

            drawing.id shouldBe "drawing-large"
            drawing.entities shouldHaveSize 10
            drawing.entities.forEach { entity ->
                entity.id.shouldNotBeBlank()
            }
            inlineValidate(drawing).shouldBeEmpty()
        }

        test("AC: valid_empty.json loads with no entities") {
            val drawing = json.decodeFromString<Drawing2D>(loadFixture("valid_empty.json"))

            drawing.id shouldBe "drawing-empty"
            drawing.entities shouldHaveSize 0
            drawing.annotations shouldHaveSize 0
            inlineValidate(drawing).shouldBeEmpty()
        }
    }

    context("Invalid golden fixtures") {

        test("AC: invalid_negative_radius.json fails to construct (Circle init)") {
            try {
                json.decodeFromString<Drawing2D>(loadFixture("invalid_negative_radius.json"))
                error("Should have thrown due to negative radius")
            } catch (e: IllegalArgumentException) {
                // Expected: Circle init block rejects radius <= 0
                e.message shouldBe "Circle radius must be finite and > 0, but was -5.0"
            }
        }

        test("AC: invalid_broken_reference.json has annotation referencing non-existent entity") {
            val drawing =
                json.decodeFromString<Drawing2D>(
                    loadFixture("invalid_broken_reference.json"),
                )

            val entityIds = drawing.entities.map { it.id }.toSet()
            val brokenRefs =
                drawing.annotations.filter { ann ->
                    ann.targetId != null && ann.targetId !in entityIds
                }

            brokenRefs shouldHaveSize 1
            brokenRefs[0].targetId shouldBe "entity-does-not-exist"
        }

        test("AC: invalid_blank_id.json has entity with blank ID") {
            val drawing =
                json.decodeFromString<Drawing2D>(
                    loadFixture("invalid_blank_id.json"),
                )

            val blankIds = drawing.entities.filter { it.id.isBlank() }
            blankIds shouldHaveSize 1
        }

        test("AC: invalid_nan_coordinate.json fails to parse (string where Double expected)") {
            try {
                json.decodeFromString<Drawing2D>(loadFixture("invalid_nan_coordinate.json"))
                error("Should have thrown SerializationException")
            } catch (_: SerializationException) {
                // Expected: "NaN" string cannot be decoded as Double
            }
        }

        test("AC: invalid_bad_schema.json loads with unsupported schema version") {
            val drawing =
                json.decodeFromString<Drawing2D>(
                    loadFixture("invalid_bad_schema.json"),
                )

            drawing.schemaVersion shouldBe 999
            // Schema version 999 is unsupported (expected: 1)
            val errors = inlineValidate(drawing)
            errors.any { it.contains("schema version") } shouldBe true
        }

        test("AC: invalid_missing_required.json fails to parse (missing entity id)") {
            try {
                json.decodeFromString<Drawing2D>(loadFixture("invalid_missing_required.json"))
                error("Should have thrown SerializationException for missing field")
            } catch (_: SerializationException) {
                // Expected: entity missing required "id" field
            }
        }
    }

    context("Parser robustness") {

        test("AC: All parseable fixtures parse without crashing") {
            val parseableFixtures =
                listOf(
                    "valid_simple.json",
                    "valid_complex.json",
                    "valid_all_features.json",
                    "valid_large.json",
                    "valid_empty.json",
                    "invalid_broken_reference.json",
                    "invalid_blank_id.json",
                    "invalid_bad_schema.json",
                )

            parseableFixtures.forEach { fixtureName ->
                val content = loadFixture(fixtureName)
                // Should not throw
                json.decodeFromString<Drawing2D>(content)
            }
        }

        test("AC: Malformed fixtures throw expected exceptions without crashing") {
            val malformedFixtures =
                listOf(
                    "invalid_negative_radius.json",
                    "invalid_nan_coordinate.json",
                    "invalid_missing_required.json",
                )

            malformedFixtures.forEach { fixtureName ->
                val content = loadFixture(fixtureName)
                try {
                    json.decodeFromString<Drawing2D>(content)
                } catch (_: SerializationException) {
                    // OK — parse error
                } catch (_: IllegalArgumentException) {
                    // OK — model constraint violation
                }
                // If we get here, no unexpected crash occurred
            }
        }

        test("AC: Valid fixtures round-trip through serialization") {
            val validFixtures =
                listOf(
                    "valid_simple.json",
                    "valid_complex.json",
                    "valid_all_features.json",
                    "valid_large.json",
                    "valid_empty.json",
                )

            validFixtures.forEach { fixtureName ->
                val content = loadFixture(fixtureName)
                val drawing = json.decodeFromString<Drawing2D>(content)
                val reserialized = Json.encodeToString(Drawing2D.serializer(), drawing)
                val reparsed = json.decodeFromString<Drawing2D>(reserialized)

                reparsed shouldBe drawing
            }
        }
    }

    context("Programmatic large drawing") {

        test("AC: Drawing with 100+ entities can be created and round-tripped") {
            val entities =
                (1..150).map { i ->
                    EntityV1.Line(
                        id = "line-$i",
                        layer = null,
                        start = com.yourapp.drawing2d.model.Point2D(i.toDouble(), 0.0),
                        end = com.yourapp.drawing2d.model.Point2D(i.toDouble(), 100.0),
                        style = com.yourapp.drawing2d.model.LineStyle("#000000", 1.0),
                    )
                }

            val drawing =
                Drawing2D(
                    id = "programmatic-large",
                    name = "Programmatic Large Drawing",
                    page = com.yourapp.drawing2d.model.Page(width = 5000.0, height = 4000.0),
                    entities = entities,
                )

            drawing.entities shouldHaveSize 150
            inlineValidate(drawing).shouldBeEmpty()

            // Round-trip
            val serialized = Json.encodeToString(Drawing2D.serializer(), drawing)
            val deserialized = json.decodeFromString<Drawing2D>(serialized)
            deserialized shouldBe drawing
        }
    }
})

/**
 * Inline validation for golden fixtures — avoids circular dependency
 * on :core:validation module. Checks the same core invariants.
 */
private fun inlineValidate(drawing: Drawing2D): List<String> {
    val errors = mutableListOf<String>()

    if (drawing.schemaVersion != 1) {
        errors.add("Unsupported schema version: ${drawing.schemaVersion} (expected: 1)")
    }

    drawing.entities.forEachIndexed { i, entity ->
        if (entity.id.isBlank()) {
            errors.add("entities[$i].id must not be blank")
        }
    }

    val entityIds = drawing.entities.map { it.id }.toSet()
    drawing.annotations.forEachIndexed { i, annotation ->
        if (annotation.id.isBlank()) {
            errors.add("annotations[$i].id must not be blank")
        }
        val targetId = annotation.targetId
        if (targetId != null && targetId !in entityIds) {
            errors.add("annotations[$i].targetId '$targetId' not found")
        }
    }

    return errors
}

private fun loadFixture(name: String): String {
    val resource =
        GoldenFixtureTest::class.java.getResourceAsStream("/fixtures/$name")
            ?: error("Fixture not found: $name")
    return resource.bufferedReader().use { it.readText() }
}
