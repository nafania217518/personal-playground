package dev.portfolio.securenotes;

import java.util.Set;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("users")
public record UserAccount(@Id String id, @Indexed(unique = true) String username,
		@com.fasterxml.jackson.annotation.JsonIgnore String passwordHash, Set<String> roles) {
}
