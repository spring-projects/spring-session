/*
 * Copyright 2014-2022 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.session.jdbc;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.core.convert.support.GenericConversionService;
import org.springframework.core.serializer.support.DeserializingConverter;
import org.springframework.core.serializer.support.SerializingConverter;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.support.lob.DefaultLobHandler;
import org.springframework.jdbc.support.lob.LobCreator;
import org.springframework.jdbc.support.lob.LobHandler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.session.DelegatingIndexResolver;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.FlushMode;
import org.springframework.session.IndexResolver;
import org.springframework.session.MapSession;
import org.springframework.session.PrincipalNameIndexResolver;
import org.springframework.session.SaveMode;
import org.springframework.session.Session;
import org.springframework.session.SessionIdGenerator;
import org.springframework.session.UuidSessionIdGenerator;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * A {@link org.springframework.session.SessionRepository} implementation that uses
 * Spring's {@link JdbcOperations} to store sessions in a relational database. This
 * implementation does not support publishing of session events.
 * <p>
 * An example of how to create a new instance can be seen below:
 *
 * <pre class="code">
 * JdbcTemplate jdbcTemplate = new JdbcTemplate();
 *
 * // ... configure jdbcTemplate ...
 *
 * TransactionTemplate transactionTemplate = new TransactionTemplate();
 *
 * // ... configure transactionTemplate ...
 *
 * JdbcIndexedSessionRepository sessionRepository =
 *         new JdbcIndexedSessionRepository(jdbcTemplate, transactionTemplate);
 * </pre>
 *
 * For additional information on how to create and configure {@code JdbcTemplate} and
 * {@code TransactionTemplate}, refer to the <a href=
 * "https://docs.spring.io/spring/docs/current/spring-framework-reference/html/spring-data-tier.html">
 * Spring Framework Reference Documentation</a>.
 * <p>
 * By default, this implementation uses <code>SPRING_SESSION</code> and
 * <code>SPRING_SESSION_ATTRIBUTES</code> tables to store sessions. Note that the table
 * name can be customized using the {@link #setTableName(String)} method. In that case the
 * table used to store attributes will be named using the provided table name, suffixed
 * with <code>_ATTRIBUTES</code>.
 *
 * Depending on your database, the table definition can be described as below:
 *
 * <pre class="code">
 * CREATE TABLE SPRING_SESSION (
 *   PRIMARY_ID CHAR(36) NOT NULL,
 *   SESSION_ID CHAR(36) NOT NULL,
 *   CREATION_TIME BIGINT NOT NULL,
 *   LAST_ACCESS_TIME BIGINT NOT NULL,
 *   MAX_INACTIVE_INTERVAL INT NOT NULL,
 *   EXPIRY_TIME BIGINT NOT NULL,
 *   PRINCIPAL_NAME VARCHAR(100),
 *   CONSTRAINT SPRING_SESSION_PK PRIMARY KEY (PRIMARY_ID)
 * );
 *
 * CREATE UNIQUE INDEX SPRING_SESSION_IX1 ON SPRING_SESSION (SESSION_ID);
 * CREATE INDEX SPRING_SESSION_IX2 ON SPRING_SESSION (EXPIRY_TIME);
 * CREATE INDEX SPRING_SESSION_IX3 ON SPRING_SESSION (PRINCIPAL_NAME);
 *
 * CREATE TABLE SPRING_SESSION_ATTRIBUTES (
 *  SESSION_PRIMARY_ID CHAR(36) NOT NULL,
 *  ATTRIBUTE_NAME VARCHAR(200) NOT NULL,
 *  ATTRIBUTE_BYTES BYTEA NOT NULL,
 *  CONSTRAINT SPRING_SESSION_ATTRIBUTES_PK PRIMARY KEY (SESSION_PRIMARY_ID, ATTRIBUTE_NAME),
 *  CONSTRAINT SPRING_SESSION_ATTRIBUTES_FK FOREIGN KEY (SESSION_PRIMARY_ID) REFERENCES SPRING_SESSION(PRIMARY_ID) ON DELETE CASCADE
 * );
 *
 * CREATE INDEX SPRING_SESSION_ATTRIBUTES_IX1 ON SPRING_SESSION_ATTRIBUTES (SESSION_PRIMARY_ID);
 * </pre>
 *
 * Due to the differences between the various database vendors, especially when it comes
 * to storing binary data, make sure to use SQL script specific to your database. Scripts
 * for most major database vendors are packaged as
 * <code>org/springframework/session/jdbc/schema-*.sql</code>, where <code>*</code> is the
 * target database type.
 *
 * @author Vedran Pavic
 * @author Craig Andrews
 * @since 2.2.0
 */
