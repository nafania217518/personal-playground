package dev.portfolio.pricing

import kotlin.test.Test
import kotlin.test.assertEquals

class PriceBookTest {
    @Test fun noDiscountBelowTen() = assertEquals(0, discountPercent(9))
    @Test fun fivePercentFromTen() = assertEquals(5, discountPercent(10))
    @Test fun tenPercentFromHundred() = assertEquals(10, discountPercent(100))
    @Test fun totalUsesLongArithmetic() = assertEquals(1_080_000L, total(12000L, 100))
}
