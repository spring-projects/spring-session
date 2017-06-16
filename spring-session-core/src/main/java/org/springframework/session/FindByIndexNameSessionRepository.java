/*
 * Copyright 2014-2016 the original author or authors.
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
 * Extends a basic {@link SessionRepository} to allow finding a session id by the
 * principal name. The principal name is defined by the {@link Session} attribute with the
 * name {@link FindByIndexNameSessionRepository#PRINCIPAL_NAME_INDEX_NAME}.
 *
 * @param <S> the type of Session being managed by this
 * {@link FindByIndexNameSessionRepository}
 * @author Rob Winch
 */
public interface FindByIndexNameSessionRepository<S extends Session>
		extends SessionRepository<S> {

	/**
	 * <p>
	 * A common session attribute that contains the current principal name (i.e.
	 * username).
	 * </p>
	 *
	 * <p>
	 * It is the responsibility of the developer to ensure the attribute is populated
	 * since Spring Session is not aware of the authentication mechanism being used.
	 * </p>
	 *
	 * @since 1.1
	 */
	String PRINCIPAL_NAME_INDEX_NAME = FindByIndexNameSessionRepository.class.getName()
			.concat(".PRINCIPAL_NAME_INDEX_NAME");

	/**
	 * Find a Map of the session id to the {@link Session} of all sessions that contain
	 * the session attribute with the name
	 * {@link FindByIndexNameSessionRepository#PRINCIPAL_NAME_INDEX_NAME} and the value of
	 * the specified principal name.
	 *
	 * @param indexName the name if the index (i.e.
	 * {@link FindByIndexNameSessionRepository#PRINCIPAL_NAME_INDEX_NAME})
	 * @param indexValue the value of the index to search for.
	 * @return a Map (never null) of the session id to the {@link Session} of all sessions
	 * that contain the session specified index name and the value of the specified index
	 * name. If no results are found, an empty Map is returned.
	 */
	Map<String, S> findByIndexNameAndIndexValue(String indexName, String indexValue);
}
