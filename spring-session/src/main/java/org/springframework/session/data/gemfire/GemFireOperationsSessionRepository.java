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

package org.springframework.session.data.gemfire;

import java.util.HashMap;
import java.util.Map;

import com.gemstone.gemfire.cache.query.SelectResults;

import org.springframework.data.gemfire.GemfireOperations;
import org.springframework.session.ExpiringSession;

/**
 * The GemFireOperationsSessionRepository class is a Spring SessionRepository
 * implementation that interfaces with and uses GemFire to back and store Spring Sessions.
 *
 * @author John Blum
 * @since 1.1.0
 * @see AbstractGemFireOperationsSessionRepository
 */
public class GemFireOperationsSessionRepository extends AbstractGemFireOperationsSessionRepository {

	// GemFire OQL query used to lookup Sessions by arbitrary attributes.
	protected static final String FIND_SESSIONS_BY_INDEX_NAME_VALUE_QUERY = "SELECT s FROM %1$s s WHERE s.attributes['%2$s'] = $1";

	// GemFire OQL query used to look up Sessions by principal name.
	protected static final String FIND_SESSIONS_BY_PRINCIPAL_NAME_QUERY = "SELECT s FROM %1$s s WHERE s.principalName = $1";

	/**
	 * Constructs an instance of GemFireOperationsSessionRepository initialized with the
	 * required GemfireOperations object used to perform data access operations to manage
	 * Session state.
	 *
	 * @param template the GemfireOperations object used to access and manage Session
	 * state in GemFire.
	 * @see org.springframework.data.gemfire.GemfireOperations
	 */
	public GemFireOperationsSessionRepository(GemfireOperations template) {
		super(template);
	}

	/**
	 * Looks up all available Sessions with the particular attribute indexed by name
	 * having the given value.
	 *
	 * @param indexName name of the indexed Session attribute. (e.g.
	 * {@link org.springframework.session.FindByIndexNameSessionRepository#PRINCIPAL_NAME_INDEX_NAME}
	 * ).
	 * @param indexValue value of the indexed Session attribute to search on (e.g.
	 * username).
	 * @return a mapping of Session ID to Session instances.
	 * @see org.springframework.session.ExpiringSession
	 * @see java.util.Map
	 * @see #prepareQuery(String)
	 */
	public Map<String, ExpiringSession> findByIndexNameAndIndexValue(String indexName, String indexValue) {
		SelectResults<ExpiringSession> results = getTemplate().find(prepareQuery(indexName), indexValue);

		Map<String, ExpiringSession> sessions = new HashMap<String, ExpiringSession>(results.size());

		for (ExpiringSession session : results.asList()) {
			sessions.put(session.getId(), session);
		}

		return sessions;
	}

	/**
	 * Prepares the appropriate GemFire OQL query based on the indexed Session attribute
	 * name.
	 *
	 * @param indexName a String indicating the name of the indexed Session attribute.
	 * @return an appropriate GemFire OQL statement for querying on a particular indexed
	 * Session attribute.
	 */
	protected String prepareQuery(String indexName) {
		return (PRINCIPAL_NAME_INDEX_NAME.equals(indexName)
			? String.format(FIND_SESSIONS_BY_PRINCIPAL_NAME_QUERY, getFullyQualifiedRegionName())
			: String.format(FIND_SESSIONS_BY_INDEX_NAME_VALUE_QUERY, getFullyQualifiedRegionName(), indexName));
	}

	/**
	 * Constructs a new {@link ExpiringSession} instance backed by GemFire.
	 *
	 * @return an instance of {@link ExpiringSession} backed by GemFire.
	 * @see AbstractGemFireOperationsSessionRepository.GemFireSession#create(int)
	 * @see org.springframework.session.ExpiringSession
	 * @see #getMaxInactiveIntervalInSeconds()
	 */
	public ExpiringSession createSession() {
		return GemFireSession.create(getMaxInactiveIntervalInSeconds());
	}

	/**
	 * Gets a copy of an existing, non-expired {@link ExpiringSession} by ID. If the
	 * Session is expired, then it is deleted.
	 *
	 * @param sessionId a String indicating the ID of the Session to get.
	 * @return an existing {@link ExpiringSession} by ID or null if not Session exists.
	 * @see AbstractGemFireOperationsSessionRepository.GemFireSession#from(ExpiringSession)
	 * @see org.springframework.session.ExpiringSession
	 * @see #delete(String)
	 */
	public ExpiringSession getSession(String sessionId) {
		ExpiringSession storedSession = getTemplate().get(sessionId);

		if (storedSession != null) {
			if (storedSession.isExpired()) {
				delete(storedSession.getId());
			}
			else {
				return GemFireSession.from(storedSession);
			}
		}

		return null;
	}

	/**
	 * Saves the specified {@link ExpiringSession} to GemFire.
	 *
	 * @param session the {@link ExpiringSession} to save.
	 * @see org.springframework.data.gemfire.GemfireOperations#put(Object, Object)
	 * @see org.springframework.session.ExpiringSession
	 */
	public void save(ExpiringSession session) {
		getTemplate().put(session.getId(), new GemFireSession(session));
	}

	/**
	 * Deletes (removes) any existing {@link ExpiringSession} from GemFire. This operation
	 * also results in a SessionDeletedEvent.
	 *
	 * @param sessionId a String indicating the ID of the Session to remove from GemFire.
	 * @see org.springframework.data.gemfire.GemfireOperations#remove(Object)
	 * @see #handleDeleted(String, ExpiringSession)
	 */
	public void delete(String sessionId) {
		handleDeleted(sessionId, getTemplate().<Object, ExpiringSession>remove(sessionId));
	}

}
