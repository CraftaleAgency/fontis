package eu.fontis.security;

import java.io.IOException;
import java.util.Optional;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Ends the session of a user who was deleted or whose role changed. The session holds the role as
 * it was at login, so without this check a deleted administrator would stay one until the session
 * expired.
 */
class CurrentUserFilter extends OncePerRequestFilter {

	private final Users users;

	CurrentUserFilter(Users users) {
		this.users = users;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication != null && !(authentication instanceof AnonymousAuthenticationToken)
				&& !stillValid(authentication)) {
			HttpSession session = request.getSession(false);
			if (session != null) {
				session.invalidate();
			}
			SecurityContextHolder.clearContext();
		}
		chain.doFilter(request, response);
	}

	private boolean stillValid(Authentication authentication) {
		Optional<Users.User> user = this.users.findByEmail(authentication.getName());
		return user.isPresent() && authentication.getAuthorities()
			.stream()
			.anyMatch(authority -> authority.getAuthority().equals("ROLE_" + user.get().role().name()));
	}

}
