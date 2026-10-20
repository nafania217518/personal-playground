package dev.portfolio.pricing

import java.util.concurrent.ConcurrentHashMap

const val CURRENCY = "EUR"

fun discountPercent(quantity: Int): Int = when {
    quantity >= 100 -> 10
    quantity >= 10 -> 5
    else -> 0
}

fun total(unitPriceCents: Long, quantity: Int): Long =
    unitPriceCents * quantity * (100 - discountPercent(quantity)) / 100

class PriceBook {
    val prices = ConcurrentHashMap(mapOf("SKU-1" to 1999L, "SKU-2" to 500L, "SKU-3" to 12000L))
}
