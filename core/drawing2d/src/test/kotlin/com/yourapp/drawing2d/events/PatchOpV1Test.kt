package com.yourapp.drawing2d.events

import com.yourapp.drawing2d.model.Point2D
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class PatchOpV1Test : FunSpec({
    context("AddNode operations") {
        test("AC: AddNode.inverse() returns DeleteNode with same position") {
            val add = PatchOpV1.AddNode("n1", Point2D(10.0, 20.0))
            val delete = add.inverse()

            delete shouldBe PatchOpV1.DeleteNode("n1", Point2D(10.0, 20.0))
        }

        test("AC: double inverse is identity") {
            val original = PatchOpV1.AddNode("n1", Point2D(10.0, 20.0))
            val result = original.inverse().inverse()

            result shouldBe original
        }
    }

    context("DeleteNode operations") {
        test("DeleteNode.inverse() returns AddNode") {
            val delete = PatchOpV1.DeleteNode("n1", Point2D(30.0, 40.0))
            val add = delete.inverse()

            add shouldBe PatchOpV1.AddNode("n1", Point2D(30.0, 40.0))
        }

        test("AC: double inverse is identity") {
            val original = PatchOpV1.DeleteNode("n1", Point2D(10.0, 20.0))
            val result = original.inverse().inverse()

            result shouldBe original
        }
    }

    context("MoveNode operations") {
        test("MoveNode.inverse() swaps positions") {
            val move = PatchOpV1.MoveNode("n1", Point2D(0.0, 0.0), Point2D(10.0, 10.0))
            val inversed = move.inverse()

            inversed shouldBe PatchOpV1.MoveNode("n1", Point2D(10.0, 10.0), Point2D(0.0, 0.0))
        }

        test("AC: MoveNode double inverse equals original") {
            val original = PatchOpV1.MoveNode("n1", Point2D(5.0, 5.0), Point2D(15.0, 15.0))
            val result = original.inverse().inverse()

            result shouldBe original
        }
    }

    context("AddMember operations") {
        test("AC: AddMember.inverse() returns DeleteMember") {
            val add =
                PatchOpV1.AddMember(
                    memberId = "m1",
                    startNodeId = "n1",
                    endNodeId = "n2",
                    profileRef = "profile-123",
                )
            val delete = add.inverse()

            delete shouldBe
                PatchOpV1.DeleteMember(
                    memberId = "m1",
                    deletedStartNodeId = "n1",
                    deletedEndNodeId = "n2",
                    deletedProfileRef = "profile-123",
                )
        }

        test("AddMember with null profileRef") {
            val add =
                PatchOpV1.AddMember(
                    memberId = "m1",
                    startNodeId = "n1",
                    endNodeId = "n2",
                    profileRef = null,
                )
            val delete = add.inverse()

            delete shouldBe
                PatchOpV1.DeleteMember(
                    memberId = "m1",
                    deletedStartNodeId = "n1",
                    deletedEndNodeId = "n2",
                    deletedProfileRef = null,
                )
        }

        test("AC: double inverse is identity") {
            val original =
                PatchOpV1.AddMember(
                    memberId = "m1",
                    startNodeId = "n1",
                    endNodeId = "n2",
                    profileRef = "profile",
                )
            val result = original.inverse().inverse()

            result shouldBe original
        }
    }

    context("DeleteMember operations") {
        test("AC: DeleteMember.inverse() returns AddMember") {
            val delete =
                PatchOpV1.DeleteMember(
                    memberId = "m1",
                    deletedStartNodeId = "n1",
                    deletedEndNodeId = "n2",
                    deletedProfileRef = "profile-456",
                )
            val add = delete.inverse()

            add shouldBe
                PatchOpV1.AddMember(
                    memberId = "m1",
                    startNodeId = "n1",
                    endNodeId = "n2",
                    profileRef = "profile-456",
                )
        }

        test("AC: double inverse is identity") {
            val original =
                PatchOpV1.DeleteMember(
                    memberId = "m1",
                    deletedStartNodeId = "n1",
                    deletedEndNodeId = "n2",
                )
            val result = original.inverse().inverse()

            result shouldBe original
        }
    }

    context("UpdateMemberProfile operations") {
        test("AC: UpdateMemberProfile.inverse() swaps old/new") {
            val update =
                PatchOpV1.UpdateMemberProfile(
                    memberId = "m1",
                    oldProfileRef = "old-profile",
                    newProfileRef = "new-profile",
                )
            val inversed = update.inverse()

            inversed shouldBe
                PatchOpV1.UpdateMemberProfile(
                    memberId = "m1",
                    oldProfileRef = "new-profile",
                    newProfileRef = "old-profile",
                )
        }

        test("AC: double inverse equals original") {
            val original =
                PatchOpV1.UpdateMemberProfile(
                    memberId = "m1",
                    oldProfileRef = "profile-a",
                    newProfileRef = "profile-b",
                )
            val result = original.inverse().inverse()

            result shouldBe original
        }
    }

    context("Serialization") {
        // Explicit Json config for test determinism
        val testJson =
            Json {
                prettyPrint = false
                ignoreUnknownKeys = false
                encodeDefaults = true
            }

        test("AC: All operations serialize/deserialize correctly") {
            val ops =
                listOf(
                    // Node operations
                    PatchOpV1.AddNode("n1", Point2D(1.0, 2.0)),
                    PatchOpV1.DeleteNode("n2", Point2D(3.0, 4.0)),
                    PatchOpV1.MoveNode("n3", Point2D(5.0, 6.0), Point2D(7.0, 8.0)),
                    // Member operations
                    PatchOpV1.AddMember("m1", "n1", "n2", "profile-1"),
                    PatchOpV1.DeleteMember("m2", "n3", "n4", null),
                    PatchOpV1.UpdateMemberProfile("m3", "old", "new"),
                )

            val json = testJson.encodeToString(ops)
            val deserialized = testJson.decodeFromString<List<PatchOpV1>>(json)

            deserialized shouldBe ops
        }

        @OptIn(ExperimentalSerializationApi::class)
        test("@SerialName ensures stable discriminators") {
            val prettyJson =
                Json {
                    prettyPrint = true
                    prettyPrintIndent = "  "
                }

            val op = PatchOpV1.AddNode("n1", Point2D(10.0, 20.0))
            val json = prettyJson.encodeToString(PatchOpV1.serializer(), op)

            // Verify JSON contains stable discriminator
            json shouldContain "\"type\": \"add_node\""
            json shouldContain "\"nodeId\": \"n1\""

            // Verify round-trip works
            val deserialized = prettyJson.decodeFromString<PatchOpV1>(json)
            deserialized shouldBe op
        }
    }

    context("Memory and Performance") {
        test("AC: Single operation serialized size < 1KB") {
            val ops =
                listOf(
                    PatchOpV1.AddNode("node-1", Point2D(100.0, 200.0)),
                    PatchOpV1.DeleteNode("node-2", Point2D(300.0, 400.0)),
                    PatchOpV1.MoveNode("node-3", Point2D(0.0, 0.0), Point2D(10.0, 10.0)),
                    PatchOpV1.AddMember("member-1", "n1", "n2", "profile-ref"),
                    PatchOpV1.DeleteMember("member-2", "n3", "n4", null),
                    PatchOpV1.UpdateMemberProfile("member-3", "old-prof", "new-prof"),
                )

            ops.forEach { op ->
                val json = Json.encodeToString(PatchOpV1.serializer(), op)
                val serializedBytes = json.toByteArray().size

                // AC: Serialized JSON size should be < 1KB for efficient storage
                serializedBytes shouldBeLessThan 1024
            }
        }
    }

    context("Edge Cases") {
        test("Empty string IDs are valid") {
            val add = PatchOpV1.AddNode("", Point2D(0.0, 0.0))
            val delete = add.inverse()

            delete shouldBe PatchOpV1.DeleteNode("", Point2D(0.0, 0.0))
        }

        test("Very long IDs (255 chars) serialize correctly") {
            val longId = "x".repeat(255)
            val op = PatchOpV1.AddNode(longId, Point2D(1.0, 2.0))

            val json = Json.encodeToString(PatchOpV1.serializer(), op)
            val deserialized = Json.decodeFromString<PatchOpV1>(json)

            deserialized shouldBe op
        }

        test("Extreme coordinates serialize correctly") {
            val op = PatchOpV1.AddNode("n1", Point2D(999_999.9999, -999_999.9999))

            val json = Json.encodeToString(PatchOpV1.serializer(), op)
            val deserialized = Json.decodeFromString<PatchOpV1>(json)

            deserialized shouldBe op
        }
    }
})
