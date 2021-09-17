package com.andrefilgs.lookdown

import com.google.common.truth.Truth

fun <T, U> assertEqualsTruth(actual:T, expected: U) {
  println("Actual: $actual")
  println("Expected: $expected")
  Truth.assertWithMessage("** DBC ERROR **").that(actual).isEqualTo(expected)
}