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

package org.springframework.session;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextImpl;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link PrincipalNameIndexResolver}.
 *
 * @author Vedran Pavic
 */
class PrincipalNameIndexResolverTests {

	private static final String PRINCIPAL_NAME = "principalName";

	private static final String SPRING_SECURITY_CONTEXT = "SPRING_SECURITY_CONTEXT";

	private PrincipalNameIndexResolver<Session> indexResolver;

	@BeforeEach
	void setUp() {
		this.indexResolver = new PrincipalNameIndexResolver<>();
	}

	@Test
	void resolveFromPrincipalName() {
		MapSession session = new MapSession("1");
		session.setAttribute(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME, PRINCIPAL_NAME);
		assertThat(this.indexResolver.resolveIndexValueFor(session)).isEqualTo(PRINCIPAL_NAME);
	}

	@Test
	void resolveFromSpringSecurityContext() {
		Authentication authentication = new UsernamePasswordAuthenticationToken(PRINCIPAL_NAME, "notused",
				AuthorityUtils.createAuthorityList("ROLE_USER"));
		SecurityContext context = new SecurityContextImpl();
		context.setAuthentication(authentication);
		MapSession session = new MapSession("1");
		session.setAttribute(SPRING_SECURITY_CONTEXT, context);
		assertThat(this.indexResolver.resolveIndexValueFor(session)).isEqualTo(PRINCIPAL_NAME);
	}

}
