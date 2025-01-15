/*
 * Copyright 2014-2024 the original author or authors.
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

package com.example;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.csrf;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@Import(TestcontainersConfig.class)
class SpringSessionSampleBootReactiveMaxSessionsTests {

	@Autowired
	WebTestClient client;

	@Autowired
	ReactiveRedisConnectionFactory redisConnectionFactory;

	@BeforeEach
	void setup() {
		this.redisConnectionFactory.getReactiveConnection().serverCommands().flushAll().block();
	}

	@Test
	void loginWhenUserAndMaximumSessionsOf1ExceededThenSecondLoginProhibited() {
		MultiValueMap<String, String> data = new LinkedMultiValueMap<>();
		data.add("username", "user");
		data.add("password", "password");

		ResponseCookie firstLoginCookie = loginReturningCookie(data);
		login(data).expectStatus().isFound().expectHeader().location("/login?error");

		performHello(firstLoginCookie).expectStatus().isOk();
	}

	@Test
	void loginWhenUserAndMaximumSessionsOf1ExceededThenSecondAndThirdLoginProhibited() {
		MultiValueMap<String, String> data = new LinkedMultiValueMap<>();
		data.add("username", "user");
		data.add("password", "password");

		ResponseCookie firstLoginCookie = loginReturningCookie(data);
		ResponseCookie secondLoginCookie = login(data).expectStatus()
			.isFound()
			.expectHeader()
			.location("/login?error")
			.returnResult(Void.class)
			.getResponseCookies()
			.getFirst("SESSION");
		ResponseCookie thirdLoginCookie = login(data).expectStatus()
			.isFound()
			.expectHeader()
			.location("/login?error")
			.returnResult(Void.class)
			.getResponseCookies()
			.getFirst("SESSION");
		assertThat(secondLoginCookie).isNull();
		assertThat(thirdLoginCookie).isNull();

		performHello(firstLoginCookie).expectStatus().isOk().expectBody(String.class).isEqualTo("Hello!");
	}

	@Test
	void loginWhenAuthenticationHasUnlimitedSessionsThenLoginIsAlwaysAllowed() {
		MultiValueMap<String, String> data = new LinkedMultiValueMap<>();
		data.add("username", "unlimited");
		data.add("password", "password");

		ResponseCookie firstLoginCookie = loginReturningCookie(data);
		ResponseCookie secondLoginCookie = loginReturningCookie(data);
		ResponseCookie thirdLoginCookie = loginReturningCookie(data);
		ResponseCookie fourthLoginCookie = loginReturningCookie(data);
		ResponseCookie fifthLoginCookie = loginReturningCookie(data);

		performHello(firstLoginCookie).expectStatus().isOk().expectBody(String.class).isEqualTo("Hello!");
		performHello(secondLoginCookie).expectStatus().isOk().expectBody(String.class).isEqualTo("Hello!");
		performHello(thirdLoginCookie).expectStatus().isOk().expectBody(String.class).isEqualTo("Hello!");
		performHello(fourthLoginCookie).expectStatus().isOk().expectBody(String.class).isEqualTo("Hello!");
		performHello(fifthLoginCookie).expectStatus().isOk().expectBody(String.class).isEqualTo("Hello!");
	}

	private WebTestClient.ResponseSpec performHello(ResponseCookie cookie) {
		return this.client.get().uri("/hello").cookie(cookie.getName(), cookie.getValue()).exchange();
	}

	private ResponseCookie loginReturningCookie(MultiValueMap<String, String> data) {
		return login(data).expectCookie()
			.exists("SESSION")
			.returnResult(Void.class)
			.getResponseCookies()
			.getFirst("SESSION");
	}

	private WebTestClient.ResponseSpec login(MultiValueMap<String, String> data) {
		return this.client.mutateWith(csrf())
			.post()
			.uri("/login")
			.contentType(MediaType.MULTIPART_FORM_DATA)
			.body(BodyInserters.fromFormData(data))
			.exchange();
	}

}
