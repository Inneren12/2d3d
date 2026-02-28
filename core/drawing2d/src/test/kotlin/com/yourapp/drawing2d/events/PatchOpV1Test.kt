package com.yourapp.drawing2d.events

import com.yourapp.drawing2d.model.Point2D
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
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

    context("Serialization") {
        test("AC: Operations serialize/deserialize correctly") {
            val ops = listOf(
                PatchOpV1.AddNode("n1", Point2D(1.0, 2.0)),
                PatchOpV1.DeleteNode("n2", Point2D(3.0, 4.0)),
                PatchOpV1.MoveNode("n3", Point2D(5.0, 6.0), Point2D(7.0, 8.0)),
            )

            val json = Json.encodeToString(ops)
            val deserialized = Json.decodeFromString<List<PatchOpV1>>(json)

            deserialized shouldBe ops
        }
    }
})
