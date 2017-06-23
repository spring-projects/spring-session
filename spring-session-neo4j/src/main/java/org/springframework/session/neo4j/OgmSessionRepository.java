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

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.neo4j.ogm.session.SessionFactory;
import org.neo4j.ogm.transaction.Transaction;
import org.springframework.core.convert.ConversionService;
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

	private static final Log logger = LogFactory.getLog(OgmSessionRepository.class);

	private static final PrincipalNameResolver PRINCIPAL_NAME_RESOLVER = new PrincipalNameResolver();

	private final SessionFactory sessionFactory;
	
	/**
	 * The name of label used by Spring Session to store sessions.
	 */
	private String label = DEFAULT_LABEL;
	
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
		
		//Session session = null;
		
		Assert.notNull(sessionFactory, "SessionFactory must not be null");
		this.sessionFactory = sessionFactory;
		this.conversionService = createDefaultConversionService();
		//prepareQueries();
	}
	
	/**
	 * Set the label used to store sessions.
	 * @param label the label
	 */
	public void setLabel(String label) {
		Assert.hasText(label, "label must not be empty");
		this.label = label.trim();
//		prepareQueries();
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
		
			 org.neo4j.ogm.session.Session ogmSession = sessionFactory.openSession();
			 
			 Transaction transaction = ogmSession.beginTransaction();

			 try {
//			     buyConcertTicket(person,concert);
//			     bookHotel(person, hotel);
				 
			     transaction.commit();
			 }
			 catch (Exception e) {
				 transaction.rollback();
			 } finally {
				 transaction.close();
			 }
			 
		}
//		if (session.isNew()) {
//			this.transactionOperations.execute(new TransactionCallbackWithoutResult() {
//
//				protected void doInTransactionWithoutResult(TransactionStatus status) {
//					OgmSessionRepository.this.jdbcOperations.update(
//							OgmSessionRepository.this.createSessionQuery,
//							ps -> {
//								ps.setString(1, session.getId());
//								ps.setLong(2, session.getCreationTime().toEpochMilli());
//								ps.setLong(3, session.getLastAccessedTime().toEpochMilli());
//								ps.setInt(4, (int) session.getMaxInactiveInterval().getSeconds());
//								ps.setString(5, session.getPrincipalName());
//							});
//					if (!session.getAttributeNames().isEmpty()) {
//						final List<String> attributeNames = new ArrayList<>(session.getAttributeNames());
//						OgmSessionRepository.this.jdbcOperations.batchUpdate(
//								OgmSessionRepository.this.createSessionAttributeQuery,
//								new BatchPreparedStatementSetter() {
//
//									public void setValues(PreparedStatement ps, int i) throws SQLException {
//										String attributeName = attributeNames.get(i);
//										ps.setString(1, session.getId());
//										ps.setString(2, attributeName);
//										serialize(ps, 3, session.getAttribute(attributeName).orElse(null));
//									}
//
//									public int getBatchSize() {
//										return attributeNames.size();
//									}
//
//								});
//					}
//				}
//
//			});
//		}
//		else {
//			this.transactionOperations.execute(new TransactionCallbackWithoutResult() {
//
//				protected void doInTransactionWithoutResult(TransactionStatus status) {
//					if (session.isChanged()) {
//						OgmSessionRepository.this.jdbcOperations.update(
//								OgmSessionRepository.this.updateSessionQuery,
//								ps -> {
//									ps.setLong(1, session.getLastAccessedTime().toEpochMilli());
//									ps.setInt(2, (int) session.getMaxInactiveInterval().getSeconds());
//									ps.setString(3, session.getPrincipalName());
//									ps.setString(4, session.getId());
//								});
//					}
//					Map<String, Object> delta = session.getDelta();
//					if (!delta.isEmpty()) {
//						for (final Map.Entry<String, Object> entry : delta.entrySet()) {
//							if (entry.getValue() == null) {
//								OgmSessionRepository.this.jdbcOperations.update(
//										OgmSessionRepository.this.deleteSessionAttributeQuery,
//										ps -> {
//											ps.setString(1, session.getId());
//											ps.setString(2, entry.getKey());
//										});
//							}
//							else {
//								int updatedCount = OgmSessionRepository.this.jdbcOperations.update(
//										OgmSessionRepository.this.updateSessionAttributeQuery,
//										ps -> {
//											serialize(ps, 1, entry.getValue());
//											ps.setString(2, session.getId());
//											ps.setString(3, entry.getKey());
//										});
//								if (updatedCount == 0) {
//									OgmSessionRepository.this.jdbcOperations.update(
//											OgmSessionRepository.this.createSessionAttributeQuery,
//											ps -> {
//												ps.setString(1, session.getId());
//												ps.setString(2, entry.getKey());
//												serialize(ps, 3, entry.getValue());
//											});
//								}
//							}
//						}
//					}
//				}
//
//			});
//		}
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
		return null;
	}

	public void delete(final String id) {
//		this.transactionOperations.execute(new TransactionCallbackWithoutResult() {
//
//			protected void doInTransactionWithoutResult(TransactionStatus status) {
//				OgmSessionRepository.this.jdbcOperations.update(
//						OgmSessionRepository.this.deleteSessionQuery, id);
//			}
//
//		});
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

	private void serialize(PreparedStatement ps, int paramIndex, Object attributeValue)
			throws SQLException {
//		this.lobHandler.getLobCreator().setBlobAsBytes(ps, paramIndex,
//				(byte[]) this.conversionService.convert(attributeValue,
//						TypeDescriptor.valueOf(Object.class),
//						TypeDescriptor.valueOf(byte[].class)));
	}

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

}