public class JdbcIndexedSessionRepository implements
		FindByIndexNameSessionRepository<JdbcIndexedSessionRepository.JdbcSession>, InitializingBean, DisposableBean {

	/**
	 * The default name of database table used by Spring Session to store sessions.
	 */
	public static final String DEFAULT_TABLE_NAME = "SPRING_SESSION";

	/**
	 * The default cron expression used for expired session cleanup job.
	 */
	public static final String DEFAULT_CLEANUP_CRON = "0 * * * * *";

	private static final String SPRING_SECURITY_CONTEXT = "SPRING_SECURITY_CONTEXT";

	private static final String CREATE_SESSION_QUERY = """
			INSERT INTO %TABLE_NAME% (PRIMARY_ID, SESSION_ID, CREATION_TIME, LAST_ACCESS_TIME, MAX_INACTIVE_INTERVAL, EXPIRY_TIME, PRINCIPAL_NAME)
			VALUES (?, ?, ?, ?, ?, ?, ?)
			""";

	private static final String CREATE_SESSION_ATTRIBUTE_QUERY = """
			INSERT INTO %TABLE_NAME%_ATTRIBUTES (SESSION_PRIMARY_ID, ATTRIBUTE_NAME, ATTRIBUTE_BYTES)
			VALUES (?, ?, ?)
			""";

	private static final String GET_SESSION_QUERY = """
			SELECT S.PRIMARY_ID, S.SESSION_ID, S.CREATION_TIME, S.LAST_ACCESS_TIME, S.MAX_INACTIVE_INTERVAL, SA.ATTRIBUTE_NAME, SA.ATTRIBUTE_BYTES
			FROM %TABLE_NAME% S
			LEFT JOIN %TABLE_NAME%_ATTRIBUTES SA ON S.PRIMARY_ID = SA.SESSION_PRIMARY_ID
			WHERE S.SESSION_ID = ?
			""";

	private static final String UPDATE_SESSION_QUERY = """
			UPDATE %TABLE_NAME%
			SET SESSION_ID = ?, LAST_ACCESS_TIME = ?, MAX_INACTIVE_INTERVAL = ?, EXPIRY_TIME = ?, PRINCIPAL_NAME = ?
			WHERE PRIMARY_ID = ?
			""";

	private static final String UPDATE_SESSION_ATTRIBUTE_QUERY = """
			UPDATE %TABLE_NAME%_ATTRIBUTES
			SET ATTRIBUTE_BYTES = ?
			WHERE SESSION_PRIMARY_ID = ?
			AND ATTRIBUTE_NAME = ?
			""";

	private static final String DELETE_SESSION_ATTRIBUTE_QUERY = """
			DELETE FROM %TABLE_NAME%_ATTRIBUTES
			WHERE SESSION_PRIMARY_ID = ?
			AND ATTRIBUTE_NAME = ?
			""";

	private static final String DELETE_SESSION_QUERY = """
			DELETE FROM %TABLE_NAME%
			WHERE SESSION_ID = ?
			AND MAX_INACTIVE_INTERVAL >= 0
			""";

	private static final String LIST_SESSIONS_BY_PRINCIPAL_NAME_QUERY = """
			SELECT S.PRIMARY_ID, S.SESSION_ID, S.CREATION_TIME, S.LAST_ACCESS_TIME, S.MAX_INACTIVE_INTERVAL, SA.ATTRIBUTE_NAME, SA.ATTRIBUTE_BYTES
			FROM %TABLE_NAME% S
			LEFT JOIN %TABLE_NAME%_ATTRIBUTES SA ON S.PRIMARY_ID = SA.SESSION_PRIMARY_ID
			WHERE S.PRINCIPAL_NAME = ?
			""";

	private static final String DELETE_SESSIONS_BY_EXPIRY_TIME_QUERY = """
			DELETE FROM %TABLE_NAME%
			WHERE EXPIRY_TIME < ?
			""";

	private static final Log logger = LogFactory.getLog(JdbcIndexedSessionRepository.class);

	private final JdbcOperations jdbcOperations;

	private final TransactionOperations transactionOperations;

	private final ResultSetExtractor<List<JdbcSession>> extractor = new SessionResultSetExtractor();

	/**
	 * The name of database table used by Spring Session to store sessions.
	 */
	private String tableName = DEFAULT_TABLE_NAME;

	private String createSessionQuery;

	private String createSessionAttributeQuery;

	private String getSessionQuery;

	private String updateSessionQuery;

	private String updateSessionAttributeQuery;

	private String deleteSessionAttributeQuery;

	private String deleteSessionQuery;

	private String listSessionsByPrincipalNameQuery;

	private String deleteSessionsByExpiryTimeQuery;

	private Duration defaultMaxInactiveInterval = Duration.ofSeconds(MapSession.DEFAULT_MAX_INACTIVE_INTERVAL_SECONDS);

	private IndexResolver<Session> indexResolver = new DelegatingIndexResolver<>(new PrincipalNameIndexResolver<>());

	private ConversionService conversionService = createDefaultConversionService();

	private LobHandler lobHandler = new DefaultLobHandler();

	private FlushMode flushMode = FlushMode.ON_SAVE;

	private SaveMode saveMode = SaveMode.ON_SET_ATTRIBUTE;

	private String cleanupCron = DEFAULT_CLEANUP_CRON;

	private ThreadPoolTaskScheduler taskScheduler;

	private SessionIdGenerator sessionIdGenerator = UuidSessionIdGenerator.getInstance();

	/**
	 * Create a new {@link JdbcIndexedSessionRepository} instance which uses the provided
	 * {@link JdbcOperations} and {@link TransactionOperations} to manage sessions.
	 * @param jdbcOperations the {@link JdbcOperations} to use
	 * @param transactionOperations the {@link TransactionOperations} to use
	 */
	public JdbcIndexedSessionRepository(JdbcOperations jdbcOperations, TransactionOperations transactionOperations) {
		Assert.notNull(jdbcOperations, "jdbcOperations must not be null");
		Assert.notNull(transactionOperations, "transactionOperations must not be null");
		this.jdbcOperations = jdbcOperations;
		this.transactionOperations = transactionOperations;
		prepareQueries();
	}

	@Override
	public void afterPropertiesSet() {
		if (!Scheduled.CRON_DISABLED.equals(this.cleanupCron)) {
			this.taskScheduler = createTaskScheduler();
			this.taskScheduler.initialize();
			this.taskScheduler.schedule(this::cleanUpExpiredSessions, new CronTrigger(this.cleanupCron));
		}
	}

	private static ThreadPoolTaskScheduler createTaskScheduler() {
		ThreadPoolTaskScheduler taskScheduler = new ThreadPoolTaskScheduler();
		taskScheduler.setThreadNamePrefix("spring-session-");
		return taskScheduler;
	}

	@Override
	public void destroy() {
		if (this.taskScheduler != null) {
			this.taskScheduler.destroy();
		}
	}

	/**
	 * Set the name of database table used to store sessions.
	 * @param tableName the database table name
	 */
	public void setTableName(String tableName) {
		Assert.hasText(tableName, "Table name must not be empty");
		this.tableName = tableName.trim();
		prepareQueries();
	}

	/**
	 * Set the custom SQL query used to create the session.
	 * @param createSessionQuery the SQL query string
	 */
	public void setCreateSessionQuery(String createSessionQuery) {
		Assert.hasText(createSessionQuery, "Query must not be empty");
		this.createSessionQuery = getQuery(createSessionQuery);
	}

	/**
	 * Set the custom SQL query used to create the session attribute.
	 * @param createSessionAttributeQuery the SQL query string
	 */
	public void setCreateSessionAttributeQuery(String createSessionAttributeQuery) {
		Assert.hasText(createSessionAttributeQuery, "Query must not be empty");
		this.createSessionAttributeQuery = getQuery(createSessionAttributeQuery);
	}

	/**
	 * Set the custom SQL query used to retrieve the session.
	 * @param getSessionQuery the SQL query string
	 */
	public void setGetSessionQuery(String getSessionQuery) {
		Assert.hasText(getSessionQuery, "Query must not be empty");
		this.getSessionQuery = getQuery(getSessionQuery);
	}

	/**
	 * Set the custom SQL query used to update the session.
	 * @param updateSessionQuery the SQL query string
	 */
	public void setUpdateSessionQuery(String updateSessionQuery) {
		Assert.hasText(updateSessionQuery, "Query must not be empty");
		this.updateSessionQuery = getQuery(updateSessionQuery);
	}

	/**
	 * Set the custom SQL query used to update the session attribute.
	 * @param updateSessionAttributeQuery the SQL query string
	 */
	public void setUpdateSessionAttributeQuery(String updateSessionAttributeQuery) {
		Assert.hasText(updateSessionAttributeQuery, "Query must not be empty");
		this.updateSessionAttributeQuery = getQuery(updateSessionAttributeQuery);
	}

	/**
	 * Set the custom SQL query used to delete the session attribute.
	 * @param deleteSessionAttributeQuery the SQL query string
	 */
	public void setDeleteSessionAttributeQuery(String deleteSessionAttributeQuery) {
		Assert.hasText(deleteSessionAttributeQuery, "Query must not be empty");
		this.deleteSessionAttributeQuery = getQuery(deleteSessionAttributeQuery);
	}

	/**
	 * Set the custom SQL query used to delete the session.
	 * @param deleteSessionQuery the SQL query string
	 */
	public void setDeleteSessionQuery(String deleteSessionQuery) {
		Assert.hasText(deleteSessionQuery, "Query must not be empty");
		this.deleteSessionQuery = getQuery(deleteSessionQuery);
	}

	/**
	 * Set the custom SQL query used to retrieve the sessions by principal name.
	 * @param listSessionsByPrincipalNameQuery the SQL query string
	 */
	public void setListSessionsByPrincipalNameQuery(String listSessionsByPrincipalNameQuery) {
		Assert.hasText(listSessionsByPrincipalNameQuery, "Query must not be empty");
		this.listSessionsByPrincipalNameQuery = getQuery(listSessionsByPrincipalNameQuery);
	}

	/**
	 * Set the custom SQL query used to delete the sessions by last access time.
	 * @param deleteSessionsByExpiryTimeQuery the SQL query string
	 */
	public void setDeleteSessionsByExpiryTimeQuery(String deleteSessionsByExpiryTimeQuery) {
		Assert.hasText(deleteSessionsByExpiryTimeQuery, "Query must not be empty");
		this.deleteSessionsByExpiryTimeQuery = getQuery(deleteSessionsByExpiryTimeQuery);
	}

	/**
	 * Set the maximum inactive interval in seconds between requests before newly created
	 * sessions will be invalidated. A negative time indicates that the session will never
	 * time out. The default is 30 minutes.
	 * @param defaultMaxInactiveInterval the default maxInactiveInterval
	 */
	public void setDefaultMaxInactiveInterval(Duration defaultMaxInactiveInterval) {
		Assert.notNull(defaultMaxInactiveInterval, "defaultMaxInactiveInterval must not be null");
		this.defaultMaxInactiveInterval = defaultMaxInactiveInterval;
	}

	/**
	 * Set the maximum inactive interval in seconds between requests before newly created
	 * sessions will be invalidated. A negative time indicates that the session will never
	 * time out. The default is 1800 (30 minutes).
	 * @param defaultMaxInactiveInterval the default maxInactiveInterval in seconds
	 * @deprecated since 3.0.0, in favor of
	 * {@link #setDefaultMaxInactiveInterval(Duration)}
	 */
	@Deprecated(since = "3.0.0")
	public void setDefaultMaxInactiveInterval(Integer defaultMaxInactiveInterval) {
		setDefaultMaxInactiveInterval(Duration.ofSeconds(defaultMaxInactiveInterval));
	}

	/**
	 * Set the {@link IndexResolver} to use.
	 * @param indexResolver the index resolver
	 */
	public void setIndexResolver(IndexResolver<Session> indexResolver) {
		Assert.notNull(indexResolver, "indexResolver cannot be null");
		this.indexResolver = indexResolver;
	}

	public void setLobHandler(LobHandler lobHandler) {
		Assert.notNull(lobHandler, "LobHandler must not be null");
		this.lobHandler = lobHandler;
	}

	/**
	 * Sets the {@link ConversionService} to use.
	 * @param conversionService the converter to set
	 */
	public void setConversionService(ConversionService conversionService) {
		Assert.notNull(conversionService, "conversionService must not be null");
		this.conversionService = conversionService;
	}

	/**
	 * Set the flush mode. Default is {@link FlushMode#ON_SAVE}.
	 * @param flushMode the flush mode
	 */
	public void setFlushMode(FlushMode flushMode) {
		Assert.notNull(flushMode, "flushMode must not be null");
		this.flushMode = flushMode;
	}

	/**
	 * Set the save mode.
	 * @param saveMode the save mode
	 */
	public void setSaveMode(SaveMode saveMode) {
		Assert.notNull(saveMode, "saveMode must not be null");
		this.saveMode = saveMode;
	}

	/**
	 * Set the cleanup cron expression.
	 * @param cleanupCron the cleanup cron expression
	 * @since 3.0.0
	 * @see CronExpression
	 * @see Scheduled#CRON_DISABLED
	 */
	public void setCleanupCron(String cleanupCron) {
		Assert.notNull(cleanupCron, "cleanupCron must not be null");
		if (!Scheduled.CRON_DISABLED.equals(cleanupCron)) {
			Assert.isTrue(CronExpression.isValidExpression(cleanupCron), "cleanupCron must be valid");
		}
		this.cleanupCron = cleanupCron;
	}

	@Override
	public JdbcSession createSession() {
		MapSession delegate = new MapSession(this.sessionIdGenerator);
		delegate.setMaxInactiveInterval(this.defaultMaxInactiveInterval);
		JdbcSession session = new JdbcSession(delegate, UUID.randomUUID().toString(), true);
		session.flushIfRequired();
		return session;
	}

	@Override
	public void save(final JdbcSession session) {
		session.save();
	}

	@Override
	public JdbcSession findById(final String id) {
		final JdbcSession session = this.transactionOperations.execute((status) -> {
			List<JdbcSession> sessions = JdbcIndexedSessionRepository.this.jdbcOperations.query(
					JdbcIndexedSessionRepository.this.getSessionQuery, (ps) -> ps.setString(1, id),
					JdbcIndexedSessionRepository.this.extractor);
			if (sessions.isEmpty()) {
				return null;
			}
			return sessions.get(0);
		});

		if (session != null) {
			if (session.isExpired()) {
				deleteById(id);
			}
			else {
				return session;
			}
		}
		return null;
	}

	@Override
	public void deleteById(final String id) {
		this.transactionOperations.executeWithoutResult((status) -> JdbcIndexedSessionRepository.this.jdbcOperations
				.update(JdbcIndexedSessionRepository.this.deleteSessionQuery, id));
	}

	@Override
	public Map<String, JdbcSession> findByIndexNameAndIndexValue(String indexName, final String indexValue) {
		if (!PRINCIPAL_NAME_INDEX_NAME.equals(indexName)) {
			return Collections.emptyMap();
		}

		List<JdbcSession> sessions = this.transactionOperations
				.execute((status) -> JdbcIndexedSessionRepository.this.jdbcOperations.query(
						JdbcIndexedSessionRepository.this.listSessionsByPrincipalNameQuery,
						(ps) -> ps.setString(1, indexValue), JdbcIndexedSessionRepository.this.extractor));

		Map<String, JdbcSession> sessionMap = new HashMap<>(sessions.size());

		for (JdbcSession session : sessions) {
			sessionMap.put(session.getId(), session);
		}

		return sessionMap;
	}

	private void insertSessionAttributes(JdbcSession session, List<String> attributeNames) {
		Assert.notEmpty(attributeNames, "attributeNames must not be null or empty");
		try (LobCreator lobCreator = this.lobHandler.getLobCreator()) {
			if (attributeNames.size() > 1) {
				try {
					this.jdbcOperations.batchUpdate(this.createSessionAttributeQuery,
							new BatchPreparedStatementSetter() {

								@Override
								public void setValues(PreparedStatement ps, int i) throws SQLException {
									String attributeName = attributeNames.get(i);
									ps.setString(1, session.primaryKey);
									ps.setString(2, attributeName);
									lobCreator.setBlobAsBytes(ps, 3, serialize(session.getAttribute(attributeName)));
								}

								@Override
								public int getBatchSize() {
									return attributeNames.size();
								}

							});
				}
				catch (DuplicateKeyException ex) {
					throw ex;
				}
				catch (DataIntegrityViolationException ex) {
					// parent record not found - we are ignoring this error because we
					// assume that a concurrent request has removed the session
					if (logger.isTraceEnabled()) {
						logger.trace("Not able to create session attributes", ex);
					}
				}
			}
			else {
				try {
					this.jdbcOperations.update(this.createSessionAttributeQuery, (ps) -> {
						String attributeName = attributeNames.get(0);
						ps.setString(1, session.primaryKey);
						ps.setString(2, attributeName);
						lobCreator.setBlobAsBytes(ps, 3, serialize(session.getAttribute(attributeName)));
					});
				}
				catch (DuplicateKeyException ex) {
					throw ex;
				}
				catch (DataIntegrityViolationException ex) {
					// parent record not found - we are ignoring this error because we
					// assume that a concurrent request has removed the session
					if (logger.isTraceEnabled()) {
						logger.trace("Not able to create session attributes", ex);
					}
				}
			}
		}
	}

	private void updateSessionAttributes(JdbcSession session, List<String> attributeNames) {
		Assert.notEmpty(attributeNames, "attributeNames must not be null or empty");
		try (LobCreator lobCreator = this.lobHandler.getLobCreator()) {
			if (attributeNames.size() > 1) {
				this.jdbcOperations.batchUpdate(this.updateSessionAttributeQuery, new BatchPreparedStatementSetter() {

					@Override
					public void setValues(PreparedStatement ps, int i) throws SQLException {
						String attributeName = attributeNames.get(i);
						lobCreator.setBlobAsBytes(ps, 1, serialize(session.getAttribute(attributeName)));
						ps.setString(2, session.primaryKey);
						ps.setString(3, attributeName);
					}

					@Override
					public int getBatchSize() {
						return attributeNames.size();
					}

				});
			}
			else {
				this.jdbcOperations.update(this.updateSessionAttributeQuery, (ps) -> {
					String attributeName = attributeNames.get(0);
					lobCreator.setBlobAsBytes(ps, 1, serialize(session.getAttribute(attributeName)));
					ps.setString(2, session.primaryKey);
					ps.setString(3, attributeName);
				});
			}
		}
	}

	private void deleteSessionAttributes(JdbcSession session, List<String> attributeNames) {
		Assert.notEmpty(attributeNames, "attributeNames must not be null or empty");
		if (attributeNames.size() > 1) {
			this.jdbcOperations.batchUpdate(this.deleteSessionAttributeQuery, new BatchPreparedStatementSetter() {

				@Override
				public void setValues(PreparedStatement ps, int i) throws SQLException {
					String attributeName = attributeNames.get(i);
					ps.setString(1, session.primaryKey);
					ps.setString(2, attributeName);
				}

				@Override
				public int getBatchSize() {
					return attributeNames.size();
				}

			});
		}
		else {
			this.jdbcOperations.update(this.deleteSessionAttributeQuery, (ps) -> {
				String attributeName = attributeNames.get(0);
				ps.setString(1, session.primaryKey);
				ps.setString(2, attributeName);
			});
		}
	}

	public void cleanUpExpiredSessions() {
		Integer deletedCount = this.transactionOperations
				.execute((status) -> JdbcIndexedSessionRepository.this.jdbcOperations.update(
						JdbcIndexedSessionRepository.this.deleteSessionsByExpiryTimeQuery, System.currentTimeMillis()));

		if (logger.isDebugEnabled()) {
			logger.debug("Cleaned up " + deletedCount + " expired sessions");
		}
	}

	private static GenericConversionService createDefaultConversionService() {
		GenericConversionService converter = new GenericConversionService();
		converter.addConverter(Object.class, byte[].class, new SerializingConverter());
		converter.addConverter(byte[].class, Object.class, new DeserializingConverter());
		return converter;
	}

	private String getQuery(String base) {
		return StringUtils.replace(base, "%TABLE_NAME%", this.tableName);
	}

	private void prepareQueries() {
		this.createSessionQuery = getQuery(CREATE_SESSION_QUERY);
		this.createSessionAttributeQuery = getQuery(CREATE_SESSION_ATTRIBUTE_QUERY);
		this.getSessionQuery = getQuery(GET_SESSION_QUERY);
		this.updateSessionQuery = getQuery(UPDATE_SESSION_QUERY);
		this.updateSessionAttributeQuery = getQuery(UPDATE_SESSION_ATTRIBUTE_QUERY);
		this.deleteSessionAttributeQuery = getQuery(DELETE_SESSION_ATTRIBUTE_QUERY);
		this.deleteSessionQuery = getQuery(DELETE_SESSION_QUERY);
		this.listSessionsByPrincipalNameQuery = getQuery(LIST_SESSIONS_BY_PRINCIPAL_NAME_QUERY);
		this.deleteSessionsByExpiryTimeQuery = getQuery(DELETE_SESSIONS_BY_EXPIRY_TIME_QUERY);
	}

	private LobHandler getLobHandler() {
		return this.lobHandler;
	}

	private byte[] serialize(Object object) {
		return (byte[]) this.conversionService.convert(object, TypeDescriptor.valueOf(Object.class),
				TypeDescriptor.valueOf(byte[].class));
	}

	private Object deserialize(byte[] bytes) {
		return this.conversionService.convert(bytes, TypeDescriptor.valueOf(byte[].class),
				TypeDescriptor.valueOf(Object.class));
	}

	/**
	 * Set the {@link SessionIdGenerator} to use to generate session ids.
	 * @param sessionIdGenerator the {@link SessionIdGenerator} to use
	 * @since 3.2
	 */
	public void setSessionIdGenerator(SessionIdGenerator sessionIdGenerator) {
		Assert.notNull(sessionIdGenerator, "sessionIdGenerator cannot be null");
		this.sessionIdGenerator = sessionIdGenerator;
	}

	private enum DeltaValue {

		ADDED, UPDATED, REMOVED

	}

	private static <T> Supplier<T> value(T value) {
		return (value != null) ? () -> value : null;
	}

	private static <T> Supplier<T> lazily(Supplier<T> supplier) {
		Supplier<T> lazySupplier = new Supplier<T>() {

			private T value;

			@Override
			public T get() {
				if (this.value == null) {
					this.value = supplier.get();
				}
				return this.value;
			}

		};

		return (supplier != null) ? lazySupplier : null;
	}

	/**
	 * The {@link Session} to use for {@link JdbcIndexedSessionRepository}.
	 *
	 * @author Vedran Pavic
	 */
	final class JdbcSession implements Session {

		private final MapSession delegate;

		private final String primaryKey;

		private boolean isNew;

		private boolean changed;

		private Map<String, DeltaValue> delta = new HashMap<>();

		JdbcSession(MapSession delegate, String primaryKey, boolean isNew) {
			this.delegate = delegate;
			this.primaryKey = primaryKey;
			this.isNew = isNew;
			if (this.isNew || (JdbcIndexedSessionRepository.this.saveMode == SaveMode.ALWAYS)) {
				getAttributeNames().forEach((attributeName) -> this.delta.put(attributeName, DeltaValue.UPDATED));
			}
		}

		boolean isNew() {
			return this.isNew;
		}

		boolean isChanged() {
			return this.changed;
		}

		Map<String, DeltaValue> getDelta() {
			return this.delta;
		}

		void clearChangeFlags() {
			this.isNew = false;
			this.changed = false;
			this.delta.clear();
		}

		Instant getExpiryTime() {
			if (getMaxInactiveInterval().isNegative()) {
				return Instant.ofEpochMilli(Long.MAX_VALUE);
			}
			return getLastAccessedTime().plus(getMaxInactiveInterval());
		}

		@Override
		public String getId() {
			return this.delegate.getId();
		}

		@Override
		public String changeSessionId() {
			this.changed = true;
			String newSessionId = JdbcIndexedSessionRepository.this.sessionIdGenerator.generate();
			this.delegate.setId(newSessionId);
			return newSessionId;
		}

		@Override
		public <T> T getAttribute(String attributeName) {
			Supplier<T> supplier = this.delegate.getAttribute(attributeName);
			if (supplier == null) {
				return null;
			}
			T attributeValue = supplier.get();
			if (attributeValue != null
					&& JdbcIndexedSessionRepository.this.saveMode.equals(SaveMode.ON_GET_ATTRIBUTE)) {
				this.delta.merge(attributeName, DeltaValue.UPDATED, (oldDeltaValue,
						deltaValue) -> (oldDeltaValue == DeltaValue.ADDED) ? oldDeltaValue : deltaValue);
			}
			return attributeValue;
		}

		@Override
		public Set<String> getAttributeNames() {
			return this.delegate.getAttributeNames();
		}

		@Override
		public void setAttribute(String attributeName, Object attributeValue) {
			boolean attributeExists = (this.delegate.getAttribute(attributeName) != null);
			boolean attributeRemoved = (attributeValue == null);
			if (!attributeExists && attributeRemoved) {
				return;
			}
			if (attributeExists) {
				if (attributeRemoved) {
					this.delta.merge(attributeName, DeltaValue.REMOVED,
							(oldDeltaValue, deltaValue) -> (oldDeltaValue == DeltaValue.ADDED) ? null : deltaValue);
				}
				else {
					this.delta.merge(attributeName, DeltaValue.UPDATED, (oldDeltaValue,
							deltaValue) -> (oldDeltaValue == DeltaValue.ADDED) ? oldDeltaValue : deltaValue);
				}
			}
			else {
				this.delta.merge(attributeName, DeltaValue.ADDED, (oldDeltaValue,
						deltaValue) -> (oldDeltaValue == DeltaValue.ADDED) ? oldDeltaValue : DeltaValue.UPDATED);
			}
			this.delegate.setAttribute(attributeName, value(attributeValue));
			if (PRINCIPAL_NAME_INDEX_NAME.equals(attributeName) || SPRING_SECURITY_CONTEXT.equals(attributeName)) {
				this.changed = true;
			}
			flushIfRequired();
		}

		@Override
		public void removeAttribute(String attributeName) {
			setAttribute(attributeName, null);
		}

		@Override
		public Instant getCreationTime() {
			return this.delegate.getCreationTime();
		}

		@Override
		public void setLastAccessedTime(Instant lastAccessedTime) {
			this.delegate.setLastAccessedTime(lastAccessedTime);
			this.changed = true;
			flushIfRequired();
		}

		@Override
		public Instant getLastAccessedTime() {
			return this.delegate.getLastAccessedTime();
		}

		@Override
		public void setMaxInactiveInterval(Duration interval) {
			this.delegate.setMaxInactiveInterval(interval);
			this.changed = true;
			flushIfRequired();
		}

		@Override
		public Duration getMaxInactiveInterval() {
			return this.delegate.getMaxInactiveInterval();
		}

		@Override
		public boolean isExpired() {
			return this.delegate.isExpired();
		}

		private void flushIfRequired() {
			if (JdbcIndexedSessionRepository.this.flushMode == FlushMode.IMMEDIATE) {
				save();
			}
		}

		private void save() {
			if (this.isNew) {
				JdbcIndexedSessionRepository.this.transactionOperations.executeWithoutResult((status) -> {
					Map<String, String> indexes = JdbcIndexedSessionRepository.this.indexResolver
							.resolveIndexesFor(JdbcSession.this);
					JdbcIndexedSessionRepository.this.jdbcOperations
							.update(JdbcIndexedSessionRepository.this.createSessionQuery, (ps) -> {
								ps.setString(1, JdbcSession.this.primaryKey);
								ps.setString(2, getId());
								ps.setLong(3, getCreationTime().toEpochMilli());
								ps.setLong(4, getLastAccessedTime().toEpochMilli());
								ps.setInt(5, (int) getMaxInactiveInterval().getSeconds());
								ps.setLong(6, getExpiryTime().toEpochMilli());
								ps.setString(7, indexes.get(PRINCIPAL_NAME_INDEX_NAME));
							});
					Set<String> attributeNames = getAttributeNames();
					if (!attributeNames.isEmpty()) {
						insertSessionAttributes(JdbcSession.this, new ArrayList<>(attributeNames));
					}
				});
			}
			else {
				JdbcIndexedSessionRepository.this.transactionOperations.executeWithoutResult((status) -> {
					if (JdbcSession.this.changed) {
						Map<String, String> indexes = JdbcIndexedSessionRepository.this.indexResolver
								.resolveIndexesFor(JdbcSession.this);
						JdbcIndexedSessionRepository.this.jdbcOperations
								.update(JdbcIndexedSessionRepository.this.updateSessionQuery, (ps) -> {
									ps.setString(1, getId());
									ps.setLong(2, getLastAccessedTime().toEpochMilli());
									ps.setInt(3, (int) getMaxInactiveInterval().getSeconds());
									ps.setLong(4, getExpiryTime().toEpochMilli());
									ps.setString(5, indexes.get(PRINCIPAL_NAME_INDEX_NAME));
									ps.setString(6, JdbcSession.this.primaryKey);
								});
					}
					List<String> addedAttributeNames = JdbcSession.this.delta.entrySet().stream()
							.filter((entry) -> entry.getValue() == DeltaValue.ADDED).map(Map.Entry::getKey)
							.collect(Collectors.toList());
					if (!addedAttributeNames.isEmpty()) {
						insertSessionAttributes(JdbcSession.this, addedAttributeNames);
					}
					List<String> updatedAttributeNames = JdbcSession.this.delta.entrySet().stream()
							.filter((entry) -> entry.getValue() == DeltaValue.UPDATED).map(Map.Entry::getKey)
							.collect(Collectors.toList());
					if (!updatedAttributeNames.isEmpty()) {
						updateSessionAttributes(JdbcSession.this, updatedAttributeNames);
					}
					List<String> removedAttributeNames = JdbcSession.this.delta.entrySet().stream()
							.filter((entry) -> entry.getValue() == DeltaValue.REMOVED).map(Map.Entry::getKey)
							.collect(Collectors.toList());
					if (!removedAttributeNames.isEmpty()) {
						deleteSessionAttributes(JdbcSession.this, removedAttributeNames);
					}
				});
			}
			clearChangeFlags();
		}

	}

	private class SessionResultSetExtractor implements ResultSetExtractor<List<JdbcSession>> {

		@Override
		public List<JdbcSession> extractData(ResultSet rs) throws SQLException, DataAccessException {
			List<JdbcSession> sessions = new ArrayList<>();
			while (rs.next()) {
				String id = rs.getString("SESSION_ID");
				JdbcSession session;
				if (sessions.size() > 0 && getLast(sessions).getId().equals(id)) {
					session = getLast(sessions);
				}
				else {
					MapSession delegate = new MapSession(id);
					String primaryKey = rs.getString("PRIMARY_ID");
					delegate.setCreationTime(Instant.ofEpochMilli(rs.getLong("CREATION_TIME")));
					delegate.setLastAccessedTime(Instant.ofEpochMilli(rs.getLong("LAST_ACCESS_TIME")));
					delegate.setMaxInactiveInterval(Duration.ofSeconds(rs.getInt("MAX_INACTIVE_INTERVAL")));
					session = new JdbcSession(delegate, primaryKey, false);
				}
				String attributeName = rs.getString("ATTRIBUTE_NAME");
				if (attributeName != null) {
					byte[] bytes = getLobHandler().getBlobAsBytes(rs, "ATTRIBUTE_BYTES");
					session.delegate.setAttribute(attributeName, lazily(() -> deserialize(bytes)));
				}
				sessions.add(session);
			}
			return sessions;
		}

		private JdbcSession getLast(List<JdbcSession> sessions) {
			return sessions.get(sessions.size() - 1);
		}

	}

}
