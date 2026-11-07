package eu.fontis.security;

import java.util.List;

import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.security.autoconfigure.actuate.web.servlet.EndpointRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;

/**
 * Session-based security for the web app: a JSON login sets a session cookie; state-changing
 * requests need the CSRF token from the XSRF-TOKEN cookie in the X-XSRF-TOKEN header.
 * <p>
 * Asking and searching are open to everyone: what a request can see is decided by
 * {@link eu.fontis.documents.DocumentAccess}, so anonymous users only get the public EU legislation.
 */
@Configuration(proxyBeanMethods = false)
class SecurityConfig {

	// Only in the web application: the ingestion job runs without a web server.
	@Bean
	@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
	SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		return http
			.authorizeHttpRequests(auth -> auth
				// Actuator listens on the internal management port only (management.server.port).
				.requestMatchers(EndpointRequest.toAnyEndpoint()).permitAll()
				.requestMatchers("/api/auth/**").permitAll()
				.requestMatchers(HttpMethod.POST, "/api/ask").permitAll()
				.requestMatchers(HttpMethod.GET, "/api/search", "/api/documents").permitAll()
				.requestMatchers("/api/log/**", "/api/admin/**").hasRole("ADMIN")
				.requestMatchers(HttpMethod.POST, "/api/documents").hasRole("ADMIN")
				.requestMatchers(HttpMethod.DELETE, "/api/documents/**").hasRole("ADMIN")
				.anyRequest().authenticated())
			.csrf(csrf -> csrf.spa())
			.securityContext(context -> context.securityContextRepository(securityContextRepository()))
			// An API: 401 instead of a redirect to a login page.
			.exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
			.formLogin(form -> form.disable())
			.httpBasic(basic -> basic.disable())
			.logout(logout -> logout.disable())
			.build();
	}

	@Bean
	SecurityContextRepository securityContextRepository() {
		return new HttpSessionSecurityContextRepository();
	}

	@Bean
	PasswordEncoder passwordEncoder() {
		return PasswordEncoderFactories.createDelegatingPasswordEncoder();
	}

	@Bean
	UserDetailsService userDetailsService(Users users) {
		return email -> users.findByEmail(email)
			.map(user -> org.springframework.security.core.userdetails.User.withUsername(user.email())
				.password(users.passwordHash(email).orElseThrow())
				.authorities(List.of(new SimpleGrantedAuthority("ROLE_" + user.role().name())))
				.build())
			.orElseThrow(() -> new UsernameNotFoundException(email));
	}

	@Bean
	AuthenticationManager authenticationManager(UserDetailsService userDetailsService, PasswordEncoder encoder) {
		DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
		provider.setPasswordEncoder(encoder);
		return new ProviderManager(provider);
	}

	/** Creates the first administrator from configuration when there are no users yet. */
	@Bean
	ApplicationRunner bootstrapAdmin(Users users, @Value("${fontis.admin.email:}") String email,
			@Value("${fontis.admin.password:}") String password) {
		return args -> {
			if (users.count() == 0 && !email.isBlank() && !password.isBlank()) {
				users.create(email, "Administrator", password, Users.Role.ADMIN, List.of());
			}
		};
	}

}
