/*
 * Copyright 2002-2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.session.security;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.MapSession;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link SpringSecurityPrincipalNameResolver}.
 *
 * @author Rob Winch
 * @author Vedran Pavic
 */
@RunWith(MockitoJUnitRunner.class)
public class SpringSecurityPrincipalNameResolverTests {

	private static final String SPRING_SECURITY_CONTEXT = "SPRING_SECURITY_CONTEXT";

	private SpringSecurityPrincipalNameResolver resolver = new SpringSecurityPrincipalNameResolver();

	@Test
	public void resolvePrincipalIndex() {
		String username = "username";
		MapSession session = new MapSession();
		session.setAttribute(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME, username);

		assertThat(this.resolver.resolvePrincipal(session)).isEqualTo(username);
	}

	@Test
	public void resolveIndexOnSecurityContext() {
		String principal = "resolveIndexOnSecurityContext";
		Authentication authentication = new UsernamePasswordAuthenticationToken(
				principal, "notused", AuthorityUtils.createAuthorityList("ROLE_USER"));
		SecurityContext context = new SecurityContextImpl();
		context.setAuthentication(authentication);

		MapSession session = new MapSession();
		session.setAttribute(SPRING_SECURITY_CONTEXT, context);

		assertThat(this.resolver.resolvePrincipal(session)).isEqualTo(principal);
	}

}
