package eu.fontis.security;

import java.util.List;

import eu.fontis.FakeEmbeddingModelConfiguration;
import eu.fontis.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Import({ TestcontainersConfiguration.class, FakeEmbeddingModelConfiguration.class })
class SecurityTests {

	@Autowired
	WebApplicationContext context;

	@Autowired
	Users users;

	@Autowired
	JdbcClient jdbc;

	MockMvc mvc;

	@BeforeEach
	void setUp() {
		this.jdbc.sql("DELETE FROM app_user").update();
		this.users.create("admin@example.eu", "Admin", "admin-password-123", Users.Role.ADMIN, List.of());
		this.users.create("alice@example.eu", "Alice", "alice-password-123", Users.Role.USER, List.of("legal"));
		this.mvc = MockMvcBuilders.webAppContextSetup(this.context).apply(springSecurity()).build();
	}

	@Test
	void theAnswerLogIsForAdministratorsOnly() throws Exception {
		this.mvc.perform(get("/api/log")).andExpect(status().isUnauthorized());
		this.mvc.perform(get("/api/log").session(login("alice@example.eu", "alice-password-123")))
			.andExpect(status().isForbidden());
		this.mvc.perform(get("/api/log").session(login("admin@example.eu", "admin-password-123")))
			.andExpect(status().isOk());
	}

	@Test
	void stateChangingRequestsNeedTheCsrfToken() throws Exception {
		this.mvc.perform(post("/api/ask").contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"x\"}"))
			.andExpect(status().isForbidden());
	}

	@Test
	void loginReturnsTheUserAndWrongPasswordsAreRejected() throws Exception {
		this.mvc.perform(post("/api/auth/login").with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"email\":\"alice@example.eu\",\"password\":\"wrong-password\"}"))
			.andExpect(status().isUnauthorized());

		MockHttpSession session = login("alice@example.eu", "alice-password-123");
		this.mvc.perform(get("/api/auth/me").session(session))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.email").value("alice@example.eu"))
			.andExpect(jsonPath("$.groups[0]").value("legal"));
		this.mvc.perform(get("/api/auth/me")).andExpect(status().isNoContent());
	}

	@Test
	void onlyAdministratorsManageUsers() throws Exception {
		String newUser = """
				{"email":"bob@example.eu","displayName":"Bob","password":"bob-password-123","role":"USER","groups":["hr"]}
				""";
		this.mvc.perform(post("/api/admin/users").with(csrf())
			.session(login("alice@example.eu", "alice-password-123"))
			.contentType(MediaType.APPLICATION_JSON)
			.content(newUser)).andExpect(status().isForbidden());
		this.mvc.perform(post("/api/admin/users").with(csrf())
			.session(login("admin@example.eu", "admin-password-123"))
			.contentType(MediaType.APPLICATION_JSON)
			.content(newUser)).andExpect(status().isCreated()).andExpect(jsonPath("$.groups[0]").value("hr"));
	}

	@Test
	void aDeletedAdministratorLosesTheSessionAtOnce() throws Exception {
		Users.User second = this.users.create("carol@example.eu", "Carol", "carol-password-123", Users.Role.ADMIN,
				List.of());
		MockHttpSession session = login("carol@example.eu", "carol-password-123");
		this.mvc.perform(get("/api/admin/users").session(session)).andExpect(status().isOk());

		this.users.delete(second.id());

		this.mvc.perform(get("/api/admin/users").session(session)).andExpect(status().isUnauthorized());
		this.mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isNoContent());
	}

	@Test
	void administratorsCannotDeleteThemselvesOrTheLastAdministrator() throws Exception {
		MockHttpSession session = login("admin@example.eu", "admin-password-123");
		long admin = this.users.findByEmail("admin@example.eu").orElseThrow().id();
		long alice = this.users.findByEmail("alice@example.eu").orElseThrow().id();

		this.mvc.perform(delete("/api/admin/users/" + admin).with(csrf()).session(session))
			.andExpect(status().isConflict());
		this.mvc.perform(delete("/api/admin/users/" + alice).with(csrf()).session(session))
			.andExpect(status().isNoContent());
		this.mvc.perform(delete("/api/admin/users/" + alice).with(csrf()).session(session))
			.andExpect(status().isNotFound());
	}

	@Test
	void searchWithRankingParametersIsForAdministratorsOnly() throws Exception {
		this.mvc.perform(get("/api/search").param("q", "fines")).andExpect(status().isUnauthorized());
		this.mvc.perform(get("/api/search").param("q", "fines").session(login("alice@example.eu", "alice-password-123")))
			.andExpect(status().isForbidden());
		this.mvc.perform(get("/api/search").param("q", "fines").session(login("admin@example.eu", "admin-password-123")))
			.andExpect(status().isOk());
	}

	@Test
	void repeatedWrongPasswordsAreLimitedPerClient() throws Exception {
		String wrong = "{\"email\":\"alice@example.eu\",\"password\":\"wrong-password\"}";
		for (int attempt = 0; attempt < 5; attempt++) {
			this.mvc.perform(post("/api/auth/login").with(csrf()).with(from("203.0.113.9"))
				.contentType(MediaType.APPLICATION_JSON)
				.content(wrong)).andExpect(status().isUnauthorized());
		}
		this.mvc.perform(post("/api/auth/login").with(csrf()).with(from("203.0.113.9"))
			.contentType(MediaType.APPLICATION_JSON)
			.content(wrong)).andExpect(status().isTooManyRequests()).andExpect(header().exists("Retry-After"));
		// Other clients are not affected.
		login("alice@example.eu", "alice-password-123");
	}

	private static RequestPostProcessor from(String address) {
		return request -> {
			request.setRemoteAddr(address);
			return request;
		};
	}

	@Test
	void serverErrorsAreNotReportedAsUnauthorized() throws Exception {
		// The servlet container forwards failed requests to /error, also for anonymous visitors.
		this.mvc.perform(get("/error")).andExpect(status().isInternalServerError());
	}

	private MockHttpSession login(String email, String password) throws Exception {
		return (MockHttpSession) this.mvc
			.perform(post("/api/auth/login").with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
			.andExpect(status().isOk())
			.andReturn()
			.getRequest()
			.getSession();
	}

}
