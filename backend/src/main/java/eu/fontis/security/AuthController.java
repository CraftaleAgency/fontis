package eu.fontis.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
class AuthController {

	private final AuthenticationManager authenticationManager;

	private final SecurityContextRepository contextRepository;

	private final Users users;

	private final SecurityContextHolderStrategy holder = SecurityContextHolder.getContextHolderStrategy();

	AuthController(AuthenticationManager authenticationManager, SecurityContextRepository contextRepository,
			Users users) {
		this.authenticationManager = authenticationManager;
		this.contextRepository = contextRepository;
		this.users = users;
	}

	record LoginRequest(@NotBlank String email, @NotBlank String password) {
	}

	/** The signed-in user, or 204 for an anonymous visitor. Also hands out the CSRF cookie. */
	@GetMapping("/me")
	Users.User me(Authentication authentication, HttpServletResponse response) {
		if (authentication == null || !authentication.isAuthenticated()
				|| authentication.getName().equals("anonymousUser")) {
			response.setStatus(HttpStatus.NO_CONTENT.value());
			return null;
		}
		return this.users.findByEmail(authentication.getName()).orElse(null);
	}

	@PostMapping("/login")
	Users.User login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest,
			HttpServletResponse httpResponse) {
		Authentication authentication;
		try {
			authentication = this.authenticationManager
				.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(request.email(), request.password()));
		}
		catch (AuthenticationException ex) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Wrong email or password");
		}
		// A new session id on login prevents session fixation; without a session one is created below.
		if (httpRequest.getSession(false) != null) {
			httpRequest.changeSessionId();
		}
		var context = this.holder.createEmptyContext();
		context.setAuthentication(authentication);
		this.holder.setContext(context);
		this.contextRepository.saveContext(context, httpRequest, httpResponse);
		return this.users.findByEmail(authentication.getName()).orElseThrow();
	}

	@PostMapping("/logout")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void logout(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
		new SecurityContextLogoutHandler().logout(request, response, authentication);
	}

}
