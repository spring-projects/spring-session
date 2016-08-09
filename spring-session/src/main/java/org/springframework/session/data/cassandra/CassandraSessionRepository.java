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

package org.springframework.session.data.cassandra;


import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import com.datastax.driver.core.BatchStatement;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Statement;
import com.datastax.driver.core.querybuilder.Insert;
import com.datastax.driver.core.querybuilder.QueryBuilder;
import com.datastax.driver.core.querybuilder.Select;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.data.cassandra.core.CassandraOperations;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.session.ExpiringSession;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.MapSession;
import org.springframework.session.Session;
import org.springframework.session.data.cassandra.conversion.SessionAttributeDeserializer;
import org.springframework.session.data.cassandra.conversion.SessionAttributeSerializer;
import org.springframework.session.jdbc.JdbcOperationsSessionRepository;


/**
 * TODO doc this.
 * @author Andrew Fitzgerald
 */
public class CassandraSessionRepository implements FindByIndexNameSessionRepository<CassandraSessionRepository.CassandraHttpSession> {

	private static final Logger log = LoggerFactory.getLogger(CassandraSessionRepository.class);
	private static final PrincipalNameResolver PRINCIPAL_NAME_RESOLVER = new PrincipalNameResolver();

	//Temporary until #557 is resolved, see commends on PrincipalNameResolver
	private static final String SPRING_SECURITY_CONTEXT = "SPRING_SECURITY_CONTEXT";


	private final CassandraOperations template;
	private final SessionAttributeDeserializer sessionAttributeDeserializer = new SessionAttributeDeserializer();
	private final SessionAttributeSerializer sessionAttributeSerializer = new SessionAttributeSerializer();
	private final TtlCalculator ttlCalculator = new TtlCalculator();
	private int defaultMaxInactiveInterval = MapSession.DEFAULT_MAX_INACTIVE_INTERVAL_SECONDS;


	@Autowired
	public CassandraSessionRepository(CassandraOperations template) {
		this.template = template;
	}

	public CassandraHttpSession createSession() {
		CassandraHttpSession cassandraHttpSession = new CassandraHttpSession();
		cassandraHttpSession.setMaxInactiveIntervalInSeconds(this.defaultMaxInactiveInterval);
		return cassandraHttpSession;
	}

	public int getDefaultMaxInactiveInterval() {
		return this.defaultMaxInactiveInterval;
	}

	public void setDefaultMaxInactiveInterval(int defaultMaxInactiveInterval) {
		this.defaultMaxInactiveInterval = defaultMaxInactiveInterval;
	}

	public void save(CassandraHttpSession session) {

		int ttl;
		TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis() - session.getLastAccessedTime());
		try {
			ttl = this.ttlCalculator.calculateTtlInSeconds(System.currentTimeMillis(), session);
		}
		catch (IllegalArgumentException e) {
			log.info("Session has already expired, skipping save");
			return;
		}

		Map<String, String> serializedAttributes = this.sessionAttributeSerializer.convert(session);

		Insert insert = QueryBuilder.insertInto("session")
				.value("id", UUID.fromString(session.getId()))
				.value("creation_time", session.getCreationTime())
				.value("last_accessed", session.getLastAccessedTime())
				.value("max_inactive_interval_in_seconds", session.getMaxInactiveIntervalInSeconds())
				.value("attributes", serializedAttributes);
		insert.using(QueryBuilder.ttl(ttl));


		BatchStatement batch = new BatchStatement();
		batch.add(insert);

		String savedPrincipalName = session.getSavedPrincipalName();
		String currentPrincipalName = session.getCurrentPrincipalName();

		boolean shouldDeleteIdx = savedPrincipalName != null && !savedPrincipalName.equals(currentPrincipalName);
		boolean shouldInsertIdx = currentPrincipalName != null;


		if (shouldDeleteIdx) {
			Statement idxDelete = QueryBuilder.delete().from("session_by_name")
					.where(QueryBuilder.eq("principal_name", savedPrincipalName));
			batch.add(idxDelete);
		}
		if (shouldInsertIdx) {
			Insert idxInsert = QueryBuilder.insertInto("session_by_name")
					.value("id", UUID.fromString(session.getId()))
					.value("principal_name", currentPrincipalName);
			idxInsert.using(QueryBuilder.ttl(session.getMaxInactiveIntervalInSeconds()));
			batch.add(idxInsert);
		}


