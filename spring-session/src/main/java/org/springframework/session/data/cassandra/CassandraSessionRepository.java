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
import com.datastax.driver.core.ConsistencyLevel;
import com.datastax.driver.core.QueryOptions;
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
import org.springframework.util.Assert;


/**
 * A {@link org.springframework.session.SessionRepository} implementation that uses
 * Spring's {@link CassandraOperations} to store sessions in cassandra. This
 * implementation does not support publishing of session events.
 * It does make use of Cassandra's
 * <a href="https://docs.datastax.com/en/cql/3.1/cql/cql_using/use_expire_c.html">TTL feature</a>,
 * automatically removing expired sessions.
 * Cassandra's
 * <a href="https://docs.datastax.com/en/cql/3.1/cql/cql_reference/batch_r.html">batch feature</a>
 * is also used to ensure that sessions and their lookup entries are added atomically.
 * <p>
 * An example of how to create a new instance can be seen below:
 *
 * <pre class="code">
 * CassandraOperations cassandraOperations = new CassandraTemplate(sesion);
 *
 * // ... configure cassandraOperations ...
 *
 *
 * CassandraSessionRepository sessionRepository =
 *         new CassandraSessionRepository(cassandraOperations);
 * </pre>
 *
 * For additional information on how to create and configure
 * {@link org.springframework.data.cassandra.core.CassandraTemplate}, refer to the
 * <a href="http://docs.spring.io/spring/docs/current/spring-framework-reference/html/spring-data-tier.html">
 * Spring Framework Reference Documentation</a>.
 * <p>
 * By default, this implementation uses <code>spring_session</code> and
 * <code>spring_session_by_name</code> tables to store sessions. Note that the table
 * name can be customized using the {@link #setTableName(String)} method. In that case the
 * table used to store attributes will be named using the provided table name, suffixed
 * with <code>_by_name</code>.
 *
 * The table definition can be described as below:
 *
 * <pre class="code">
 * CREATE TABLE session (
 *   id uuid PRIMARY KEY,
 *   attributes map&lt;text, text&gt;,
 *   creation_time bigint,
 *   last_accessed bigint,
 *   max_inactive_interval_in_seconds int);
 *
 * CREATE TABLE session_by_name (
 *   principal_name text,
 *   id uuid,
 *   PRIMARY KEY (principal_name, id));
 *
 * </pre>
 *
 * @author Andrew Fitzgerald
 */
public class CassandraSessionRepository implements FindByIndexNameSessionRepository<CassandraSessionRepository.CassandraHttpSession> {

	private static final Logger log = LoggerFactory.getLogger(CassandraSessionRepository.class);
	private static final PrincipalNameResolver PRINCIPAL_NAME_RESOLVER = new PrincipalNameResolver();


	/**
	 * Default name of the cassandra table used to store sessions.
	 */
	public static final String DEFAULT_TABLE_NAME = "spring_session";

	//Temporary until #557 is resolved, see commends on PrincipalNameResolver
	private static final String SPRING_SECURITY_CONTEXT = "SPRING_SECURITY_CONTEXT";


	private final CassandraOperations cassandraOperations;
	private final SessionAttributeDeserializer sessionAttributeDeserializer = new SessionAttributeDeserializer();
	private final SessionAttributeSerializer sessionAttributeSerializer = new SessionAttributeSerializer();
	private final TtlCalculator ttlCalculator = new TtlCalculator();

	/**
	 * The default number of seconds a {@link CassandraHttpSession} session will be valid.
	 */
	private int defaultMaxInactiveInterval = MapSession.DEFAULT_MAX_INACTIVE_INTERVAL_SECONDS;

	/**
	 * The name of cassandra table used by Spring Session to store sessions.
	 */
	private String tableName = DEFAULT_TABLE_NAME;

	/**
	 * The consistency level to use for Spring Session queries.
	 */
	private ConsistencyLevel consistencyLevel = QueryOptions.DEFAULT_CONSISTENCY_LEVEL;

