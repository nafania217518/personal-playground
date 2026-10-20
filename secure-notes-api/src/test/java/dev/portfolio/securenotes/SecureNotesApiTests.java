package dev.portfolio.securenotes;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SecureNotesApiTests {

	@Autowired MockMvc mvc;
	@Autowired ObjectMapper json;

	private static String creds(String user, String pass) {
		return "{\"username\":\"%s\",\"password\":\"%s\"}".formatted(user, pass);
	}

	private static String unique() {
		return "u" + UUID.randomUUID().toString().substring(0, 8);
	}

	/** Registers a fresh user, logs in, returns the bearer token. */
	private String token() throws Exception {
		return tokenFor(unique(), "password123");
	}

	private String tokenFor(String user, String pass) throws Exception {
		mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content(creds(user, pass)));
		var res = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content(creds(user, pass)))
				.andExpect(status().isOk()).andReturn();
		return json.readTree(res.getResponse().getContentAsString()).get("token").asString();
	}

	private MvcResult createNote(String token, String title, String content) throws Exception {
		return mvc.perform(post("/notes").header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"title\":\"%s\",\"content\":\"%s\"}".formatted(title, content)))
				.andExpect(status().isCreated()).andReturn();
	}

	private String id(MvcResult res) throws Exception {
		return json.readTree(res.getResponse().getContentAsString()).get("id").asString();
	}

	@Test
	void registerCreatesUserAndReturns201() throws Exception {
		var user = unique();
		mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content(creds(user, "password123")))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").isNotEmpty())
				.andExpect(jsonPath("$.username").value(user))
				.andExpect(jsonPath("$.roles", contains("USER")))
				.andExpect(jsonPath("$.passwordHash").doesNotExist());
	}

	@Test
	void registerDuplicateUsernameReturns409() throws Exception {
		var body = creds(unique(), "password123");
		mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated());
		mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isConflict());
	}

	@Test
	void registerWithShortPasswordReturns400() throws Exception {
		mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content(creds(unique(), "short")))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
	}

	@Test
	void loginReturnsJwtToken() throws Exception {
		var user = unique();
		mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content(creds(user, "password123")));
		mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content(creds(user, "password123")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.token", matchesRegex("[\\w-]+\\.[\\w-]+\\.[\\w-]+")))
				.andExpect(jsonPath("$.expiresIn", greaterThan(0)));
	}

	@Test
	void loginWithWrongPasswordReturns401() throws Exception {
		var user = unique();
		mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content(creds(user, "password123")));
		mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content(creds(user, "wrongpass1")))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void notesWithoutTokenReturns401() throws Exception {
		mvc.perform(get("/notes")).andExpect(status().isUnauthorized());
		mvc.perform(get("/notes").header("Authorization", "Bearer not.a.jwt")).andExpect(status().isUnauthorized());
	}

	@Test
	void createNoteReturns201WithAuditFields() throws Exception {
		var t = token();
		mvc.perform(post("/notes").header("Authorization", "Bearer " + t).contentType(MediaType.APPLICATION_JSON)
				.content("{\"title\":\"Hello\",\"content\":\"World\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").isNotEmpty())
				.andExpect(jsonPath("$.owner").isNotEmpty())
				.andExpect(jsonPath("$.title").value("Hello"))
				.andExpect(jsonPath("$.content").value("World"))
				.andExpect(jsonPath("$.createdAt").isNotEmpty())
				.andExpect(jsonPath("$.updatedAt").isNotEmpty());
	}

	@Test
	void createNoteWithBlankTitleReturns400() throws Exception {
		mvc.perform(post("/notes").header("Authorization", "Bearer " + token()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"title\":\"  \",\"content\":\"x\"}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void listNotesReturnsOnlyOwnNotesNewestFirst() throws Exception {
		var mine = token();
		var other = token();
		createNote(mine, "first", "a");
		createNote(mine, "second", "b");
		createNote(other, "theirs", "c");
		mvc.perform(get("/notes").header("Authorization", "Bearer " + mine))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content", hasSize(2)))
				.andExpect(jsonPath("$.content[0].title").value("second"))
				.andExpect(jsonPath("$.content[1].title").value("first"))
				.andExpect(jsonPath("$.page.size").value(20))
				.andExpect(jsonPath("$.page.number").value(0))
				.andExpect(jsonPath("$.page.totalElements").value(2))
				.andExpect(jsonPath("$.page.totalPages").value(1));
	}

	@Test
	void searchNotesUsesTextIndex() throws Exception {
		var t = token();
		createNote(t, "Kafka tips", "streams");
		createNote(t, "Redis tips", "cache");
		mvc.perform(get("/notes").param("q", "kafka").header("Authorization", "Bearer " + t))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content", hasSize(1)))
				.andExpect(jsonPath("$.content[0].title").value("Kafka tips"));
	}

	@Test
	void getOtherUsersNoteReturns404() throws Exception {
		var id = id(createNote(token(), "secret", "x"));
		var other = token();
		mvc.perform(get("/notes/" + id).header("Authorization", "Bearer " + other)).andExpect(status().isNotFound());
		mvc.perform(put("/notes/" + id).header("Authorization", "Bearer " + other).contentType(MediaType.APPLICATION_JSON)
				.content("{\"title\":\"h\",\"content\":\"x\"}")).andExpect(status().isNotFound());
		mvc.perform(delete("/notes/" + id).header("Authorization", "Bearer " + other)).andExpect(status().isNotFound());
		mvc.perform(get("/notes/doesnotexist").header("Authorization", "Bearer " + other)).andExpect(status().isNotFound());
	}

	@Test
	void updateNoteChangesContentAndUpdatedAt() throws Exception {
		var t = token();
		var created = createNote(t, "v1", "old");
		var createdAt = json.readTree(created.getResponse().getContentAsString()).get("updatedAt").asString();
		Thread.sleep(5);
		mvc.perform(put("/notes/" + id(created)).header("Authorization", "Bearer " + t).contentType(MediaType.APPLICATION_JSON)
				.content("{\"title\":\"v2\",\"content\":\"new\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.title").value("v2"))
				.andExpect(jsonPath("$.content").value("new"))
				.andExpect(jsonPath("$.updatedAt", not(createdAt)));
	}

	@Test
	void deleteNoteReturns204ThenGetReturns404() throws Exception {
		var t = token();
		var id = id(createNote(t, "bye", "x"));
		mvc.perform(delete("/notes/" + id).header("Authorization", "Bearer " + t)).andExpect(status().isNoContent());
		mvc.perform(get("/notes/" + id).header("Authorization", "Bearer " + t)).andExpect(status().isNotFound());
	}

	@Test
	void adminEndpointForbiddenForUser() throws Exception {
		mvc.perform(get("/admin/notes").header("Authorization", "Bearer " + token())).andExpect(status().isForbidden());
	}

	@Test
	void adminEndpointListsAllNotesForSeededAdmin() throws Exception {
		createNote(token(), "a", "x");
		createNote(token(), "b", "y");
		var admin = tokenFor("admin", "admin-dev-password");
		mvc.perform(get("/admin/notes").header("Authorization", "Bearer " + admin))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(2))));
	}

	@Test
	void corsPreflightAllowsAngularDevOrigin() throws Exception {
		mvc.perform(options("/notes").header("Origin", "http://localhost:4200")
				.header("Access-Control-Request-Method", "POST")
				.header("Access-Control-Request-Headers", "Authorization, Content-Type"))
				.andExpect(status().isOk())
				.andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:4200"))
				.andExpect(header().string("Access-Control-Allow-Headers", containsStringIgnoringCase("authorization")));
	}
}
