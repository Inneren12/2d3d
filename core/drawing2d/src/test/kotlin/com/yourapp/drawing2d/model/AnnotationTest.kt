package com.yourapp.drawing2d.model

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

// ── Shared fixtures ───────────────────────────────────────────────────────────

private val pos1 = Point2D(100.0, 200.0)
private val pos2 = Point2D(150.0, 300.0)

// ── Tests ─────────────────────────────────────────────────────────────────────

class AnnotationTest : FunSpec({

    // ── AnnotationV1.Text ─────────────────────────────────────────────────────

    context("AnnotationV1.Text") {

        test("constructs with required fields") {
            val text =
                AnnotationV1.Text(
                    id = "t1",
                    targetId = null,
                    position = pos1,
                    content = "Main entrance",
                    fontSize = 12.0,
                    rotation = 0.0,
                )
            text.id shouldBe "t1"
            text.content shouldBe "Main entrance"
            text.fontSize shouldBe 12.0
            text.rotation shouldBe 0.0
            text.position shouldBe pos1
        }

        test("targetId is nullable (can be null)") {
            val text =
                AnnotationV1.Text(
                    id = "t2",
                    targetId = null,
                    position = pos1,
                    content = "Free-floating label",
                    fontSize = 10.0,
                    rotation = 0.0,
                )
            text.targetId.shouldBeNull()
        }

        test("targetId can reference an entity") {
            val text =
                AnnotationV1.Text(
                    id = "t3",
                    targetId = "wall1",
                    position = pos1,
                    content = "Label on wall",
                    fontSize = 10.0,
                    rotation = 0.0,
                )
            text.targetId shouldBe "wall1"
        }

        test("fontSize and rotation are Double type") {
            val text =
                AnnotationV1.Text(
                    id = "t4",
                    targetId = null,
                    position = pos1,
                    content = "test",
                    fontSize = 14.5,
                    rotation = 45.0,
                )
            text.fontSize shouldBe 14.5
            text.rotation shouldBe 45.0
        }

        test("serialization round-trip preserves all fields") {
            val text =
                AnnotationV1.Text(
                    id = "t5",
                    targetId = "entity1",
                    position = pos2,
                    content = "Hello",
                    fontSize = 16.0,
                    rotation = 90.0,
                )
            val encoded = Json.encodeToString<AnnotationV1>(text)
            val decoded = Json.decodeFromString<AnnotationV1>(encoded)
            decoded shouldBe text
        }

        test("JSON discriminator is 'text'") {
            val text =
                AnnotationV1.Text(
                    id = "t6",
                    targetId = null,
                    position = pos1,
                    content = "test",
                    fontSize = 12.0,
                    rotation = 0.0,
                )
            val encoded = Json.encodeToString<AnnotationV1>(text)
            encoded shouldContain "\"type\":\"text\""
        }

        test("targetId = null serializes as explicit null (not omitted)") {
            val text =
                AnnotationV1.Text(
                    id = "t7",
                    targetId = null,
                    position = pos1,
                    content = "test",
                    fontSize = 12.0,
                    rotation = 0.0,
                )
            val encoded = Json.encodeToString<AnnotationV1>(text)
            encoded shouldContain "\"targetId\":null"
        }

        test("rotation = 360.0 is valid (no angle validation)") {
            val text =
                AnnotationV1.Text(
                    id = "t8",
                    targetId = null,
                    position = pos1,
                    content = "test",
                    fontSize = 12.0,
                    rotation = 360.0,
                )
            text.rotation shouldBe 360.0
        }

        test("fontSize = 0.0 is valid (validation deferred to renderer)") {
            val text =
                AnnotationV1.Text(
                    id = "t9",
                    targetId = null,
                    position = pos1,
                    content = "test",
                    fontSize = 0.0,
                    rotation = 0.0,
                )
            text.fontSize shouldBe 0.0
        }
    }

    // ── AnnotationV1.Dimension ────────────────────────────────────────────────

    context("AnnotationV1.Dimension") {

        test("AC: negative value throws IllegalArgumentException") {
            val ex =
                shouldThrow<IllegalArgumentException> {
                    AnnotationV1.Dimension(
                        id = "d1",
                        targetId = "wall1",
                        value = -1.0,
                        units = Units.MM,
                        position = pos1,
                    )
                }
            ex.message shouldContain "-1.0"
        }

        test("AC: value = 0.0 is valid (exactly zero allowed)") {
            val dim =
                AnnotationV1.Dimension(
                    id = "d2",
                    targetId = "wall1",
                    value = 0.0,
                    units = Units.MM,
                    position = pos1,
                )
            dim.value shouldBe 0.0
        }

        test("targetId is NOT nullable (required reference)") {
            val dim =
                AnnotationV1.Dimension(
                    id = "d3",
                    targetId = "beam1",
                    value = 2500.0,
                    units = Units.MM,
                    position = pos1,
                )
            dim.targetId.shouldNotBeNull()
            dim.targetId shouldBe "beam1"
        }

        test("units enum serializes as string name") {
            val dim =
                AnnotationV1.Dimension(
                    id = "d4",
                    targetId = "entity1",
                    value = 10.0,
                    units = Units.FEET,
                    position = pos1,
                )
            val encoded = Json.encodeToString<AnnotationV1>(dim)
            encoded shouldContain "\"FEET\""
        }

        test("serialization round-trip preserves targetId") {
            val dim =
                AnnotationV1.Dimension(
                    id = "d5",
                    targetId = "wall-abc",
                    value = 1500.0,
                    units = Units.CM,
                    position = pos2,
                )
            val encoded = Json.encodeToString<AnnotationV1>(dim)
            val decoded = Json.decodeFromString<AnnotationV1>(encoded) as AnnotationV1.Dimension
            decoded.targetId shouldBe "wall-abc"
        }

        test("JSON discriminator is 'dimension'") {
            val dim =
                AnnotationV1.Dimension(
                    id = "d6",
                    targetId = "entity1",
                    value = 100.0,
                    units = Units.M,
                    position = pos1,
                )
            val encoded = Json.encodeToString<AnnotationV1>(dim)
            encoded shouldContain "\"type\":\"dimension\""
        }

        test("Dimension with value = -0.001 throws IllegalArgumentException") {
            shouldThrow<IllegalArgumentException> {
                AnnotationV1.Dimension(
                    id = "d7",
                    targetId = "entity1",
                    value = -0.001,
                    units = Units.MM,
                    position = pos1,
                )
            }
        }

        test("positive value constructs correctly") {
            val dim =
                AnnotationV1.Dimension(
                    id = "d8",
                    targetId = "wall1",
                    value = 2500.0,
                    units = Units.MM,
                    position = pos2,
                )
            dim.value shouldBe 2500.0
            dim.units shouldBe Units.MM
        }
    }

    // ── AnnotationV1.Tag ──────────────────────────────────────────────────────

    context("AnnotationV1.Tag") {

        test("constructs with targetId (non-null)") {
            val tag =
                AnnotationV1.Tag(
                    id = "tag1",
                    targetId = "beam1",
                    label = "load-bearing",
                )
            tag.targetId shouldBe "beam1"
        }

        test("category is nullable") {
            val tag =
                AnnotationV1.Tag(
                    id = "tag2",
                    targetId = "beam1",
                    label = "structural",
                    category = null,
                )
            tag.category.shouldBeNull()
        }

        test("category can be set") {
            val tag =
                AnnotationV1.Tag(
                    id = "tag3",
                    targetId = "beam1",
                    label = "load-bearing",
                    category = "structural",
                )
            tag.category shouldBe "structural"
        }

        test("label is a string") {
            val tag =
                AnnotationV1.Tag(
                    id = "tag4",
                    targetId = "entity1",
                    label = "electrical",
                )
            tag.label shouldBe "electrical"
        }

        test("serialization round-trip preserves all fields") {
            val tag =
                AnnotationV1.Tag(
                    id = "tag5",
                    targetId = "wall1",
                    label = "fire-rated",
                    category = "safety",
                )
            val encoded = Json.encodeToString<AnnotationV1>(tag)
            val decoded = Json.decodeFromString<AnnotationV1>(encoded)
            decoded shouldBe tag
        }

        test("JSON discriminator is 'tag'") {
            val tag =
                AnnotationV1.Tag(
                    id = "tag6",
                    targetId = "entity1",
                    label = "test",
                )
            val encoded = Json.encodeToString<AnnotationV1>(tag)
            encoded shouldContain "\"type\":\"tag\""
        }
    }

    // ── AnnotationV1.Group ────────────────────────────────────────────────────

    context("AnnotationV1.Group") {

        test("AC: empty memberIds throws IllegalArgumentException with message 'Group must have at least 1 member'") {
            val ex =
                shouldThrow<IllegalArgumentException> {
                    AnnotationV1.Group(
                        id = "g1",
                        targetId = null,
                        name = "Empty group",
                        memberIds = emptyList(),
                    )
                }
            ex.message shouldBe "Group must have at least 1 member"
        }

        test("1 member is valid (minimum requirement)") {
            val group =
                AnnotationV1.Group(
                    id = "g2",
                    targetId = null,
                    name = "Single member",
                    memberIds = listOf("wall1"),
                )
            group.memberIds.size shouldBe 1
        }

        test("targetId is nullable (group can be standalone)") {
            val group =
                AnnotationV1.Group(
                    id = "g3",
                    targetId = null,
                    name = "Standalone group",
                    memberIds = listOf("wall1", "wall2"),
                )
            group.targetId.shouldBeNull()
        }

        test("serialization round-trip preserves memberIds order") {
            val group =
                AnnotationV1.Group(
                    id = "g4",
                    targetId = null,
                    name = "Ordered walls",
                    memberIds = listOf("wall3", "wall1", "wall2"),
                )
            val encoded = Json.encodeToString<AnnotationV1>(group)
            val decoded = Json.decodeFromString<AnnotationV1>(encoded) as AnnotationV1.Group
            decoded.memberIds shouldBe listOf("wall3", "wall1", "wall2")
        }

        test("JSON discriminator is 'group'") {
            val group =
                AnnotationV1.Group(
                    id = "g5",
                    targetId = null,
                    name = "Test group",
                    memberIds = listOf("e1"),
                )
            val encoded = Json.encodeToString<AnnotationV1>(group)
            encoded shouldContain "\"type\":\"group\""
        }

        test("Group with single member List succeeds") {
            val group =
                AnnotationV1.Group(
                    id = "g6",
                    targetId = null,
                    name = "Single",
                    memberIds = listOf("id1"),
                )
            group.memberIds shouldBe listOf("id1")
        }

        test("targetId = null serializes as explicit null (not omitted)") {
            val group =
                AnnotationV1.Group(
                    id = "g7",
                    targetId = null,
                    name = "Standalone",
                    memberIds = listOf("e1"),
                )
            val encoded = Json.encodeToString<AnnotationV1>(group)
            encoded shouldContain "\"targetId\":null"
        }
    }

    // ── Polymorphic deserialization ───────────────────────────────────────────

    context("Polymorphic deserialization") {

        test("list of mixed annotation types round-trips correctly") {
            val annotations: List<AnnotationV1> =
                listOf(
                    AnnotationV1.Text(
                        id = "t1",
                        targetId = null,
                        position = pos1,
                        content = "Label",
                        fontSize = 12.0,
                        rotation = 0.0,
                    ),
                    AnnotationV1.Dimension(
                        id = "d1",
                        targetId = "wall1",
                        value = 2500.0,
                        units = Units.MM,
                        position = pos2,
                    ),
                    AnnotationV1.Tag(
                        id = "tag1",
                        targetId = "beam1",
                        label = "structural",
                        category = "load-bearing",
                    ),
                    AnnotationV1.Group(
                        id = "g1",
                        targetId = null,
                        name = "Floor 1",
                        memberIds = listOf("wall1", "wall2"),
                    ),
                )
            val encoded = Json.encodeToString(annotations)
            val decoded = Json.decodeFromString<List<AnnotationV1>>(encoded)
            decoded shouldBe annotations
        }

        test("Units enum values all serialize as string names") {
            Units.values().forEach { unit ->
                val dim =
                    AnnotationV1.Dimension(
                        id = "u-${unit.name}",
                        targetId = "entity",
                        value = 1.0,
                        units = unit,
                        position = pos1,
                    )
                val encoded = Json.encodeToString<AnnotationV1>(dim)
                encoded shouldContain "\"${unit.name}\""
            }
        }
    }
})
