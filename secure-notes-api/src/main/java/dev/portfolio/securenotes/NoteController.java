package dev.portfolio.securenotes;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.query.TextCriteria;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
class NoteController {

	record NoteRequest(@NotBlank @Size(max = 120) String title, String content) {
	}

	private final NoteRepository notes;

	NoteController(NoteRepository notes) {
		this.notes = notes;
	}

	@GetMapping("/notes")
	Page<Note> list(@RequestParam(defaultValue = "") String q, @RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size, Authentication auth) {
		var pageable = PageRequest.of(page, size);
		return q.isBlank() ? notes.findByOwnerOrderByCreatedAtDesc(auth.getName(), pageable)
				: notes.findByOwner(auth.getName(), TextCriteria.forDefaultLanguage().matchingAny(q), pageable);
	}

	@PostMapping("/notes")
	@ResponseStatus(HttpStatus.CREATED)
	Note create(@Valid @RequestBody NoteRequest body, Authentication auth) {
		return notes.save(new Note(null, auth.getName(), body.title(), body.content(), null, null));
	}

	@GetMapping("/notes/{id}")
	Note get(@PathVariable String id, Authentication auth) {
		return own(id, auth);
	}

	@PutMapping("/notes/{id}")
	Note update(@PathVariable String id, @Valid @RequestBody NoteRequest body, Authentication auth) {
		return notes.save(own(id, auth).withContent(body.title(), body.content()));
	}

	@DeleteMapping("/notes/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void delete(@PathVariable String id, Authentication auth) {
		notes.delete(own(id, auth));
	}

	@GetMapping("/admin/notes")
	@PreAuthorize("hasRole('ADMIN')")
	List<Note> all() {
		return notes.findAll();
	}

	private Note own(String id, Authentication auth) {
		return notes.findByIdAndOwner(id, auth.getName())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
	}
}
