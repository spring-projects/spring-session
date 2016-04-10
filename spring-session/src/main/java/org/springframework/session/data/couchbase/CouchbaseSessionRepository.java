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
package org.springframework.session.data.couchbase;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.core.convert.TypeDescriptor;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.util.Assert;

/**
 * A {@link FindByIndexNameSessionRepository} that manages HTTP session data persistence.
 * The data is persisted to Couchbase database.
 *
 * @author Mariusz Kopylec
 * @since 1.2.0
 */
public class CouchbaseSessionRepository
		implements FindByIndexNameSessionRepository<CouchbaseSession> {

	protected static final int SESSION_DOCUMENT_EXPIRATION_DELAY_IN_SECONDS = 60;

	private static final Log log = LogFactory.getLog(CouchbaseSessionRepository.class);

	protected final CouchbaseDao dao;
	protected final ObjectMapper mapper;
	protected final int sessionTimeout;
	protected final SessionDataConverter converter;
	protected final boolean principalSessionsEnabled;

	public CouchbaseSessionRepository(CouchbaseDao dao, ObjectMapper mapper,
			int sessionTimeout, SessionDataConverter converter,
			boolean principalSessionsEnabled) {
		Assert.notNull(dao, "Missing couchbase data access object");
		Assert.notNull(mapper, "Missing JSON object mapper");
		Assert.notNull(converter, "Missing session data converter");
		this.dao = dao;
		this.mapper = mapper;
		this.sessionTimeout = sessionTimeout;
		this.converter = converter;
		this.principalSessionsEnabled = principalSessionsEnabled;
	}

	public CouchbaseSession createSession() {
		CouchbaseSession session = new CouchbaseSession(this.sessionTimeout);
		SessionDocument sessionDocument = new SessionDocument(session.getId(),
				session.getAttributes());
		this.dao.save(sessionDocument);
		this.dao.updateExpirationTime(session.getId(), getSessionDocumentExpiration());
		if (log.isDebugEnabled()) {
			log.debug("Created HTTP session with ID " + session.getId());
		}
		return session;
	}

	public void save(CouchbaseSession session) {
		SessionDocument sessionDocument = (SessionDocument) this.converter.convert(
				session, TypeDescriptor.valueOf(CouchbaseSession.class),
				TypeDescriptor.valueOf(SessionDocument.class));
		this.dao.save(sessionDocument);
		if (isOperationOnPrincipalSessionsRequired(session)) {
			savePrincipalSession(session);
		}
		this.dao.updateExpirationTime(session.getId(), getSessionDocumentExpiration());
		if (log.isDebugEnabled()) {
			log.debug("Saved HTTP session with ID " + session.getId());
		}
	}

	public CouchbaseSession getSession(String id) {
		SessionDocument sessionDocument = this.dao.findById(id);
		if (sessionDocument == null) {
			if (log.isDebugEnabled()) {
				log.debug("HTTP session with ID " + id + " not found");
			}
			return null;
		}
		CouchbaseSession session = (CouchbaseSession) this.converter.convert(
				sessionDocument, TypeDescriptor.valueOf(SessionDocument.class),
				TypeDescriptor.valueOf(CouchbaseSession.class));
		if (session.isExpired()) {
			if (log.isDebugEnabled()) {
				log.debug("HTTP session with ID " + id + " has expired");
			}
			deleteSession(session);
			return null;
		}
		session.setLastAccessedTime(System.currentTimeMillis());
		if (log.isDebugEnabled()) {
			log.debug("Found HTTP session with ID " + id);
		}
		return session;
	}

	public void delete(String id) {
		CouchbaseSession session = getSession(id);
		if (session == null) {
			return;
		}
		deleteSession(session);
	}

	public Map<String, CouchbaseSession> findByIndexNameAndIndexValue(String indexName,
			String indexValue) {
		if (!this.principalSessionsEnabled) {
			throw new IllegalStateException(
					"Cannot get principal HTTP sessions. Enable getting principal HTTP sessions using '@EnableCouchbaseHttpSession.principalSessionsEnabled' attribute.");
		}
		if (!PRINCIPAL_NAME_INDEX_NAME.equals(indexName)) {
			return Collections.emptyMap();
		}
		PrincipalSessionsDocument sessionsDocument = this.dao.findByPrincipal(indexValue);
		if (sessionsDocument == null) {
			if (log.isDebugEnabled()) {
				log.debug("Principals " + indexValue + " sessions not found");
			}
			return Collections.emptyMap();
		}
		Map<String, CouchbaseSession> sessionsById = new HashMap<String, CouchbaseSession>(
				sessionsDocument.getSessionIds().size());
		for (String sessionId : sessionsDocument.getSessionIds()) {
			CouchbaseSession session = getSession(sessionId);
			sessionsById.put(sessionId, session);
		}
		if (log.isDebugEnabled()) {
			log.debug("Found principals " + indexValue + " sessions with IDs "
					+ sessionsById.keySet());
		}
		return sessionsById;
	}

	protected int getSessionDocumentExpiration() {
		return this.sessionTimeout + SESSION_DOCUMENT_EXPIRATION_DELAY_IN_SECONDS;
	}

	protected void savePrincipalSession(CouchbaseSession session) {
		String principal = session.getPrincipalAttribute();
		if (this.dao.exists(principal)) {
			this.dao.updatePutPrincipalSession(principal, session.getId());
		}
		else {
			PrincipalSessionsDocument sessionsDocument = new PrincipalSessionsDocument(
					principal, Collections.singletonList(session.getId()));
			this.dao.save(sessionsDocument);
		}
		if (log.isDebugEnabled()) {
			log.debug("Added principals " + principal + " session with ID "
					+ session.getId());
		}
		this.dao.updateExpirationTime(principal, getSessionDocumentExpiration());
	}

	protected void deleteSession(CouchbaseSession session) {
		if (isOperationOnPrincipalSessionsRequired(session)) {
			this.dao.updateRemovePrincipalSession(session.getPrincipalAttribute(),
					session.getId());
			if (log.isDebugEnabled()) {
				log.debug("Removed principals " + session.getPrincipalAttribute()
						+ " session with ID " + session.getId());
			}
		}
		this.dao.delete(session.getId());
		if (log.isDebugEnabled()) {
			log.debug("Deleted HTTP session with ID " + session.getId());
		}
	}

	private boolean isOperationOnPrincipalSessionsRequired(CouchbaseSession session) {
		return this.principalSessionsEnabled && session.isPrincipalSession();
	}
}
