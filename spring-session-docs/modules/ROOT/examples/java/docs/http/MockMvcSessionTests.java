/*
 * Copyright 2014-present the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package docs.http;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.session.MapSession;
import org.springframework.session.MapSessionRepository;
import org.springframework.session.config.annotation.web.http.EnableSpringHttpSession;
import org.springframework.session.web.http.SessionRepositoryFilter;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Demonstrates how to provide an existing session to a request when testing with
 * {@link MockMvc} and {@link SessionRepositoryFilter}.
 *
 * @author Matheus Leal
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration
@WebAppConfiguration
class MockMvcSessionTests {

	@Autowired
	WebApplicationContext context;

	@Autowired
	SessionRepositoryFilter<?> springSessionRepositoryFilter;

	@Autowired
	MapSessionRepository sessionRepository;

	private MockMvc mockMvc;

	@BeforeEach
	void setup() {
		this.mockMvc = MockMvcBuilders.webAppContextSetup(this.context)
			.addFilters(this.springSessionRepositoryFilter)
			.build();
	}

	@Test
	void mockMvcSessionIsIgnoredBySessionRepositoryFilter() throws Exception {
		MockHttpSession mockSession = new MockHttpSession();
		mockSession.setAttribute("username", "rob");
		String username = this.mockMvc.perform(get("/username").session(mockSession))
			.andReturn()
			.getResponse()
			.getContentAsString();
		assertThat(username).isEmpty();
	}

	@Test
	void sessionCookieProvidesExistingSession() throws Exception {
		// tag::session-cookie[]
		MapSession session = this.sessionRepository.createSession();
		session.setAttribute("username", "rob");
		this.sessionRepository.save(session);
		String cookieValue = Base64.getEncoder().encodeToString(session.getId().getBytes(StandardCharsets.UTF_8));

		String username = this.mockMvc.perform(get("/username").cookie(new Cookie("SESSION", cookieValue)))
			.andReturn()
			.getResponse()
			.getContentAsString();
		assertThat(username).isEqualTo("rob");
		// end::session-cookie[]
	}

	@Configuration
	@EnableWebMvc
	@EnableSpringHttpSession
	static class Config {

		@Bean
		MapSessionRepository sessionRepository() {
			return new MapSessionRepository(new ConcurrentHashMap<>());
		}

		@Bean
		UsernameController usernameController() {
			return new UsernameController();
		}

	}

	@RestController
	static class UsernameController {

		@GetMapping("/username")
		String username(HttpSession session) {
			Object username = session.getAttribute("username");
			return (username != null) ? username.toString() : "";
		}

	}

}
