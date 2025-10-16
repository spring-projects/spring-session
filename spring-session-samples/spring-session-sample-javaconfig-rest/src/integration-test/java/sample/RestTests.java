/*
 * Copyright 2014-2019 the original author or authors.
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

package sample;

import java.util.Base64;
import java.util.Collections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * @author Pool Dolorier
 */
class RestTests {

	private static final String AUTHORIZATION = "Authorization";

	private static final String BASIC = "Basic ";

	private static final String X_AUTH_TOKEN = "X-Auth-Token";

	private RestTemplate restTemplate;

	private String baseUrl;

	@BeforeEach
	void setUp() {
		this.baseUrl = "http://localhost:" + System.getProperty("app.port");
		this.restTemplate = new RestTemplate();
	}

	@Test
	void unauthenticatedUserSentToLogInPage() {
		HttpHeaders headers = new HttpHeaders();
		headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
		assertThatExceptionOfType(HttpClientErrorException.class)
			.isThrownBy(() -> getForUser(this.baseUrl + "/", headers, String.class))
			.satisfies((e) -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED));
	}

	@Test
	void authenticateWithBasicWorks() {
		String auth = getAuth("user", "password");
		HttpHeaders headers = getHttpHeaders();
		headers.set(AUTHORIZATION, BASIC + auth);
		ResponseEntity<User> entity = getForUser(this.baseUrl + "/", headers, User.class);
		assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(entity.getHeaders().containsKey(X_AUTH_TOKEN)).isTrue();
		assertThat(entity.getBody().getUsername()).isEqualTo("user");
	}

	@Test
	void authenticateWithXAuthTokenWorks() {
		String auth = getAuth("user", "password");
		HttpHeaders headers = getHttpHeaders();
		headers.set(AUTHORIZATION, BASIC + auth);
		ResponseEntity<User> entity = getForUser(this.baseUrl + "/", headers, User.class);

		String token = entity.getHeaders().getFirst(X_AUTH_TOKEN);

		HttpHeaders authTokenHeader = new HttpHeaders();
		authTokenHeader.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
		authTokenHeader.set(X_AUTH_TOKEN, token);
		ResponseEntity<User> authTokenResponse = getForUser(this.baseUrl + "/", authTokenHeader, User.class);
		assertThat(authTokenResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(authTokenResponse.getBody().getUsername()).isEqualTo("user");
	}

	@Test
	void logout() {
		String auth = getAuth("user", "password");
		HttpHeaders headers = getHttpHeaders();
		headers.set(AUTHORIZATION, BASIC + auth);
		ResponseEntity<User> entity = getForUser(this.baseUrl + "/", headers, User.class);

		String token = entity.getHeaders().getFirst(X_AUTH_TOKEN);

		HttpHeaders logoutHeader = getHttpHeaders();
		logoutHeader.set(X_AUTH_TOKEN, token);
		ResponseEntity<User> logoutResponse = getForUser(this.baseUrl + "/logout", logoutHeader, User.class);
		assertThat(logoutResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
	}

	private <T> ResponseEntity<T> getForUser(String resourceUrl, HttpHeaders headers, Class<T> type) {
		return this.restTemplate.exchange(resourceUrl, HttpMethod.GET, new HttpEntity<T>(headers), type);
	}

	private HttpHeaders getHttpHeaders() {
		HttpHeaders headers = new HttpHeaders();
		headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
		return headers;
	}

	private String getAuth(String user, String password) {
		String auth = user + ":" + password;
		return Base64.getEncoder().encodeToString(auth.getBytes());
	}

}
