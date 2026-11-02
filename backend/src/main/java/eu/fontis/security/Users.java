package eu.fontis.security;

import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Users and groups. Groups are created on first use, by name. */
@Component
public class Users {

	public enum Role {

		ADMIN, USER

	}

	/**
	 * @param groups names of the groups the user belongs to
	 */
	public record User(long id, String email, String displayName, Role role, List<String> groups) {
	}

	private final JdbcClient jdbc;

	private final PasswordEncoder passwordEncoder;

	Users(JdbcClient jdbc, PasswordEncoder passwordEncoder) {
		this.jdbc = jdbc;
		this.passwordEncoder = passwordEncoder;
	}

	@Transactional
	public User create(String email, String displayName, String password, Role role, List<String> groups) {
		long id = this.jdbc.sql("""
				INSERT INTO app_user (email, display_name, password_hash, role)
				VALUES (:email, :displayName, :hash, :role) RETURNING id
				""")
			.param("email", email.strip().toLowerCase())
			.param("displayName", displayName.strip())
			.param("hash", this.passwordEncoder.encode(password))
			.param("role", role.name())
			.query(Long.class)
			.single();
		for (long groupId : groupIds(groups)) {
			this.jdbc.sql("INSERT INTO user_group (user_id, group_id) VALUES (:user, :group)")
				.param("user", id)
				.param("group", groupId)
				.update();
		}
		return find(id).orElseThrow();
	}

	public Optional<User> find(long id) {
		return this.jdbc.sql(SELECT_USER + " WHERE u.id = :id GROUP BY u.id")
			.param("id", id)
			.query(this::user)
			.optional();
	}

	public Optional<User> findByEmail(String email) {
		return this.jdbc.sql(SELECT_USER + " WHERE u.email = :email GROUP BY u.id")
			.param("email", email.strip().toLowerCase())
			.query(this::user)
			.optional();
	}

	Optional<String> passwordHash(String email) {
		return this.jdbc.sql("SELECT password_hash FROM app_user WHERE email = :email")
			.param("email", email.strip().toLowerCase())
			.query(String.class)
			.optional();
	}

	public List<User> all() {
		return this.jdbc.sql(SELECT_USER + " GROUP BY u.id ORDER BY u.email").query(this::user).list();
	}

	public boolean delete(long id) {
		return this.jdbc.sql("DELETE FROM app_user WHERE id = :id").param("id", id).update() == 1;
	}

	public long count() {
		return this.jdbc.sql("SELECT count(*) FROM app_user").query(Long.class).single();
	}

	public List<String> groupNames() {
		return this.jdbc.sql("SELECT name FROM app_group ORDER BY name").query(String.class).list();
	}

	/** Ids of the named groups, creating the missing ones. Blank names are ignored. */
	public List<Long> groupIds(List<String> names) {
		return names.stream().map(String::strip).filter(n -> !n.isEmpty()).distinct().map(name -> this.jdbc.sql("""
				INSERT INTO app_group (name) VALUES (:name)
				ON CONFLICT (name) DO UPDATE SET name = EXCLUDED.name
				RETURNING id
				""").param("name", name).query(Long.class).single()).toList();
	}

	private static final String SELECT_USER = """
			SELECT u.id, u.email, u.display_name, u.role,
			       coalesce(array_agg(g.name ORDER BY g.name) FILTER (WHERE g.name IS NOT NULL), '{}') AS groups
			FROM app_user u
			LEFT JOIN user_group ug ON ug.user_id = u.id
			LEFT JOIN app_group g ON g.id = ug.group_id
			""";

	private User user(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
		return new User(rs.getLong("id"), rs.getString("email"), rs.getString("display_name"),
				Role.valueOf(rs.getString("role")), List.of((String[]) rs.getArray("groups").getArray()));
	}

}
