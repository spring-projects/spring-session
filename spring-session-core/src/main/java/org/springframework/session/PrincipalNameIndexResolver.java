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

import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;

/**
 * {@link IndexResolver} to resolve the principal name from session attribute named
 * {@link FindByIndexNameSessionRepository#PRINCIPAL_NAME_INDEX_NAME} or Spring Security
 * context stored in the session under {@code SPRING_SECURITY_CONTEXT} attribute.
 *
 * @param <S> the type of Session being handled
 * @author Vedran Pavic
 * @since 2.2.0
 */
public class PrincipalNameIndexResolver<S extends Session> extends SingleIndexResolver<S> {

	private static final String SPRING_SECURITY_CONTEXT = "SPRING_SECURITY_CONTEXT";

	private static final Expression expression = new SpelExpressionParser().parseExpression("authentication?.name");

	public PrincipalNameIndexResolver() {
		super(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME);
	}

	/**
	 * Create a new instance specifying the name of the index to be resolved.
	 * @param indexName the name of the index to be resolved
	 * @since 3.3
	 */
	public PrincipalNameIndexResolver(String indexName) {
		super(indexName);
	}

	public String resolveIndexValueFor(S session) {
		String principalName = session.getAttribute(getIndexName());
		if (principalName != null) {
			return principalName;
		}
		Object authentication = session.getAttribute(SPRING_SECURITY_CONTEXT);
		if (authentication != null) {
			return expression.getValue(authentication, String.class);
		}
		return null;
	}

}
