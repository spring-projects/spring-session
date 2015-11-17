/*
 * Copyright 2002-2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
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

import org.springframework.data.gemfire.GemfireOperations;
import org.springframework.session.ExpiringSession;

import com.gemstone.gemfire.cache.query.SelectResults;

/**
 * The GemFireOperationsSessionRepository class is a Spring SessionRepository implementation that interfaces with
 * and uses GemFire to back and store Spring Sessions.
 *
 * @author John Blum
 * @see org.springframework.data.gemfire.GemfireOperations
 * @see org.springframework.session.ExpiringSession
 * @see org.springframework.session.Session
 * @see org.springframework.session.data.gemfire.AbstractGemFireOperationsSessionRepository
 * @since 1.1.0
 */
public class GemFireOperationsSessionRepository extends AbstractGemFireOperationsSessionRepository {

	// GemFire OQL query used to look up Sessions by principal name.
	protected static final String FIND_SESSIONS_BY_PRINCIPAL_NAME_QUERY =
		"SELECT s FROM %1$s s WHERE s.principalName = $1";

	/**
	 * Constructs an instance of GemFireOperationsSessionRepository initialized with the required GemfireOperations
	 * object used to perform data access operations to manage Session state.
	 *
	 * @param template the GemfireOperations object used to access and manage Session state in GemFire.
	 * @see org.springframework.data.gemfire.GemfireOperations
	 */
	public GemFireOperationsSessionRepository(GemfireOperations template) {
		super(template);
	}

	/**
	 * Looks up all the available Sessions tied to the specific user identified by principal name.
	 *
	 * @param principalName the principal name (i.e. username) to search for all existing Spring Sessions.
	 * @return a mapping of Session ID to Session instances.
	 * @see org.springframework.session.ExpiringSession
	 */
	public Map<String, ExpiringSession> findByPrincipalName(String principalName) {
		SelectResults<ExpiringSession> results = getTemplate().find(String.format(
			FIND_SESSIONS_BY_PRINCIPAL_NAME_QUERY, getFullyQualifiedRegionName()), principalName);

		Map<String, ExpiringSession> sessions = new HashMap<String, ExpiringSession>(results.size());

		for (ExpiringSession session : results.asList()) {
			sessions.put(session.getId(), session);
		}

		return sessions;
	}

	/**
	 * Constructs a new {@link ExpiringSession} instance backed by GemFire.
	 *
	 * @return an instance of {@link ExpiringSession} backed by GemFire.
	 * @see org.springframework.session.data.gemfire.GemFireOperationsSessionRepository.GemFireSession#create(int)
	 * @see org.springframework.session.ExpiringSession
	 * @see #getMaxInactiveIntervalInSeconds()
	 */
	public ExpiringSession createSession() {
		return GemFireSession.create(getMaxInactiveIntervalInSeconds());
	}

	/**
	 * Gets a copy of an existing, non-expired {@link ExpiringSession} by ID.  If the Session is expired,
	 * then it is deleted.
	 *
	 * @param sessionId a String indicating the ID of the Session to get.
	 * @return an existing {@link ExpiringSession} by ID or null if not Session exists.
	 * @see org.springframework.session.data.gemfire.GemFireOperationsSessionRepository.GemFireSession#from(ExpiringSession)
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
	 * Deletes (removes) any existing {@link ExpiringSession} from GemFire.  This operation also results in
	 * a SessionDeletedEvent.
	 *
	 * @param sessionId a String indicating the ID of the Session to remove from GemFire.
	 * @see org.springframework.data.gemfire.GemfireOperations#remove(Object)
	 * @see #handleDeleted(String, ExpiringSession)
	 */
	public void delete(String sessionId) {
		handleDeleted(sessionId, getTemplate().<Object, ExpiringSession>remove(sessionId));
	}

}
