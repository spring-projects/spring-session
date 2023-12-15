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

import org.springframework.security.authentication.AuthenticationTrustResolver;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.util.Assert;

/**
 * Resolves the principal by checking if the session contains a {@link SecurityContext}
 * attribute with a non-null and non-anonymous {@link Authentication}.
 *
 * @since 3.3
 * @author Marcus da Coregio
 */
public final class SecurityContextAttributePrincipalNameResolver implements PersistentSessionPrincipalNameResolver {

	private final AuthenticationTrustResolver trustResolver = new AuthenticationTrustResolverImpl();

	private String securityContextAttributeName = "SPRING_SECURITY_CONTEXT";

	@Override
	public String resolve(PersistentSessionRepository.PersistentSession session) {
		SecurityContext securityContext = session.getAttribute(this.securityContextAttributeName);
		Authentication authentication = securityContext != null ? securityContext.getAuthentication() : null;
		if (authentication == null || this.trustResolver.isAnonymous(authentication)) {
			return null;
		}
		return authentication.getName();
	}

	/**
	 * The attribute name used to find the {@link SecurityContext} in the session. Used to
	 * retrieve the authentication name.
	 * @param securityContextAttributeName the attribute name. Cannot be empty.
	 */
	public void setSecurityContextAttributeName(String securityContextAttributeName) {
		Assert.hasText(securityContextAttributeName, "securityContextAttributeName cannot be empty");
		this.securityContextAttributeName = securityContextAttributeName;
	}

}