		this.template.execute(batch);
		session.onSave();
	}


	public CassandraHttpSession getSession(String id) {
		Select select = QueryBuilder.select("id", "creation_time", "last_accessed", "max_inactive_interval_in_seconds", "attributes")
				.from("session");
		select.where(QueryBuilder.eq("id", UUID.fromString(id)));
		Row row = this.template.getSession().execute(select).one();
		if (row == null) {
			return null;
		}
		long creationTime = row.getLong(1);
		long lastAccessed = row.getLong(2);
		int maxInactiveIntervalInSeconds = row.getInt(3);

		Map<String, String> attributes = row.getMap(4, String.class, String.class);
		CassandraHttpSession result = new CassandraHttpSession(id);
		result.setCreationTime(creationTime);
		result.setLastAccessedTime(lastAccessed);
		result.setMaxInactiveIntervalInSeconds(maxInactiveIntervalInSeconds);
		this.sessionAttributeDeserializer.inflateSession(attributes, result);
		result.onSave();
		return result;
	}

	public void delete(String id) {
		CassandraHttpSession session = getSession(id);
		Statement delete = QueryBuilder.delete().from("session")
				.where(QueryBuilder.eq("id", UUID.fromString(id)));
		String principalName = session.getSavedPrincipalName();
		if (principalName == null) {
			this.template.execute(delete);
		}
		else {
			Statement deleteIdx = QueryBuilder.delete().from("session_by_name")
					.where(QueryBuilder.eq("principal_name", principalName));

			BatchStatement batchStatement = new BatchStatement();
			batchStatement.add(delete);
			batchStatement.add(deleteIdx);
			this.template.execute(batchStatement);
		}

	}


	public Map<String, CassandraHttpSession> findByIndexNameAndIndexValue(String indexName, String indexValue) {
		Select select = QueryBuilder.select("id")
				.from("session_by_name");
		select.where(QueryBuilder.eq("principal_name", indexValue));
		List<UUID> uuids = this.template.queryForList(select, UUID.class);
		Map<String, CassandraHttpSession> result = new HashMap<String, CassandraHttpSession>();
		for (UUID id : uuids) {
			CassandraHttpSession session = getSession(id.toString());
			if (session != null) {
				result.put(id.toString(), getSession(id.toString()));
			}
		}
		return result;
	}

	/**
	 * TODO doc this.
	 * @author Andrew Fitzgerald
	 */
	static class CassandraHttpSession implements ExpiringSession {

		private String savedPrincipalName = null;
		private String currentPrincipalName = null;
		private MapSession delegate;


		CassandraHttpSession() {
			this.delegate = new MapSession();
		}


		CassandraHttpSession(String id) {
			this.delegate = new MapSession(id);
		}

		public void onSave() {
			this.savedPrincipalName = this.currentPrincipalName;
		}

		private void onMaybeChangedPrincipalName() {
			this.currentPrincipalName = PRINCIPAL_NAME_RESOLVER.resolvePrincipal(this.delegate);
		}

		public String getSavedPrincipalName() {
			return this.savedPrincipalName;
		}

		public String getCurrentPrincipalName() {
			return this.currentPrincipalName;
		}

		public String getId() {
			return this.delegate.getId();
		}

		public long getCreationTime() {
			return this.delegate.getCreationTime();
		}

		public void setCreationTime(long creationTime) {
			this.delegate.setCreationTime(creationTime);
		}

		public <T> T getAttribute(String attributeName) {
			return this.delegate.getAttribute(attributeName);
		}

		public long getLastAccessedTime() {
			return this.delegate.getLastAccessedTime();
		}

		public void setLastAccessedTime(long lastAccessedTime) {
			this.delegate.setLastAccessedTime(lastAccessedTime);
		}

		public Set<String> getAttributeNames() {
			return this.delegate.getAttributeNames();
		}

		public void setAttribute(String attributeName, Object attributeValue) {
			this.delegate.setAttribute(attributeName, attributeValue);
			onMaybeChangedPrincipalName();
		}

		public int getMaxInactiveIntervalInSeconds() {
			return this.delegate.getMaxInactiveIntervalInSeconds();
		}

		public void setMaxInactiveIntervalInSeconds(int interval) {
			this.delegate.setMaxInactiveIntervalInSeconds(interval);
		}

		public void removeAttribute(String attributeName) {
			this.delegate.removeAttribute(attributeName);
			onMaybeChangedPrincipalName();
		}

		public boolean isExpired() {
			return this.delegate.isExpired();
		}
	}



	/**
	 * Resolves the Spring Security principal name.
	 * Copy pasted from ${@link JdbcOperationsSessionRepository} until it is extracted to a common class.
	 * https://github.com/spring-projects/spring-session/pull/557
	 *
	 * @author Vedran Pavic
	 */
	public static class PrincipalNameResolver {

		private SpelExpressionParser parser = new SpelExpressionParser();

		public String resolvePrincipal(Session session) {
			String principalName = session.getAttribute(PRINCIPAL_NAME_INDEX_NAME);
			if (principalName != null) {
				return principalName;
			}
			Object authentication = session.getAttribute(SPRING_SECURITY_CONTEXT);
			if (authentication != null) {
				Expression expression = this.parser
						.parseExpression("authentication?.name");
				return expression.getValue(authentication, String.class);
			}
			return null;
		}

	}

}
