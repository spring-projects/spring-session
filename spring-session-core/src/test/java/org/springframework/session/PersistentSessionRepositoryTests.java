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

package org.springframework.session;

import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;

import javax.crypto.KeyGenerator;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.session.PersistentSessionRepository.PersistentSession;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.Mockito.mock;

/**
 * Tests for {@link PersistentSessionRepository}
 *
 * @author Marcus da Coregio
 */
class PersistentSessionRepositoryTests {

	private PersistentSessionRepository repository;

	UserDetails user = User.withUsername("user").password("password").roles("USER").build();

	UserDetails anotheruser = User.withUsername("anotheruser").password("password").roles("USER").build();

	@BeforeEach
	void setup() throws NoSuchAlgorithmException {
		this.repository = new PersistentSessionRepository(KeyGenerator.getInstance("AES").generateKey(),
				new SecurityContextAttributePrincipalNameResolver(),
				new SecurityContextAttributePersistentSessionRestorer(userDetailsService()));
	}

	@Test
	void constructorWhenNotExpectedKeyLengthThenException() {
		assertThatIllegalStateException()
			.isThrownBy(() -> new PersistentSessionRepository(
					new SecretKeySpec("2f12cb0f1d2e3d12345f1af2b123d".getBytes(), "AES"), mock(), mock()))
			.withMessage("key must be 16, 24 or 32 bytes in length");
	}

	@ParameterizedTest
	@ValueSource(strings = { "2f12cb0f1d2e3d12345f1af2b123dce4", "2f12cb0f1d2e3d12345f1af2", "2f12cb0f1d2e3d12" })
	void constructorWhenExpectedKeyLengthThenSuccess(String key) {
		assertThatNoException().isThrownBy(
				() -> new PersistentSessionRepository(new SecretKeySpec(key.getBytes(), "AES"), mock(), mock()));
	}

	@Test
	void securityContextPresentWhenSaveAndFindByIdThenDecryptSuccessful() {
		SecurityContextImpl securityContext = new SecurityContextImpl();
		securityContext.setAuthentication(createAuthentication(this.user));
		PersistentSession session = this.repository.createSession();
		session.setAttribute("SPRING_SECURITY_CONTEXT", securityContext);
		Duration maxInactiveInterval = session.getMaxInactiveInterval();
		Instant lastAccessedTime = session.getLastAccessedTime();
		this.repository.save(session);
		PersistentSession decryptedSession = this.repository.findById(session.getId());
		securityContext = decryptedSession.getAttribute("SPRING_SECURITY_CONTEXT");
		assertThat(securityContext).isNotNull();
		assertThat(securityContext.getAuthentication()).isNotNull();
		assertThat(securityContext.getAuthentication().getName()).isEqualTo(this.user.getUsername());
		assertThat(securityContext.getAuthentication())
			.isInstanceOf(SecurityContextAttributePersistentSessionRestorer.PersistentAuthenticationToken.class);
		assertThat(decryptedSession.getMaxInactiveInterval()).isEqualTo(maxInactiveInterval);
		assertThat(decryptedSession.getLastAccessedTime().truncatedTo(ChronoUnit.SECONDS))
			.isEqualTo(lastAccessedTime.truncatedTo(ChronoUnit.SECONDS));
		Instant expectedExpireAt = (Instant) ReflectionTestUtils.getField(session, "expireAt");
		Instant actualExpireAt = (Instant) ReflectionTestUtils.getField(decryptedSession, "expireAt");
		assertThat(actualExpireAt).usingComparator(new InstantComparator()).isEqualTo(expectedExpireAt);
	}

	@Test
	void saveTwiceWhenNotInNewIdThresholdThenIdStaysTheSame() {
		SecurityContextImpl securityContext = new SecurityContextImpl();
		securityContext.setAuthentication(createAuthentication(this.user));
		PersistentSession session = this.repository.createSession();
		session.setAttribute("SPRING_SECURITY_CONTEXT", securityContext);
		this.repository.save(session);
		String savedId = session.getId();
		this.repository.save(session);
		String secondSaveId = session.getId();
		assertThat(secondSaveId).isEqualTo(savedId);
	}

