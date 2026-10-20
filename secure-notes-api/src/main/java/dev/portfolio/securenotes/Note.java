package dev.portfolio.securenotes;

import java.time.Instant;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.index.TextIndexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("notes")
public record Note(@Id String id, @Indexed String owner, @TextIndexed String title, @TextIndexed String content,
		@CreatedDate Instant createdAt, @LastModifiedDate Instant updatedAt) {

	Note withContent(String title, String content) {
		return new Note(id, owner, title, content, createdAt, updatedAt);
	}
}