	@Autowired
	public CassandraSessionRepository(CassandraOperations cassandraOperations) {
		this.cassandraOperations = cassandraOperations;
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

	/**
	 * Set the name of cassandra table used to store sessions.
	 * @param tableName the cassandra table name
	 */
	public void setTableName(String tableName) {
		Assert.hasText(tableName, "Table name must not be empty");
		this.tableName = tableName.trim();
	}

	/**
	 * Set the consistency level to use for Spring Session queries.
	 * @param consistencyLevel to use
	 */
	public void setConsistencyLevel(ConsistencyLevel consistencyLevel) {
		this.consistencyLevel = consistencyLevel;
	}

	public String getTableName() {
		return this.tableName;
	}

	public String getIndexTableName() {
		return this.tableName + "_by_name";
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

		Insert insert = QueryBuilder.insertInto(this.tableName)
				.value("id", UUID.fromString(session.getId()))
				.value("creation_time", session.getCreationTime())
				.value("last_accessed", session.getLastAccessedTime())
				.value("max_inactive_interval_in_seconds", session.getMaxInactiveIntervalInSeconds())
				.value("attributes", serializedAttributes);
		insert.using(QueryBuilder.ttl(ttl));


		BatchStatement batch = new BatchStatement();
		batch.setConsistencyLevel(this.consistencyLevel);
		batch.add(insert);

		String savedPrincipalName = session.getSavedPrincipalName();
		String currentPrincipalName = session.getCurrentPrincipalName();

		boolean shouldDeleteIdx = savedPrincipalName != null && !savedPrincipalName.equals(currentPrincipalName);
		boolean shouldInsertIdx = currentPrincipalName != null;


		if (shouldDeleteIdx) {
			Statement idxDelete = QueryBuilder.delete().from(this.getIndexTableName())
					.where(QueryBuilder.eq("principal_name", savedPrincipalName));
			batch.add(idxDelete);
		}
		if (shouldInsertIdx) {
			Insert idxInsert = QueryBuilder.insertInto(this.getIndexTableName())
					.value("id", UUID.fromString(session.getId()))
					.value("principal_name", currentPrincipalName);
			idxInsert.using(QueryBuilder.ttl(session.getMaxInactiveIntervalInSeconds()));
			batch.add(idxInsert);
		}


		this.cassandraOperations.execute(batch);
		session.onSave();
	}


	public CassandraHttpSession getSession(String id) {
		Select select = QueryBuilder.select("id", "creation_time", "last_accessed", "max_inactive_interval_in_seconds", "attributes")
				.from(this.tableName);
		select.where(QueryBuilder.eq("id", UUID.fromString(id)));
		select.setConsistencyLevel(this.consistencyLevel);
		Row row = this.cassandraOperations.getSession().execute(select).one();
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
		Statement delete = QueryBuilder.delete().from(this.tableName)
				.where(QueryBuilder.eq("id", UUID.fromString(id)));
		String principalName = session.getSavedPrincipalName();
		if (principalName == null) {
			this.cassandraOperations.execute(delete);
		}
		else {
			Statement deleteIdx = QueryBuilder.delete().from(this.getIndexTableName())
					.where(QueryBuilder.eq("principal_name", principalName));

			BatchStatement batchStatement = new BatchStatement();
			batchStatement.add(delete);
			batchStatement.add(deleteIdx);
			batchStatement.setConsistencyLevel(this.consistencyLevel);
			this.cassandraOperations.execute(batchStatement);
		}

	}


	public Map<String, CassandraHttpSession> findByIndexNameAndIndexValue(String indexName, String indexValue) {
		Select select = QueryBuilder.select("id")
				.from(this.getIndexTableName());
				select.where(QueryBuilder.eq("principal_name", indexValue));
				select.setConsistencyLevel(this.consistencyLevel);
		List<UUID> uuids = this.cassandraOperations.queryForList(select, UUID.class);
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
	 * Implementation of ExpiringSession which primarily delegates to a {@link MapSession}.
	 * Keeps track of the saved and current principal by calling {@link #onMaybeChangedPrincipalName()}
	 * when attributes are updated or deleted.
	 * This is done so that we know if we need to insert/delete index entries when the session is saved.
	 *
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
