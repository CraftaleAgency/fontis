package eu.fontis.documents;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import eu.fontis.security.Users;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/** Listing is open (each user sees what they may search); upload and delete are for administrators. */
@RestController
@RequestMapping("/api/documents")
class DocumentController {

	private final Documents documents;

	private final DocumentAccess access;

	private final Users users;

	DocumentController(Documents documents, DocumentAccess access, Users users) {
		this.documents = documents;
		this.access = access;
		this.users = users;
	}

	@GetMapping
	List<Documents.Info> list(Authentication authentication) {
		return this.documents.visible(this.access.of(authentication));
	}

	/**
	 * @param groups comma-separated group names that may search a restricted document
	 */
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	Documents.Info upload(@RequestParam MultipartFile file, @RequestParam(required = false) String title,
			@RequestParam(defaultValue = "eng") String lang,
			@RequestParam(defaultValue = "RESTRICTED") Documents.Visibility visibility,
			@RequestParam(defaultValue = "") String groups, Authentication authentication) throws IOException {
		if (file.isEmpty() || file.getOriginalFilename() == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No file");
		}
		if (!Documents.LANGUAGES.contains(lang)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported language: " + lang);
		}
		Long uploadedBy = this.users.findByEmail(authentication.getName()).map(Users.User::id).orElse(null);
		try {
			return this.documents.add(new Documents.Upload(file.getOriginalFilename(), file.getBytes(), title, lang,
					visibility, Arrays.stream(groups.split(",")).toList(), uploadedBy));
		}
		catch (IllegalArgumentException ex) {
			throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, ex.getMessage());
		}
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void delete(@PathVariable String id) {
		if (!this.documents.delete(id)) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND);
		}
	}

}
