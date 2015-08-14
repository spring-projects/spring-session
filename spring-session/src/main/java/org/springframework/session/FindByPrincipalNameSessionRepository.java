/*
 * Copyright 2002-2015 the original author or authors.
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
package org.springframework.session;

import java.util.Map;

/**
 * Extends a basic {@link SessionRepository} to allow finding a session id by
 * the principal name. The principal name is defined by the {@link Session}
 * attribute with the name {@link Session#PRINCIPAL_NAME_ATTRIBUTE_NAME}.
 *
 * @author Rob Winch
 *
 * @param <S>
 *            the type of Session being managed by this
 *            {@link FindByPrincipalNameSessionRepository}
 */
public interface FindByPrincipalNameSessionRepository<S extends Session> extends SessionRepository<S> {

	/**
	 * Find a Map of the session id to the {@link Session} of all sessions that
	 * contain the session attribute with the name
	 * {@link Session#PRINCIPAL_NAME_ATTRIBUTE_NAME} and the value of the
	 * specified principal name.
	 *
	 * @param principalName
	 *            the principal name (i.e. username) to search for
	 * @return a Map (never null) of the session id to the {@link Session} of
	 *         all sessions that contain the session attribute with the name
	 *         {@link Session#PRINCIPAL_NAME_ATTRIBUTE_NAME} and the value of
	 *         the specified principal name. If no results are found, an empty
	 *         Map is returned.
	 */
	Map<String, S> findByPrincipalName(String principalName);
}