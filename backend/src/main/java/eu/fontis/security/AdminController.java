package eu.fontis.security;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** User and group management, for administrators (see {@link SecurityConfig}). */
@RestController
@RequestMapping("/api/admin")
class AdminController {

	private final Users users;

	AdminController(Users users) {
		this.users = users;
	}

	record NewUser(@NotBlank @Email String email, @NotBlank String displayName,
			@NotBlank @Size(min = 12, message = "at least 12 characters") String password, @NotNull Users.Role role,
			List<String> groups) {
	}

	@GetMapping("/users")
	List<Users.User> users() {
		return this.users.all();
	}

	@PostMapping("/users")
	@ResponseStatus(HttpStatus.CREATED)
	Users.User create(@Valid @RequestBody NewUser user) {
		try {
			return this.users.create(user.email(), user.displayName(), user.password(), user.role(),
					user.groups() == null ? List.of() : user.groups());
		}
		catch (DuplicateKeyException ex) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "A user with this email already exists");
		}
	}

	@DeleteMapping("/users/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void delete(@PathVariable long id, Authentication authentication) {
		Users.User user = this.users.find(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
		// Otherwise nobody could manage users or documents any more.
		if (user.email().equals(authentication.getName())) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "You cannot delete your own account");
		}
		if (user.role() == Users.Role.ADMIN && this.users.countAdmins() <= 1) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "The last administrator cannot be deleted");
		}
		this.users.delete(id);
	}

	@GetMapping("/groups")
	List<String> groups() {
		return this.users.groupNames();
	}

}
