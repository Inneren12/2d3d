package com.yourapp.drawing2d.model

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe

class PrimitivesTest : FunSpec({

    // ── Point2D.distanceTo ────────────────────────────────────────────────────

    context("Point2D.distanceTo") {

        test("AC: (0,0).distanceTo(3,4) == 5.0  [3-4-5 right triangle]") {
            Point2D(0.0, 0.0).distanceTo(Point2D(3.0, 4.0)) shouldBe 5.0
        }

        test("distance to self is 0.0") {
            val p = Point2D(3.0, 4.0)
            p.distanceTo(p) shouldBe 0.0
        }

        test("distance is symmetric: a.distanceTo(b) == b.distanceTo(a)") {
            val a = Point2D(1.0, 2.0)
            val b = Point2D(4.0, 6.0)
            a.distanceTo(b) shouldBe b.distanceTo(a)
        }

        test("distance with negative source coordinates — 3-4-5 preserved") {
            Point2D(-3.0, 0.0).distanceTo(Point2D(0.0, 4.0)) shouldBe 5.0
        }

        test("horizontal distance along x-axis") {
            Point2D(0.0, 0.0).distanceTo(Point2D(5.0, 0.0)) shouldBe 5.0
        }

        test("vertical distance along y-axis") {
            Point2D(0.0, 0.0).distanceTo(Point2D(0.0, 7.0)) shouldBe 7.0
        }

        test("non-integer result is correct to floating-point precision") {
            // sqrt(2) ≈ 1.41421356…
            Point2D(0.0, 0.0).distanceTo(Point2D(1.0, 1.0)) shouldBe (1.41421356 plusOrMinus 1e-7)
        }
    }

    // ── Point2D.toJsonSafe ────────────────────────────────────────────────────

    context("Point2D.toJsonSafe") {

        test("AC: (1.123456789, 2.987654321).toJsonSafe() == Point2D(1.1235, 2.9877)") {
            Point2D(1.123456789, 2.987654321).toJsonSafe() shouldBe Point2D(1.1235, 2.9877)
        }

        test("integer-valued coordinates are unchanged after rounding") {
            Point2D(3.0, 4.0).toJsonSafe() shouldBe Point2D(3.0, 4.0)
        }

        test("x coordinate is rounded to 4 decimal places") {
            Point2D(1.00005, 0.0).toJsonSafe().x shouldBe 1.0001
        }

        test("y coordinate is rounded to 4 decimal places") {
            Point2D(0.0, 2.55555).toJsonSafe().y shouldBe 2.5556
        }

        test("toJsonSafe is idempotent: applying it twice yields the same result") {
            val p = Point2D(1.123456789, 2.987654321)
            p.toJsonSafe() shouldBe p.toJsonSafe().toJsonSafe()
        }

        test("negative coordinates are rounded correctly") {
            Point2D(-1.123456789, -2.987654321).toJsonSafe() shouldBe Point2D(-1.1235, -2.9877)
        }

        test("zero coordinates remain zero") {
            Point2D(0.0, 0.0).toJsonSafe() shouldBe Point2D(0.0, 0.0)
        }
    }

    // ── Point2D.plus ─────────────────────────────────────────────────────────

    context("Point2D.plus") {

        test("(1,2) + (3,4) == (4,6)") {
            Point2D(1.0, 2.0) + Point2D(3.0, 4.0) shouldBe Point2D(4.0, 6.0)
        }

        test("adding zero point is identity") {
            val p = Point2D(5.0, 7.0)
            p + Point2D(0.0, 0.0) shouldBe p
        }

        test("addition with negative point yields correct result") {
            Point2D(3.0, 5.0) + Point2D(-1.0, -2.0) shouldBe Point2D(2.0, 3.0)
        }
    }

    // ── Point2D.minus ────────────────────────────────────────────────────────

    context("Point2D.minus") {

        test("(4,6) - (1,2) == (3,4)") {
            Point2D(4.0, 6.0) - Point2D(1.0, 2.0) shouldBe Point2D(3.0, 4.0)
        }

        test("subtracting self yields (0,0)") {
            val p = Point2D(5.0, 7.0)
            p - p shouldBe Point2D(0.0, 0.0)
        }

        test("subtraction can produce negative coordinates") {
            Point2D(1.0, 2.0) - Point2D(3.0, 5.0) shouldBe Point2D(-2.0, -3.0)
        }
    }

    // ── Point2D.times ────────────────────────────────────────────────────────

    context("Point2D.times") {

        test("(2,3) * 2.0 == (4,6)") {
            Point2D(2.0, 3.0) * 2.0 shouldBe Point2D(4.0, 6.0)
        }

        test("multiplying by 0.0 yields (0,0)") {
            Point2D(5.0, 7.0) * 0.0 shouldBe Point2D(0.0, 0.0)
        }

        test("multiplying by 1.0 is identity") {
            val p = Point2D(5.0, 7.0)
            p * 1.0 shouldBe p
        }

        test("multiplying by -1.0 negates both coordinates") {
            Point2D(3.0, 4.0) * -1.0 shouldBe Point2D(-3.0, -4.0)
        }

        test("multiplying by 0.5 halves each coordinate") {
            Point2D(4.0, 6.0) * 0.5 shouldBe Point2D(2.0, 3.0)
        }
    }

    // ── Vector2D.length ──────────────────────────────────────────────────────

    context("Vector2D.length") {

        test("(3,4).length() == 5.0  [3-4-5 triangle]") {
            Vector2D(3.0, 4.0).length() shouldBe 5.0
        }

        test("zero vector has length 0.0") {
            Vector2D(0.0, 0.0).length() shouldBe 0.0
        }

        test("unit vector along x has length 1.0") {
            Vector2D(1.0, 0.0).length() shouldBe 1.0
        }

        test("negative components do not affect length") {
            Vector2D(-3.0, -4.0).length() shouldBe 5.0
        }
    }

    // ── Vector2D arithmetic ───────────────────────────────────────────────────

    context("Vector2D.plus") {

        test("(1,2) + (3,4) == (4,6)") {
            Vector2D(1.0, 2.0) + Vector2D(3.0, 4.0) shouldBe Vector2D(4.0, 6.0)
        }

        test("adding zero vector is identity") {
            val v = Vector2D(5.0, 7.0)
            v + Vector2D(0.0, 0.0) shouldBe v
        }
    }

    context("Vector2D.minus") {

        test("(4,6) - (1,2) == (3,4)") {
            Vector2D(4.0, 6.0) - Vector2D(1.0, 2.0) shouldBe Vector2D(3.0, 4.0)
        }

        test("subtracting self yields (0,0)") {
            val v = Vector2D(5.0, 7.0)
            v - v shouldBe Vector2D(0.0, 0.0)
        }
    }

    context("Vector2D.times") {

        test("(2,3) * 3.0 == (6,9)") {
            Vector2D(2.0, 3.0) * 3.0 shouldBe Vector2D(6.0, 9.0)
        }

        test("multiplying by 0.0 yields zero vector") {
            Vector2D(5.0, 7.0) * 0.0 shouldBe Vector2D(0.0, 0.0)
        }

        test("multiplying by -1.0 negates both components") {
            Vector2D(3.0, 4.0) * -1.0 shouldBe Vector2D(-3.0, -4.0)
        }
    }

    // ── Vector2D.toJsonSafe ───────────────────────────────────────────────────

    context("Vector2D.toJsonSafe") {

        test("(1.123456789, 2.987654321).toJsonSafe() == Vector2D(1.1235, 2.9877)") {
            Vector2D(1.123456789, 2.987654321).toJsonSafe() shouldBe Vector2D(1.1235, 2.9877)
        }

        test("integer-valued components are unchanged") {
            Vector2D(3.0, 4.0).toJsonSafe() shouldBe Vector2D(3.0, 4.0)
        }

        test("toJsonSafe is idempotent") {
            val v = Vector2D(1.123456789, 2.987654321)
            v.toJsonSafe() shouldBe v.toJsonSafe().toJsonSafe()
        }
    }
})
