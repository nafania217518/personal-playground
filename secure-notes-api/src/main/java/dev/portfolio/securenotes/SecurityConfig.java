package dev.portfolio.securenotes;

import java.util.List;
import java.util.Set;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableMethodSecurity
@EnableMongoAuditing
class SecurityConfig {

	@Bean
	SecurityFilterChain filterChain(HttpSecurity http, JwtService jwt) throws Exception {
		var rolesToAuthorities = new JwtGrantedAuthoritiesConverter();
		rolesToAuthorities.setAuthoritiesClaimName("roles");
		rolesToAuthorities.setAuthorityPrefix("ROLE_");
		var converter = new JwtAuthenticationConverter();
		converter.setJwtGrantedAuthoritiesConverter(rolesToAuthorities);

		return http.csrf(c -> c.disable())
				.cors(Customizer.withDefaults())
				.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(a -> a.requestMatchers("/auth/**").permitAll().anyRequest().authenticated())
				.oauth2ResourceServer(o -> o.jwt(j -> j.decoder(jwt.decoder()).jwtAuthenticationConverter(converter)))
				.build();
	}

	@Bean
	UrlBasedCorsConfigurationSource corsConfigurationSource() {
		var cfg = new CorsConfiguration();
		cfg.setAllowedOrigins(List.of("http://localhost:4200"));
		cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
		cfg.setAllowedHeaders(List.of("Authorization", "Content-Type"));
		var source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", cfg);
		return source;
	}

	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	UserDetailsService userDetailsService(UserAccountRepository users) {
		return username -> users.findByUsername(username)
				.map(u -> User.withUsername(u.username()).password(u.passwordHash()).roles(u.roles().toArray(String[]::new)).build())
				.orElseThrow(() -> new UsernameNotFoundException(username));
	}

	@Bean
	ApplicationRunner seedAdmin(UserAccountRepository users, PasswordEncoder encoder,
			@org.springframework.beans.factory.annotation.Value("${app.admin.username}") String username,
			@org.springframework.beans.factory.annotation.Value("${app.admin.password}") String password) {
		return args -> {
			if (users.findByUsername(username).isEmpty()) {
				users.save(new UserAccount(null, username, encoder.encode(password), Set.of("USER", "ADMIN")));
			}
		};
	}
}
