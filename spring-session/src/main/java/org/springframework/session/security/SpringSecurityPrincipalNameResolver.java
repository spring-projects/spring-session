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

import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.PrincipalNameResolver;
import org.springframework.session.Session;

/**
 * Implementation of {@link PrincipalNameResolver} which resolves the principal name from
 * Spring Security Context.
 *
 * @author Rob Winch
 * @author Vedran Pavic
 */
public class SpringSecurityPrincipalNameResolver implements PrincipalNameResolver {

	private static final String SPRING_SECURITY_CONTEXT = "SPRING_SECURITY_CONTEXT";

	private SpelExpressionParser parser = new SpelExpressionParser();

	@Override
	public String resolvePrincipal(Session session) {
		String principalName = session.getAttribute(
				FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME);
		if (principalName != null) {
			return principalName;
		}
		Object authentication = session.getAttribute(SPRING_SECURITY_CONTEXT);
		if (authentication != null) {
			Expression expression = parser.parseExpression("authentication?.name");
			return expression.getValue(authentication, String.class);
		}
		return null;
	}

}
