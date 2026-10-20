package dev.portfolio.securenotes;

import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface UserAccountRepository extends MongoRepository<UserAccount, String> {
	Optional<UserAccount> findByUsername(String username);
}
