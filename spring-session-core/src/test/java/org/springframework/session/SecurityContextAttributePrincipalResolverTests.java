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

import org.junit.jupiter.api.Test;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextImpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * Tests for {@link SecurityContextAttributePrincipalNameResolver}
 *
 * @author Marcus da Coregio
 */
class SecurityContextAttributePrincipalResolverTests {

	SecurityContextAttributePrincipalNameResolver resolver = new SecurityContextAttributePrincipalNameResolver();

	@Test
	void resolveWhenSecurityContextContainsAuthenticationThenUsernameResolved() {
		Authentication authentication = createAuthentication("user");
		SecurityContextImpl securityContext = new SecurityContextImpl();
		securityContext.setAuthentication(authentication);
		PersistentSessionRepository.PersistentSession session = mock();
		given(session.getAttribute("SPRING_SECURITY_CONTEXT")).willReturn(securityContext);
		String resolvedUsername = this.resolver.resolve(session);
		assertThat(resolvedUsername).isEqualTo(authentication.getName());
	}

	@Test
	void resolveWhenNoSecurityContextThenNull() {
		PersistentSessionRepository.PersistentSession session = mock();
		given(session.getAttribute("SPRING_SECURITY_CONTEXT")).willReturn(null);
		String resolvedUsername = this.resolver.resolve(session);
		assertThat(resolvedUsername).isNull();
		;
	}

	@Test
	void resolveWhenNullAuthenticationThenNull() {
		SecurityContextImpl securityContext = new SecurityContextImpl();
		PersistentSessionRepository.PersistentSession session = mock();
		given(session.getAttribute("SPRING_SECURITY_CONTEXT")).willReturn(securityContext);
		String resolvedUsername = this.resolver.resolve(session);
		assertThat(resolvedUsername).isNull();
	}

	@Test
	void resolveWhenAnonymousAuthenticationThenNull() {
		Authentication authentication = new AnonymousAuthenticationToken("key", "anonymous",
				AuthorityUtils.createAuthorityList("ANY"));
		SecurityContextImpl securityContext = new SecurityContextImpl();
		securityContext.setAuthentication(authentication);
		PersistentSessionRepository.PersistentSession session = mock();
		given(session.getAttribute("SPRING_SECURITY_CONTEXT")).willReturn(securityContext);
		String resolvedUsername = this.resolver.resolve(session);
		assertThat(resolvedUsername).isNull();
	}

	@Test
	void resolveWhenSecurityContextContainsAuthenticationAndCustomAttributeNameThenUsernameResolved() {
		Authentication authentication = createAuthentication("user");
		SecurityContextImpl securityContext = new SecurityContextImpl();
		securityContext.setAuthentication(authentication);
		PersistentSessionRepository.PersistentSession session = mock();
		given(session.getAttribute("MY_ATTRIBUTE_NAME")).willReturn(securityContext);
		this.resolver.setSecurityContextAttributeName("MY_ATTRIBUTE_NAME");
		String resolvedUsername = this.resolver.resolve(session);
		assertThat(resolvedUsername).isEqualTo(authentication.getName());
	}

	private Authentication createAuthentication(String username) {
		return UsernamePasswordAuthenticationToken.authenticated(username, "N/A",
				AuthorityUtils.createAuthorityList("ANY"));
	}

}
