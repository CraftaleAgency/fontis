package eu.fontis.documents;

import eu.fontis.ask.Access;
import eu.fontis.security.Users;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * Decides which documents a request may search: public documents for everyone, documents shared
 * with the user's groups for signed-in users, everything for administrators.
 */
@Component
public class DocumentAccess {

	private static final String VISIBLE = """
			SELECT d.celex FROM source_document d
			WHERE d.visibility = 'public'
			   OR EXISTS (SELECT 1 FROM document_group dg
			              JOIN user_group ug ON ug.group_id = dg.group_id
			              WHERE dg.celex = d.celex AND ug.user_id = :userId)
			""";

	private final JdbcClient jdbc;

	private final Users users;

	DocumentAccess(JdbcClient jdbc, Users users) {
		this.jdbc = jdbc;
		this.users = users;
	}

	public Access of(Authentication authentication) {
		Users.User user = signedIn(authentication) ? this.users.findByEmail(authentication.getName()).orElse(null)
				: null;
		if (user != null && user.role() == Users.Role.ADMIN) {
			return Access.EVERYTHING;
		}
		long userId = user == null ? -1 : user.id();
		return Access.only(this.jdbc.sql(VISIBLE).param("userId", userId).query(String.class).list());
	}

	public static boolean signedIn(Authentication authentication) {
		return authentication != null && authentication.isAuthenticated()
				&& !"anonymousUser".equals(authentication.getName());
	}

}
