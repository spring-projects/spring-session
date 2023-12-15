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

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.util.Assert;

/**
 * A {@link PersistentSessionPrincipalRestorer} strategy that uses a
 * {@link UserDetailsService} to get the principal and use it to store a
 * {@link PersistentAuthenticationToken} in the session's
 * {@link org.springframework.security.core.context.SecurityContext} attribute.
 *
 * @since 3.3
 * @author Marcus da Coregio
 */
public final class SecurityContextAttributePersistentSessionRestorer implements PersistentSessionPrincipalRestorer {

	private final UserDetailsService userDetailsService;

	private String securityContextAttributeName = "SPRING_SECURITY_CONTEXT";

	public SecurityContextAttributePersistentSessionRestorer(UserDetailsService userDetailsService) {
		Assert.notNull(userDetailsService, "userDetailsService cannot be null");
		this.userDetailsService = userDetailsService;
	}

	@Override
	public void restore(String principal, PersistentSessionRepository.PersistentSession session) {
		UserDetails userDetails = getUserDetails(principal);
		PersistentAuthenticationToken authentication = new PersistentAuthenticationToken(userDetails, session.getId());
		SecurityContextImpl securityContext = new SecurityContextImpl();
		securityContext.setAuthentication(authentication);
		session.setAttribute(this.securityContextAttributeName, securityContext);
	}

	/**
	 * The attribute name where the {@link SecurityContext} will be stored.
	 * @param securityContextAttributeName the attribute name. Cannot be empty.
	 */
	public void setSecurityContextAttributeName(String securityContextAttributeName) {
		Assert.hasText(securityContextAttributeName, "securityContextAttributeName cannot be empty");
		this.securityContextAttributeName = securityContextAttributeName;
	}

	private UserDetails getUserDetails(String username) {
		try {
			return this.userDetailsService.loadUserByUsername(username);
		}
		catch (UsernameNotFoundException ex) {
			throw new PrincipalRestoreException("Could not find UserDetails for principal", ex);
		}
	}

	public static final class PersistentAuthenticationToken extends UsernamePasswordAuthenticationToken {

		public PersistentAuthenticationToken(UserDetails userDetails, String credentials) {
			super(userDetails, credentials, userDetails.getAuthorities());
		}

	}

}
