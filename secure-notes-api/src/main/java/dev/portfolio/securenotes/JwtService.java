package dev.portfolio.securenotes;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Set;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

	private final SecretKeySpec key;
	private final long ttlSeconds;

	public JwtService(@Value("${app.jwt.secret}") String secret, @Value("${app.jwt.ttl-seconds}") long ttlSeconds) {
		this.key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
		this.ttlSeconds = ttlSeconds;
	}

	public String issue(String username, Set<String> roles) {
		var now = Instant.now();
		var claims = JwtClaimsSet.builder().subject(username).issuedAt(now).expiresAt(now.plusSeconds(ttlSeconds))
				.claim("roles", roles.stream().toList()).build();
		var header = JwsHeader.with(MacAlgorithm.HS256).build();
		return NimbusJwtEncoder.withSecretKey(key).build().encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
	}

	public JwtDecoder decoder() {
		return NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
	}

	public long expiresIn() {
		return ttlSeconds;
	}
}
