package dev.portfolio.quote.gateway;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

final class Dtos {
    private Dtos() {}

    record ItemRequest(@NotBlank String sku, @Min(1) int quantity) {}

    record QuoteResponse(String sku, int quantity, int available, boolean enough,
                         long unitPriceCents, long totalCents, String currency, int discountPercent) {}

    record OrderResponse(String orderId, String sku, int quantity, long totalCents, String currency, int remaining) {}
}
