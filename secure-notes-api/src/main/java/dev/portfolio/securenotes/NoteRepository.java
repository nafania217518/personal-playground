package dev.portfolio.securenotes;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.TextCriteria;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface NoteRepository extends MongoRepository<Note, String> {
	Optional<Note> findByIdAndOwner(String id, String owner);

	Page<Note> findByOwnerOrderByCreatedAtDesc(String owner, Pageable pageable);

	Page<Note> findByOwner(String owner, TextCriteria text, Pageable pageable);
}
