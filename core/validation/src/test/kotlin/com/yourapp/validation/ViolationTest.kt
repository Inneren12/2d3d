package com.yourapp.validation

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class ViolationTest : FunSpec({

    val json =
        Json {
            prettyPrint = false
        }

    context("Severity enum") {
        test("Has three levels") {
            Severity.entries.size shouldBe 3
        }

        test("Serializes correctly") {
            val severity = Severity.ERROR
            val jsonString = json.encodeToString(severity)

            jsonString shouldBe "\"ERROR\""
        }
    }

    context("MissingField violation") {
        test("Creates with correct message") {
            val violation =
                Violation.MissingField(
                    path = "drawing",
                    fieldName = "id",
                )

            violation.path shouldBe "drawing"
            violation.severity shouldBe Severity.ERROR
            violation.message shouldBe "Missing required field: id"
        }

        test("AC: Serializes to JSON correctly") {
            val violation =
                Violation.MissingField(
                    path = "drawing.entities[0]",
                    fieldName = "layer",
                )

            val jsonString = json.encodeToString(violation)
            val deserialized = json.decodeFromString<Violation.MissingField>(jsonString)

            deserialized shouldBe violation
        }

        test("AC: Message is clear and actionable") {
            val violation =
                Violation.MissingField(
                    path = "drawing",
                    fieldName = "schemaVersion",
                )

            violation.message shouldContain "Missing required field"
            violation.message shouldContain "schemaVersion"
        }
    }

    context("InvalidValue violation") {
        test("Creates with correct message") {
            val violation =
                Violation.InvalidValue(
                    path = "drawing.entities[0]",
                    fieldName = "radius",
                    value = "-5.0",
                    constraint = "must be positive",
                )

            violation.path shouldBe "drawing.entities[0]"
            violation.severity shouldBe Severity.ERROR
            violation.message shouldBe "Invalid value for radius: '-5.0' (must be positive)"
        }

        test("AC: Serializes to JSON correctly") {
            val violation =
                Violation.InvalidValue(
                    path = "entity.circle",
                    fieldName = "radius",
                    value = "NaN",
                    constraint = "must be finite",
                )

            val jsonString = json.encodeToString(violation)
            val deserialized = json.decodeFromString<Violation.InvalidValue>(jsonString)

            deserialized shouldBe violation
        }

        test("AC: Message is clear and actionable") {
            val violation =
                Violation.InvalidValue(
                    path = "polyline",
                    fieldName = "points",
                    value = "[]",
                    constraint = "minimum 2 points required",
                )

            violation.message shouldContain "Invalid value"
            violation.message shouldContain "points"
            violation.message shouldContain "minimum 2 points"
        }
    }

    context("BrokenReference violation") {
        test("Creates with correct message") {
            val violation =
                Violation.BrokenReference(
                    path = "drawing.annotations[0]",
                    referenceId = "entity-123",
                    targetType = "Entity",
                )

            violation.path shouldBe "drawing.annotations[0]"
            violation.severity shouldBe Severity.ERROR
            violation.message shouldBe "Reference entity-123 to Entity not found"
        }

        test("AC: Serializes to JSON correctly") {
            val violation =
                Violation.BrokenReference(
                    path = "annotation.dimension",
                    referenceId = "line-456",
                    targetType = "Line",
                )

            val jsonString = json.encodeToString(violation)
            val deserialized = json.decodeFromString<Violation.BrokenReference>(jsonString)

            deserialized shouldBe violation
        }

        test("AC: Message is clear and actionable") {
            val violation =
                Violation.BrokenReference(
                    path = "dimension",
                    referenceId = "member-789",
                    targetType = "Member",
                )

            violation.message shouldContain "Reference"
            violation.message shouldContain "member-789"
            violation.message shouldContain "not found"
        }
    }

    context("Custom violation") {
        test("Creates with arbitrary severity and message") {
            val violation =
                Violation.Custom(
                    path = "drawing",
                    severity = Severity.WARNING,
                    message = "Drawing has no scale information",
                )

            violation.path shouldBe "drawing"
            violation.severity shouldBe Severity.WARNING
            violation.message shouldBe "Drawing has no scale information"
        }

        test("AC: Serializes to JSON correctly") {
            val violation =
                Violation.Custom(
                    path = "schema",
                    severity = Severity.INFO,
                    message = "Using default schema version",
                )

            val jsonString = json.encodeToString(violation)
            val deserialized = json.decodeFromString<Violation.Custom>(jsonString)

            deserialized shouldBe violation
        }

        test("Supports ERROR severity") {
            val violation =
                Violation.Custom(
                    path = "drawing",
                    severity = Severity.ERROR,
                    message = "Too many entities: 150000 (max: 100000)",
                )

            violation.severity shouldBe Severity.ERROR
        }

        test("Supports INFO severity") {
            val violation =
                Violation.Custom(
                    path = "metadata",
                    severity = Severity.INFO,
                    message = "No metadata provided",
                )

            violation.severity shouldBe Severity.INFO
        }
    }

    context("Serialization of mixed violations") {
        test("AC: All violation types serialize correctly") {
            val violations =
                listOf(
                    Violation.MissingField("path1", "field1"),
                    Violation.InvalidValue("path2", "field2", "value", "constraint"),
                    Violation.BrokenReference("path3", "ref1", "Type"),
                    Violation.Custom("path4", Severity.WARNING, "Custom message"),
                )

            val jsonString = json.encodeToString(violations)
            val deserialized = json.decodeFromString<List<Violation>>(jsonString)

            deserialized shouldBe violations
        }
    }
})
