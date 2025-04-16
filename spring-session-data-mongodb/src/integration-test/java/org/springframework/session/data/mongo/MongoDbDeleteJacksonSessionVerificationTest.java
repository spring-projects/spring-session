/*
 * Copyright 2014-2025 the original author or authors.
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

package org.springframework.session.data.mongo;

import java.net.URI;

import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import org.assertj.core.api.AssertionsForClassTypes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MongoDBContainer;
import reactor.test.StepVerifier;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.ReactiveMongoOperations;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.userdetails.MapReactiveUserDetailsService;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.session.data.mongo.config.annotation.web.reactive.EnableMongoWebSession;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.web.reactive.server.FluxExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.config.EnableWebFlux;
import org.springframework.web.reactive.function.BodyInserters;

/**
 * @author Boris Finkelshteyn
 */
@SpringJUnitConfig
class MongoDbDeleteJacksonSessionVerificationTest {

	@Autowired
	ApplicationContext ctx;

	WebTestClient client;

	@BeforeEach
	void setUp() {
		this.client = WebTestClient.bindToApplicationContext(this.ctx).build();
	}

	@Test
	void logoutShouldDeleteOldSessionFromMongoDB() {

		// 1. Login and capture the SESSION cookie value.
		// @formatter:off
		FluxExchangeResult<String> loginResult = this.client.post().uri("/login")
				.contentType(MediaType.APPLICATION_FORM_URLENCODED)
				.body(BodyInserters
						.fromFormData("username", "admin")
						.with("password", "password"))
				.exchange()
				.returnResult(String.class);
		// @formatter:on

		AssertionsForClassTypes.assertThat(loginResult.getResponseHeaders().getLocation()).isEqualTo(URI.create("/"));

		String originalSessionId = loginResult.getResponseCookies().getFirst("SESSION").getValue();

		// 2. Fetch a protected resource using the SESSION cookie.
		// @formatter:off
		this.client.get().uri("/hello")
				.cookie("SESSION", originalSessionId)
				.exchange()
				.expectStatus().isOk()
				.returnResult(String.class).getResponseBody()
				.as(StepVerifier::create)
				.expectNext("HelloWorld")
				.verifyComplete();
		// @formatter:on

		// 3. Logout using the SESSION cookie, and capture the new SESSION cookie.
		// @formatter:off
		String newSessionId = this.client.post().uri("/logout")
				.cookie("SESSION", originalSessionId)
				.exchange()
				.expectStatus().isFound()
				.returnResult(String.class).getResponseCookies().getFirst("SESSION").getValue();
		// @formatter:on

		AssertionsForClassTypes.assertThat(newSessionId).isNotEqualTo(originalSessionId);

		// 4. Verify the new SESSION cookie is not yet authorized.
		// @formatter:off
		this.client.get().uri("/hello")
				.cookie("SESSION", newSessionId)
				.exchange()
				.expectStatus().isFound()
				.expectHeader()
				.value(HttpHeaders.LOCATION, (value) -> AssertionsForClassTypes.assertThat(value).isEqualTo("/login"));
		// @formatter:on

		// 5. Verify the original SESSION cookie no longer works.
		// @formatter:off
		this.client.get().uri("/hello")
				.cookie("SESSION", originalSessionId)
				.exchange()
				.expectStatus().isFound()
				.expectHeader()
				.value(HttpHeaders.LOCATION, (value) -> AssertionsForClassTypes.assertThat(value).isEqualTo("/login"));
		// @formatter:on
	}

	@RestController
	static class TestController {

		@GetMapping("/hello")
		ResponseEntity<String> hello() {
			return ResponseEntity.ok("HelloWorld");
		}

	}

	@Configuration(proxyBeanMethods = false)
	@EnableWebFluxSecurity
	static class SecurityConfig {

		@Bean
		SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
			// @formatter:off
			return http
					.logout(Customizer.withDefaults())
					.formLogin(Customizer.withDefaults())
					.csrf((csrf) -> csrf.disable())
					.authorizeExchange((ae) -> ae.anyExchange().authenticated())
					.build();
			// @formatter:on
		}

		@Bean
		MapReactiveUserDetailsService userDetailsService() {
			// @formatter:off
			return new MapReactiveUserDetailsService(User.withUsername("admin")
					.password("{noop}password")
					.roles("USER,ADMIN")
					.build());
			// @formatter:on
		}

		@Bean
		AbstractMongoSessionConverter mongoSessionConverter() {
			return new JacksonMongoSessionConverter();
		}

	}

	@Configuration
	@EnableWebFlux
	@EnableMongoWebSession
	static class Config {

		private static final String DOCKER_IMAGE = "mongo:5.0.11";

		@Bean
		MongoDBContainer mongoDbContainer() {
			MongoDBContainer mongoDbContainer = new MongoDBContainer(DOCKER_IMAGE);
			mongoDbContainer.start();
			return mongoDbContainer;
		}

		@Bean
		ReactiveMongoOperations mongoOperations(MongoDBContainer mongoContainer) {

			MongoClient mongo = MongoClients
				.create("mongodb://" + mongoContainer.getHost() + ":" + mongoContainer.getFirstMappedPort());
			return new ReactiveMongoTemplate(mongo, "DB_Name_DeleteJacksonSessionVerificationTest");
		}

		@Bean
		TestController controller() {
			return new TestController();
		}

	}

}
