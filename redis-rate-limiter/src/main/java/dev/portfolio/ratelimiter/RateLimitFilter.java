package dev.portfolio.ratelimiter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Limits /api/** by X-Api-Key header, or by remote address. Skips /api/limits/**. */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

	private final RateLimiter limiter;

	public RateLimitFilter(RateLimiter limiter) {
		this.limiter = limiter;
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		var path = request.getRequestURI();
		return !path.startsWith("/api/") || path.startsWith("/api/limits/");
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		var apiKey = request.getHeader("X-Api-Key");
		var decision = limiter.tryAcquire(apiKey != null ? apiKey : request.getRemoteAddr());
		response.setHeader("X-RateLimit-Remaining", String.valueOf(decision.remaining()));
		if (!decision.allowed()) {
			response.setStatus(429);
			response.setHeader("Retry-After", String.valueOf(decision.retryAfterSeconds()));
			return;
		}
		chain.doFilter(request, response);
	}
}
