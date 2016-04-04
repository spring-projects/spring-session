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

import com.couchbase.client.java.document.json.JsonObject;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

	protected static final String GLOBAL_NAMESPACE = "global";
	protected static final int SESSION_DOCUMENT_EXPIRATION_DELAY_IN_SECONDS = 60;

	private static final Logger log = LoggerFactory
			.getLogger(CouchbaseSessionRepository.class);

	protected final CouchbaseDao dao;
	protected final ObjectMapper mapper;
	protected final String namespace;
	protected final int sessionTimeout;
	protected final Serializer serializer;
	protected final boolean principalSessionsEnabled;

	public CouchbaseSessionRepository(CouchbaseDao dao, String namespace,
			ObjectMapper mapper, int sessionTimeout, Serializer serializer,
			boolean principalSessionsEnabled) {
		Assert.notNull(dao, "Missing couchbase data access object");
		Assert.notNull(mapper, "Missing JSON object mapper");
		Assert.hasText(namespace, "Empty HTTP session namespace");
		Assert.isTrue(!namespace.equals(GLOBAL_NAMESPACE),
				"Forbidden HTTP session namespace '" + namespace + "'");
		Assert.notNull(serializer, "Missing object serializer");
		this.dao = dao;
		this.mapper = mapper;
		this.namespace = namespace.trim();
		this.sessionTimeout = sessionTimeout;
		this.serializer = serializer;
		this.principalSessionsEnabled = principalSessionsEnabled;
	}

	public CouchbaseSession createSession() {
		CouchbaseSession session = new CouchbaseSession(sessionTimeout);
		Map<String, Map<String, Object>> sessionData = new HashMap<String, Map<String, Object>>(
				2);
		sessionData.put(GLOBAL_NAMESPACE, session.getGlobalAttributes());
		sessionData.put(namespace, session.getNamespaceAttributes());
		SessionDocument sessionDocument = new SessionDocument(session.getId(),
				sessionData);
		dao.save(sessionDocument);
		dao.updateExpirationTime(session.getId(), getSessionDocumentExpiration());

		log.debug("Created HTTP session with ID {}", session.getId());

		return session;
	}

	public void save(CouchbaseSession session) {
		Map<String, Object> serializedGlobal = serializer
				.serializeSessionAttributes(session.getGlobalAttributes());
		dao.updateSession(JsonObject.from(serializedGlobal), GLOBAL_NAMESPACE,
				session.getId());

		if (session.isNamespacePersistenceRequired()) {
			Map<String, Object> serializedNamespace = serializer
					.serializeSessionAttributes(session.getNamespaceAttributes());
			dao.updateSession(JsonObject.from(serializedNamespace), namespace,
					session.getId());
		}

		if (isOperationOnPrincipalSessionsRequired(session)) {
			savePrincipalSession(session);
		}
		dao.updateExpirationTime(session.getId(), getSessionDocumentExpiration());
		log.debug("Saved HTTP session with ID {}", session.getId());
	}

	public CouchbaseSession getSession(String id) {
		Map<String, Object> globalAttributes = dao.findSessionAttributes(id,
				GLOBAL_NAMESPACE);
		Map<String, Object> namespaceAttributes = dao.findSessionAttributes(id,
				namespace);

		if (globalAttributes == null && namespaceAttributes == null) {
			log.debug("HTTP session with ID {} not found", id);
			return null;
		}

		Assert.notNull(globalAttributes,
				"Invalid state of HTTP session persisted in couchbase. Missing global attributes.");

		Map<String, Object> deserializedGlobal = serializer
				.deserializeSessionAttributes(globalAttributes);
		Map<String, Object> deserializedNamespace = serializer
				.deserializeSessionAttributes(namespaceAttributes);
		CouchbaseSession session = new CouchbaseSession(id, deserializedGlobal,
				deserializedNamespace);
		if (session.isExpired()) {
			log.debug("HTTP session with ID {} has expired", id);
			deleteSession(session);
			return null;
		}
		session.setLastAccessedTime(System.currentTimeMillis());

		log.debug("Found HTTP session with ID {}", id);

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
		if (!principalSessionsEnabled) {
			throw new IllegalStateException(
					"Cannot get principal HTTP sessions. Enable getting principal HTTP sessions using '@EnableCouchbaseHttpSession.principalSessionsEnabled' attribute.");
		}
		if (!PRINCIPAL_NAME_INDEX_NAME.equals(indexName)) {
			return Collections.emptyMap();
		}
		PrincipalSessionsDocument sessionsDocument = dao.findByPrincipal(indexValue);
		if (sessionsDocument == null) {
			log.debug("Principals {} sessions not found", indexValue);
			return Collections.emptyMap();
		}
		Map<String, CouchbaseSession> sessionsById = new HashMap<String, CouchbaseSession>(
				sessionsDocument.getSessionIds().size());
		for (String sessionId : sessionsDocument.getSessionIds()) {
			CouchbaseSession session = getSession(sessionId);
			sessionsById.put(sessionId, session);
		}

		log.debug("Found principals {} sessions with IDs {}", indexValue,
				sessionsById.keySet());

		return sessionsById;
	}

	protected int getSessionDocumentExpiration() {
		return sessionTimeout + SESSION_DOCUMENT_EXPIRATION_DELAY_IN_SECONDS;
	}

	protected void savePrincipalSession(CouchbaseSession session) {
		String principal = session.getPrincipalAttribute();
		if (dao.exists(principal)) {
			dao.updatePutPrincipalSession(principal, session.getId());
		}
		else {
			PrincipalSessionsDocument sessionsDocument = new PrincipalSessionsDocument(
					principal, Collections.singletonList(session.getId()));
			dao.save(sessionsDocument);
		}
		log.debug("Added principals {} session with ID {}", principal, session.getId());
		dao.updateExpirationTime(principal, getSessionDocumentExpiration());
	}

	protected void deleteSession(CouchbaseSession session) {
		if (isOperationOnPrincipalSessionsRequired(session)) {
			dao.updateRemovePrincipalSession(session.getPrincipalAttribute(),
					session.getId());
			log.debug("Removed principals {} session with ID {}",
					session.getPrincipalAttribute(), session.getId());
		}
		dao.delete(session.getId());
		log.debug("Deleted HTTP session with ID {}", session.getId());
	}

	private boolean isOperationOnPrincipalSessionsRequired(CouchbaseSession session) {
		return principalSessionsEnabled && session.isPrincipalSession();
	}
}
