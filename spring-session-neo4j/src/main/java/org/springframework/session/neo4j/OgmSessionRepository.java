/*
 * Copyright 2014-2017 the original author or authors.
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

package org.springframework.session.neo4j;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.neo4j.ogm.model.Result;
import org.neo4j.ogm.session.SessionFactory;
import org.neo4j.ogm.transaction.Transaction;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.core.convert.support.GenericConversionService;
import org.springframework.core.serializer.support.DeserializingConverter;
import org.springframework.core.serializer.support.SerializingConverter;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.MapSession;
import org.springframework.session.Session;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

// TODO: JavaDoc
/**
 * A {@link org.springframework.session.SessionRepository} implementation that uses
 * Spring's {@link JdbcOperations} to store sessions in a relational database. This
 * implementation does not support publishing of session events.
 * 
 * @author Eric Spiegelberg
 */
public class OgmSessionRepository implements
		FindByIndexNameSessionRepository<OgmSessionRepository.OgmSession> {

	/**
	 * The default node label used by Spring Session to store sessions.
	 */
	public static final String DEFAULT_LABEL = "SPRING_SESSION";
	
	private static final String SPRING_SECURITY_CONTEXT = "SPRING_SECURITY_CONTEXT";

	private static final String CREATE_SESSION_QUERY = "create (n:%LABEL%) set {nodeProperties}";

	private static final String GET_SESSION_QUERY = "match (n:%LABEL%) where n.sessionId={sessionId} return n";
	
	private static final String UPDATE_SESSION_QUERY = "match (n:%LABEL%) where n.sessionId={sessionId} set {nodeProperties}";
	
	private static final String DELETE_SESSION_QUERY = "match (n:%LABEL%) where n.sessionId={sessionId} detach delete n";
	
	private static final String LIST_SESSIONS_BY_PRINCIPAL_NAME_QUERY = "match (n:%LABEL%) where n.principalName={principalName} return n";

// TODO: Complete the deleteSessionByLastAccessTimeQuery
//	private static final String DELETE_SESSIONS_BY_LAST_ACCESS_TIME_QUERY =
//			"DELETE FROM %TABLE_NAME% " +
//					"WHERE MAX_INACTIVE_INTERVAL < (? - LAST_ACCESS_TIME) / 1000";
	
	private static final Log logger = LogFactory.getLog(OgmSessionRepository.class);
	
	private static final PrincipalNameResolver PRINCIPAL_NAME_RESOLVER = new PrincipalNameResolver();

	private final SessionFactory sessionFactory;
	
	/**
	 * The name of label used by Spring Session to store sessions.
	 */
	private String label = DEFAULT_LABEL;
	
	private String createSessionQuery;
	
	private String getSessionQuery;
	
	private String updateSessionQuery;
	
	private String deleteSessionQuery;
	
	private String listSessionsByPrincipalNameQuery;

	private String deleteSessionsByLastAccessTimeQuery;
	
	/**
	 * If non-null, this value is used to override the default value for
	 * {@link OgmSession#setMaxInactiveInterval(Duration)}.
	 */
	private Integer defaultMaxInactiveInterval;

	private ConversionService conversionService;

	/**
	 * Create a new {@link OgmSessionRepository} instance which uses the
	 * provided {@link JdbcOperations} to manage sessions.
	 * @param jdbcOperations the {@link JdbcOperations} to use
	 * @param transactionManager the {@link PlatformTransactionManager} to use
	 */
	public OgmSessionRepository(SessionFactory sessionFactory) {

		Assert.notNull(sessionFactory, "SessionFactory must not be null");
		this.sessionFactory = sessionFactory;
		this.conversionService = createDefaultConversionService();
		prepareQueries();
	}
	
	/**
	 * Set the label used to store sessions.
	 * @param label the label
	 */
	public void setLabel(String label) {
		Assert.hasText(label, "label must not be empty");
		this.label = label.trim();
		prepareQueries();
	}

	/**
	 * Set the custom Cypher query used to create the session.
	 * @param createSessionQuery the Cypher query string
	 */
	public void setCreateSessionQuery(String createSessionQuery) {
		Assert.hasText(createSessionQuery, "createSessionQuery must not be empty");
		this.createSessionQuery = createSessionQuery;
	}
	
	/**
	 * Set the custom Cypher query used to retrieve the session.
	 * @param getSessionQuery the Cypher query string
	 */
	public void setGetSessionQuery(String getSessionQuery) {
		Assert.hasText(getSessionQuery, "getSessionQuery must not be empty");
		this.getSessionQuery = getSessionQuery;
	}
	
	/**
	 * Set the custom Cypher query used to update the session.
	 * @param updateSessionQuery the Cypher query string
	 */
	public void setUpdateSessionQuery(String updateSessionQuery) {
		Assert.hasText(updateSessionQuery, "updateSessionQuery must not be empty");
		this.updateSessionQuery = updateSessionQuery;
	}
	
	/**
	 * Set the custom Cypher query used to delete the session.
	 * @param deleteSessionQuery the Cypher query string
	 */
	public void setDeleteSessionQuery(String deleteSessionQuery) {
		Assert.hasText(deleteSessionQuery, "deleteSessionQuery must not be empty");
		this.deleteSessionQuery = deleteSessionQuery;
	}
	
	/**
	 * Set the custom Cypher query used to retrieve the sessions by principal name.
	 * @param listSessionsByPrincipalNameQuery the Cypher query string
	 */
	public void setListSessionsByPrincipalNameQuery(String listSessionsByPrincipalNameQuery) {
		Assert.hasText(listSessionsByPrincipalNameQuery, "Query must not be empty");
		this.listSessionsByPrincipalNameQuery = listSessionsByPrincipalNameQuery;
	}

	/**
	 * Set the custom Cypher query used to delete the sessions by last access time.
	 * @param deleteSessionsByLastAccessTimeQuery the Cypher query string
	 */
	public void setDeleteSessionsByLastAccessTimeQuery(String deleteSessionsByLastAccessTimeQuery) {
		Assert.hasText(deleteSessionsByLastAccessTimeQuery, "Query must not be empty");
		this.deleteSessionsByLastAccessTimeQuery = deleteSessionsByLastAccessTimeQuery;
	}
	
	/**
	 * Set the maximum inactive interval in seconds between requests before newly created
	 * sessions will be invalidated. A negative time indicates that the session will never
	 * timeout. The default is 1800 (30 minutes).
	 * @param defaultMaxInactiveInterval the maximum inactive interval in seconds
	 */
	public void setDefaultMaxInactiveInterval(Integer defaultMaxInactiveInterval) {
		this.defaultMaxInactiveInterval = defaultMaxInactiveInterval;
	}

	/**
	 * Sets the {@link ConversionService} to use.
	 * @param conversionService the converter to set
	 */
	public void setConversionService(ConversionService conversionService) {
		Assert.notNull(conversionService, "conversionService must not be null");
		this.conversionService = conversionService;
	}

	public OgmSession createSession() {
		OgmSession session = new OgmSession();
		if (this.defaultMaxInactiveInterval != null) {
			session.setMaxInactiveInterval(Duration.ofSeconds(this.defaultMaxInactiveInterval));
		}
		return session;
	}
	
	public void save(final OgmSession session) {
		if (session.isNew()) {

			Map<String, Object> parameters = new HashMap<>(1);

			int size = 5 + session.getAttributeNames().size();
			Map<String, Object> nodeProperties = new HashMap<>(size);
			parameters.put("nodeProperties", nodeProperties);
			
			nodeProperties.put("sessionId", session.getId());
			nodeProperties.put("creationTime", session.getCreationTime());
			nodeProperties.put("principalName", session.getPrincipalName());
			nodeProperties.put("lastAccessTime", session.getLastAccessedTime());
			nodeProperties.put("maxInactiveInterval", session.getMaxInactiveInterval());
			
			for (String attributeName : session.getAttributeNames()) {
				
				Optional<String> attributeValue = session.getAttribute(attributeName);

				if (attributeValue.isPresent()) {
					// TODO performance: Serialize the attributeValue only if it is not a native Neo4j type?
					String key = "attribute_" + attributeName;
					byte attributeValueAsBytes[] = serialize(attributeValue);
					nodeProperties.put(key, attributeValueAsBytes);
				}
				
			}

			executeCypher(createSessionQuery, parameters);
			
		} else {

			Map<String, Object> delta = session.getDelta();
			
			int size = 3 + delta.size();
			Map<String, Object> parameters = new HashMap<>(1);			
			Map<String, Object> nodeProperties = new HashMap<>(size);
			parameters.put("nodeProperties", nodeProperties);
			
			nodeProperties.put("principalName", session.getPrincipalName());
			nodeProperties.put("lastAccessTime", session.getLastAccessedTime());
			nodeProperties.put("maxInactiveInterval", session.getMaxInactiveInterval());
			
			if (!delta.isEmpty()) {				
				for (final Map.Entry<String, Object> entry : delta.entrySet()) {
//					if (entry.getValue() == null) {
//						// TODO: Verify a null property is removed from the node						
//					} else {
//					}				
					// TODO performance: Serialize the attributeValue only if it is not a native Neo4j type?
					String key = "attribute_" + entry.getKey();
					byte attributeValueAsBytes[] = serialize(entry.getValue());
					nodeProperties.put(key, attributeValueAsBytes);
				}			
			}			
		}

		session.clearChangeFlags();
	}

	public OgmSession getSession(final String id) {
//		final Session session = this.transactionOperations.execute(status -> {
//			List<Session> sessions = OgmSessionRepository.this.jdbcOperations.query(
//					OgmSessionRepository.this.getSessionQuery,
//					ps -> ps.setString(1, id),
//					OgmSessionRepository.this.extractor
//			);
//			if (sessions.isEmpty()) {
//				return null;
//			}
//			return sessions.get(0);
//		});
//
//		if (session != null) {
//			if (session.isExpired()) {
//				delete(id);
//			}
//			else {
//				return new OgmSession(session);
//			}
//		}
		
		//private static final String GET_SESSION_QUERY = "match (n:%LABEL%) where n.sessionId={id} return n";
		String cypher = getSessionQuery.replace("%LABEL", label);
		Map<String, Object> parameters = new HashMap<>();
		parameters.put("id", id);
		
		Result result = executeCypher(cypher, parameters);
		
		Iterator<Map<String, Object>> resultIterator = result.iterator();
		
		if (resultIterator.hasNext()) {
		
			Map<String, Object> r = resultIterator.next();
			
			// TODO: Convert r into a MapSession	
			MapSession session = new MapSession(id);
	//		session.setCreationTime(Instant.ofEpochMilli(rs.getLong("CREATION_TIME")));
	//		session.setLastAccessedTime(Instant.ofEpochMilli(rs.getLong("LAST_ACCESS_TIME")));
	//		session.setMaxInactiveInterval(Duration.ofSeconds(rs.getInt("MAX_INACTIVE_INTERVAL")));
			//mapSession.setAttribute(attributeName, attributeValue);
			
			if (session != null) {
				if (session.isExpired()) {
					delete(id);
				} else {
					return new OgmSession(session);
				}
			}		

		}
		
		return null;
	}

	public void delete(final String sessionId) {		
		Map<String, Object> parameters = new HashMap<>();
		parameters.put("sessionId", sessionId);
		
		executeCypher(this.deleteSessionQuery, parameters);		
	}

	public Map<String, OgmSession> findByIndexNameAndIndexValue(String indexName,
			final String indexValue) {
//		if (!PRINCIPAL_NAME_INDEX_NAME.equals(indexName)) {
//			return Collections.emptyMap();
//		}
//
//		List<Session> sessions = this.transactionOperations.execute(status ->
//				OgmSessionRepository.this.jdbcOperations.query(
//						OgmSessionRepository.this.listSessionsByPrincipalNameQuery,
//						ps -> ps.setString(1, indexValue),
//						OgmSessionRepository.this.extractor));
//
//		Map<String, OgmSession> sessionMap = new HashMap<>(
//				sessions.size());
//
//		for (Session session : sessions) {
//			sessionMap.put(session.getId(), new OgmSession(session));
//		}
//
//		return sessionMap;
		
		return null;
	}

	@Scheduled(cron = "${spring.session.cleanup.cron.expression:0 * * * * *}")
	public void cleanUpExpiredSessions() {
//		int deletedCount = this.transactionOperations.execute(transactionStatus ->
//				OgmSessionRepository.this.jdbcOperations.update(
//						OgmSessionRepository.this.deleteSessionsByLastAccessTimeQuery,
//						System.currentTimeMillis()));
//
//		if (logger.isDebugEnabled()) {
//			logger.debug("Cleaned up " + deletedCount + " expired sessions");
//		}
	}

	private static GenericConversionService createDefaultConversionService() {
		GenericConversionService converter = new GenericConversionService();
		converter.addConverter(Object.class, byte[].class,
				new SerializingConverter());
		converter.addConverter(byte[].class, Object.class,
				new DeserializingConverter());
		return converter;
	}

	private String getQuery(String base) {
		return StringUtils.replace(base, "%LABEL%", this.label);
	}
	
	private void prepareQueries() {
		this.createSessionQuery = getQuery(CREATE_SESSION_QUERY);
		this.getSessionQuery = getQuery(GET_SESSION_QUERY);
		this.updateSessionQuery = getQuery(UPDATE_SESSION_QUERY);
		this.deleteSessionQuery = getQuery(DELETE_SESSION_QUERY);
		this.listSessionsByPrincipalNameQuery =
				getQuery(LIST_SESSIONS_BY_PRINCIPAL_NAME_QUERY);
// TODO: Complete the deleteSessionByLastAccessTimeQuery		
//		this.deleteSessionsByLastAccessTimeQuery =
//				getQuery(DELETE_SESSIONS_BY_LAST_ACCESS_TIME_QUERY);
	}
	
	private byte[] serialize(Object attributeValue) {		
		byte bytes[] = (byte[]) this.conversionService.convert(attributeValue,
				TypeDescriptor.valueOf(Object.class),
				TypeDescriptor.valueOf(byte[].class));

		return bytes;
	}
	
//	private void serialize(PreparedStatement ps, int paramIndex, Object attributeValue)
//			throws SQLException {
////		this.lobHandler.getLobCreator().setBlobAsBytes(ps, paramIndex,
////				(byte[]) this.conversionService.convert(attributeValue,
////						TypeDescriptor.valueOf(Object.class),
////						TypeDescriptor.valueOf(byte[].class)));
//	}

	private Object deserialize(ResultSet rs, String columnName)
			throws SQLException {
//		return this.conversionService.convert(
//				this.lobHandler.getBlobAsBytes(rs, columnName),
//				TypeDescriptor.valueOf(byte[].class),
//				TypeDescriptor.valueOf(Object.class));
		return null;
	}

	/**
	 * The {@link Session} to use for {@link OgmSessionRepository}.
	 *
	 * @author Eric Spiegelberg
	 */
	final class OgmSession implements Session {

		private final Session delegate;

		private boolean isNew;

		private boolean changed;

		private Map<String, Object> delta = new HashMap<>();

		OgmSession() {
			this.delegate = new MapSession();
			this.isNew = true;
		}

		OgmSession(Session delegate) {
			Assert.notNull(delegate, "Session cannot be null");
			this.delegate = delegate;
		}

		boolean isNew() {
			return this.isNew;
		}

		boolean isChanged() {
			return this.changed;
		}

		Map<String, Object> getDelta() {
			return this.delta;
		}

		void clearChangeFlags() {
			this.isNew = false;
			this.changed = false;
			this.delta.clear();
		}

		String getPrincipalName() {
			return PRINCIPAL_NAME_RESOLVER.resolvePrincipal(this);
		}

		public String getId() {
			return this.delegate.getId();
		}

		public <T> Optional<T> getAttribute(String attributeName) {
			return this.delegate.getAttribute(attributeName);
		}

		public Set<String> getAttributeNames() {
			return this.delegate.getAttributeNames();
		}

		public void setAttribute(String attributeName, Object attributeValue) {
			this.delegate.setAttribute(attributeName, attributeValue);
			this.delta.put(attributeName, attributeValue);
			if (PRINCIPAL_NAME_INDEX_NAME.equals(attributeName) ||
					SPRING_SECURITY_CONTEXT.equals(attributeName)) {
				this.changed = true;
			}
		}

		public void removeAttribute(String attributeName) {
			this.delegate.removeAttribute(attributeName);
			this.delta.put(attributeName, null);
		}

		public Instant getCreationTime() {
			return this.delegate.getCreationTime();
		}

		public void setLastAccessedTime(Instant lastAccessedTime) {
			this.delegate.setLastAccessedTime(lastAccessedTime);
			this.changed = true;
		}

		public Instant getLastAccessedTime() {
			return this.delegate.getLastAccessedTime();
		}

		public void setMaxInactiveInterval(Duration interval) {
			this.delegate.setMaxInactiveInterval(interval);
			this.changed = true;
		}

		public Duration getMaxInactiveInterval() {
			return this.delegate.getMaxInactiveInterval();
		}

		public boolean isExpired() {
			return this.delegate.isExpired();
		}

	}

	/**
	 * Resolves the Spring Security principal name.
	 *
	 * @author Vedran Pavic
	 */
	static class PrincipalNameResolver {

		private SpelExpressionParser parser = new SpelExpressionParser();

		public String resolvePrincipal(Session session) {
			Optional<String> principalName = session.getAttribute(PRINCIPAL_NAME_INDEX_NAME);
			if (principalName.isPresent()) {
				return principalName.get();
			}
			Optional<Object> authentication = session.getAttribute(SPRING_SECURITY_CONTEXT);
			if (authentication.isPresent()) {
				Expression expression = this.parser
						.parseExpression("authentication?.name");
				return expression.getValue(authentication.get(), String.class);
			}
			return null;
		}

	}

	protected Result executeCypher(String cypher, Map<String, Object> parameters) {

		Result result = null;

		org.neo4j.ogm.session.Session ogmSession = sessionFactory.openSession();

		Transaction transaction = ogmSession.beginTransaction();

		try {

			result = ogmSession.query(cypher, parameters);

			transaction.commit();

		} catch (Exception e) {
			transaction.rollback();
		} finally {
			transaction.close();
		}
		
		return result;

	}
	
}
