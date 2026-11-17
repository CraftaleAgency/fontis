package eu.fontis.mcp;

import java.io.IOException;

import eu.fontis.ask.AskRateLimiter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Requests to the MCP endpoint per client per minute, like questions: every search embeds the query on
 * the GPU. Added to the security filter chain (see SecurityConfig), not registered as a bean, so it
 * runs once.
 */
public class McpRateLimitFilter extends OncePerRequestFilter {

	private final AskRateLimiter limiter;

	public McpRateLimitFilter(McpProperties properties) {
		this.limiter = new AskRateLimiter(properties.rateLimitPerMinute());
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		String path = request.getRequestURI().substring(request.getContextPath().length());
		return !(path.equals("/mcp") || path.startsWith("/mcp/"));
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		if (!this.limiter.tryAcquire(request.getRemoteAddr())) {
			response.setHeader("Retry-After", String.valueOf(this.limiter.retryAfterSeconds()));
			response.sendError(429, "Too many requests, try again shortly");
			return;
		}
		chain.doFilter(request, response);
	}

}
