package dev.portfolio.securenotes;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

	@Test
	void issuedTokenDecodesWithExpectedSubjectAndRoles() {
		var service = new JwtService("0123456789abcdef0123456789abcdef", 3600);
		var token = service.issue("alice", Set.of("USER", "ADMIN"));
		var jwt = service.decoder().decode(token);
		assertThat(jwt.getSubject()).isEqualTo("alice");
		assertThat(jwt.getClaimAsStringList("roles")).containsExactlyInAnyOrder("USER", "ADMIN");
		assertThat(service.expiresIn()).isEqualTo(3600);
	}
}
