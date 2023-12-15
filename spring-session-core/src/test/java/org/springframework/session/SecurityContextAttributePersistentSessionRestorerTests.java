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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Tests for {@link SecurityContextAttributePersistentSessionRestorer}
 *
 * @author Marcus da Coregio
 */
class SecurityContextAttributePersistentSessionRestorerTests {

	private SecurityContextAttributePersistentSessionRestorer restorer;

	@BeforeEach
	void setup() {
		this.restorer = new SecurityContextAttributePersistentSessionRestorer(userDetailsService());
	}

	@Test
	void restoreWhenUsernameExistsThenRestored() {
		PersistentSessionRepository.PersistentSession session = mock();
		this.restorer.restore("user", session);
		ArgumentCaptor<SecurityContext> captor = ArgumentCaptor.forClass(SecurityContext.class);
		verify(session).setAttribute(eq("SPRING_SECURITY_CONTEXT"), captor.capture());
		assertThat(captor.getValue().getAuthentication().getName()).isEqualTo("user");
	}

	@Test
	void restoreWhenUsernameExistsAndCustomAttributeNameThenRestored() {
		PersistentSessionRepository.PersistentSession session = mock();
		this.restorer.setSecurityContextAttributeName("MY_ATTRIBUTE_NAME");
		this.restorer.restore("user", session);
		ArgumentCaptor<SecurityContext> captor = ArgumentCaptor.forClass(SecurityContext.class);
		verify(session).setAttribute(eq("MY_ATTRIBUTE_NAME"), captor.capture());
		assertThat(captor.getValue().getAuthentication().getName()).isEqualTo("user");
	}

	@Test
	void restoreWhenUsernameDoesNotExistThenException() {
		PersistentSessionRepository.PersistentSession session = mock();
		assertThatExceptionOfType(PrincipalRestoreException.class)
			.isThrownBy(() -> this.restorer.restore("unknown", session));
	}

	UserDetailsService userDetailsService() {
		UserDetails user = User.withDefaultPasswordEncoder()
			.username("user")
			.password("password")
			.roles("USER")
			.build();
		return new InMemoryUserDetailsManager(user);
	}

}
