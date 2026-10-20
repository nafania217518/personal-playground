package dev.portfolio.securenotes;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
class AuthController {

	record Credentials(@NotBlank @Size(min = 3, max = 32) String username, @NotBlank @Size(min = 8) String password) {
	}

	record TokenResponse(String token, long expiresIn) {
	}

	private final UserAccountRepository users;
	private final PasswordEncoder encoder;
	private final JwtService jwt;

	AuthController(UserAccountRepository users, PasswordEncoder encoder, JwtService jwt) {
		this.users = users;
		this.encoder = encoder;
		this.jwt = jwt;
	}

	@PostMapping("/register")
	@ResponseStatus(HttpStatus.CREATED)
	UserAccount register(@Valid @RequestBody Credentials body) {
		if (users.findByUsername(body.username()).isPresent()) {
			throw new DuplicateUsername();
		}
		return users.save(new UserAccount(null, body.username(), encoder.encode(body.password()), Set.of("USER")));
	}

	@PostMapping("/login")
	TokenResponse login(@RequestBody Credentials body) {
		var user = users.findByUsername(body.username())
				.filter(u -> encoder.matches(body.password(), u.passwordHash()))
				.orElseThrow(() -> new BadCredentialsException("Bad credentials"));
		return new TokenResponse(jwt.issue(user.username(), user.roles()), jwt.expiresIn());
	}

	@ResponseStatus(HttpStatus.CONFLICT)
	static class DuplicateUsername extends RuntimeException {
	}

	@ExceptionHandler(BadCredentialsException.class)
	@ResponseStatus(HttpStatus.UNAUTHORIZED)
	void badCredentials() {
	}
}