	@Test
	void saveTwiceWhenInNewIdThresholdThenIdChanges() {
		SecurityContextImpl securityContext = new SecurityContextImpl();
		securityContext.setAuthentication(createAuthentication(this.user));
		PersistentSession session = this.repository.createSession();
		session.setAttribute("SPRING_SECURITY_CONTEXT", securityContext);
		this.repository.save(session);
		String savedId = session.getId();
		this.repository.setClock(Clock.fixed(Instant.now().plus(2, ChronoUnit.MINUTES), ZoneOffset.UTC));
		this.repository.save(session);
		String secondSaveId = session.getId();
		assertThat(secondSaveId).isNotEmpty();
		assertThat(secondSaveId).isNotEqualTo(savedId);
	}

	@Test
	void securityContextPresentWhenSaveAndFindByIdAndUsernameNotFoundThenNull() {
		SecurityContextImpl securityContext = new SecurityContextImpl();
		securityContext.setAuthentication(UsernamePasswordAuthenticationToken.authenticated("unknown", "N/A",
				AuthorityUtils.createAuthorityList("AUTHORITY")));
		PersistentSession session = this.repository.createSession();
		session.setAttribute("SPRING_SECURITY_CONTEXT", securityContext);
		this.repository.save(session);
		PersistentSession decryptedSession = this.repository.findById(session.getId());
		assertThat(decryptedSession).isNull();
	}

	@Test
	void securityContextPresentWhenSaveAndFindByIdAndSessionExpiredThenNull() {
		SecurityContextImpl securityContext = new SecurityContextImpl();
		securityContext.setAuthentication(createAuthentication(this.user));
		PersistentSession session = this.repository.createSession();
		session.setAttribute("SPRING_SECURITY_CONTEXT", securityContext);
		this.repository.save(session);
		this.repository.setClock(Clock.fixed(Instant.now().plus(31, ChronoUnit.MINUTES), ZoneOffset.UTC));
		PersistentSession decryptedSession = this.repository.findById(session.getId());
		assertThat(decryptedSession).isNull();
	}

	@Test
	void saveWhenNoSecurityContextThenEmptyId() {
		PersistentSession session = this.repository.createSession();
		this.repository.save(session);
		assertThat(session.getId()).isEmpty();
	}

	@Test
	void saveWhenSecurityContextWithNoAuthenticationThenEmptyId() {
		SecurityContextImpl securityContext = new SecurityContextImpl();
		PersistentSession session = this.repository.createSession();
		session.setAttribute("SPRING_SECURITY_CONTEXT", securityContext);
		this.repository.save(session);
		assertThat(session.getId()).isEmpty();
	}

	@Test
	void saveWhenSecurityContextAndAnonymousAuthenticationThenEmptyId() {
		SecurityContextImpl securityContext = new SecurityContextImpl();
		securityContext.setAuthentication(
				new AnonymousAuthenticationToken("key", "anonymous", AuthorityUtils.createAuthorityList("ANY")));
		PersistentSession session = this.repository.createSession();
		session.setAttribute("SPRING_SECURITY_CONTEXT", securityContext);
		this.repository.save(session);
		assertThat(session.getId()).isEmpty();
	}

	private UsernamePasswordAuthenticationToken createAuthentication(UserDetails user) {
		return UsernamePasswordAuthenticationToken.authenticated(user, "N/A", user.getAuthorities());
	}

	private UserDetailsService userDetailsService() {
		return new InMemoryUserDetailsManager(this.user, this.anotheruser);
	}

	static class InstantComparator implements Comparator<Instant> {

		@Override
		public int compare(Instant o1, Instant o2) {
			return o1.truncatedTo(ChronoUnit.SECONDS).compareTo(o2.truncatedTo(ChronoUnit.SECONDS));
		}

	}

}
