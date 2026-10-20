package dev.portfolio.kafkaorders;

import java.math.BigDecimal;

public record CustomerStats(String customerId, int orderCount, BigDecimal totalRevenue) {

	static CustomerStats zero(String customerId) {
		return new CustomerStats(customerId, 0, BigDecimal.ZERO);
	}

	CustomerStats plus(int orders, BigDecimal revenue) {
		return new CustomerStats(customerId, orderCount + orders, totalRevenue.add(revenue));
	}
}
