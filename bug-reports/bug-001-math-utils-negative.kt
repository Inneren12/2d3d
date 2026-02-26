package com.yourapp.drawing2d.math
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
/**
BUG REPORT: BUG-001
Title: MathUtils.round() incorrect rounding for negative numbers
Root Cause: toLong() truncates towards zero, not mathematical rounding.
*/
class BugReport001NegativeRounding : FunSpec({
test("BUG-001: MathUtils.round() incorrect for negative numbers at decimals=0") {
val input = -5.6
val decimals = 0
val result = MathUtils.round(input, decimals)
}
})
