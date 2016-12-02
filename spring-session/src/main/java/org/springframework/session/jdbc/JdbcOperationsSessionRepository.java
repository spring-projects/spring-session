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

package org.springframework.session.jdbc;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.sql.DataSource;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.core.convert.support.GenericConversionService;
import org.springframework.core.serializer.support.DeserializingConverter;
import org.springframework.core.serializer.support.SerializingConverter;
import org.springframework.dao.DataAccessException;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementSetter;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.support.lob.DefaultLobHandler;
import org.springframework.jdbc.support.lob.LobHandler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.session.ExpiringSession;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.MapSession;
import org.springframework.session.Session;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;
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
 * PlatformTransactionManager transactionManager = new DataSourceTransactionManager();
 *
 * // ... configure transactionManager ...
 *
 * JdbcOperationsSessionRepository sessionRepository =
 *         new JdbcOperationsSessionRepository(jdbcTemplate, transactionManager);
 * </pre>
 *
 * For additional information on how to create and configure {@link JdbcTemplate} and
 * {@link PlatformTransactionManager}, refer to the
 * <a href="http://docs.spring.io/spring/docs/current/spring-framework-reference/html/spring-data-tier.html">
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
 *   SESSION_ID CHAR(36),
 *   CREATION_TIME BIGINT NOT NULL,
 *   LAST_ACCESS_TIME BIGINT NOT NULL,
 *   MAX_INACTIVE_INTERVAL INT NOT NULL,
 *   PRINCIPAL_NAME VARCHAR(100),
 *   CONSTRAINT SPRING_SESSION_PK PRIMARY KEY (SESSION_ID)
 * );
 *
 * CREATE INDEX SPRING_SESSION_IX1 ON SPRING_SESSION (LAST_ACCESS_TIME);
 *
 * CREATE TABLE SPRING_SESSION_ATTRIBUTES (
 *  SESSION_ID CHAR(36),
 *  ATTRIBUTE_NAME VARCHAR(200),
 *  ATTRIBUTE_BYTES BYTEA,
 *  CONSTRAINT SPRING_SESSION_ATTRIBUTES_PK PRIMARY KEY (SESSION_ID, ATTRIBUTE_NAME),
 *  CONSTRAINT SPRING_SESSION_ATTRIBUTES_FK FOREIGN KEY (SESSION_ID) REFERENCES SPRING_SESSION(SESSION_ID) ON DELETE CASCADE
 * );
 *
 * CREATE INDEX SPRING_SESSION_ATTRIBUTES_IX1 ON SPRING_SESSION_ATTRIBUTES (SESSION_ID);
 * </pre>
 *
 * Due to the differences between the various database vendors, especially when it comes
 * to storing binary data, make sure to use SQL script specific to your database. Scripts
 * for most major database vendors are packaged as
 * <code>org/springframework/session/jdbc/schema-*.sql</code>, where <code>*</code> is the
 * target database type.
 *
 * @author Vedran Pavic
 * @since 1.2.0
 */
public class JdbcOperationsSessionRepository implements
		FindByIndexNameSessionRepository<JdbcOperationsSessionRepository.JdbcSession> {

	/**
	 * The default name of database table used by Spring Session to store sessions.
	 */
	public static final String DEFAULT_TABLE_NAME = "SPRING_SESSION";

	private static final String SPRING_SECURITY_CONTEXT = "SPRING_SECURITY_CONTEXT";

	private static final String CREATE_SESSION_QUERY =
			"INSERT INTO %TABLE_NAME%(SESSION_ID, CREATION_TIME, LAST_ACCESS_TIME, MAX_INACTIVE_INTERVAL, PRINCIPAL_NAME) " +
					"VALUES (?, ?, ?, ?, ?)";

	private static final String CREATE_SESSION_ATTRIBUTE_QUERY =
			"INSERT INTO %TABLE_NAME%_ATTRIBUTES(SESSION_ID, ATTRIBUTE_NAME, ATTRIBUTE_BYTES) " +
					"VALUES (?, ?, ?)";

	private static final String GET_SESSION_QUERY =
			"SELECT S.SESSION_ID, S.CREATION_TIME, S.LAST_ACCESS_TIME, S.MAX_INACTIVE_INTERVAL, SA.ATTRIBUTE_NAME, SA.ATTRIBUTE_BYTES " +
					"FROM %TABLE_NAME% S " +
					"LEFT OUTER JOIN %TABLE_NAME%_ATTRIBUTES SA ON S.SESSION_ID = SA.SESSION_ID " +
					"WHERE S.SESSION_ID = ?";

	private static final String UPDATE_SESSION_QUERY =
			"UPDATE %TABLE_NAME% SET LAST_ACCESS_TIME = ?, MAX_INACTIVE_INTERVAL = ?, PRINCIPAL_NAME = ? " +
					"WHERE SESSION_ID = ?";

	private static final String UPDATE_SESSION_ATTRIBUTE_QUERY =
			"UPDATE %TABLE_NAME%_ATTRIBUTES SET ATTRIBUTE_BYTES = ? " +
					"WHERE SESSION_ID = ? " +
					"AND ATTRIBUTE_NAME = ?";

	private static final String DELETE_SESSION_ATTRIBUTE_QUERY =
			"DELETE FROM %TABLE_NAME%_ATTRIBUTES " +
					"WHERE SESSION_ID = ? " +
					"AND ATTRIBUTE_NAME = ?";

	private static final String DELETE_SESSION_QUERY =
			"DELETE FROM %TABLE_NAME% " +
					"WHERE SESSION_ID = ?";

	private static final String LIST_SESSIONS_BY_PRINCIPAL_NAME_QUERY =
			"SELECT S.SESSION_ID, S.CREATION_TIME, S.LAST_ACCESS_TIME, S.MAX_INACTIVE_INTERVAL, SA.ATTRIBUTE_NAME, SA.ATTRIBUTE_BYTES " +
					"FROM %TABLE_NAME% S " +
					"LEFT OUTER JOIN %TABLE_NAME%_ATTRIBUTES SA ON S.SESSION_ID = SA.SESSION_ID " +
					"WHERE S.PRINCIPAL_NAME = ?";

	private static final String DELETE_SESSIONS_BY_LAST_ACCESS_TIME_QUERY =
			"DELETE FROM %TABLE_NAME% " +
					"WHERE MAX_INACTIVE_INTERVAL < (? - LAST_ACCESS_TIME) / 1000";

	private static final Log logger = LogFactory
			.getLog(JdbcOperationsSessionRepository.class);

	private static final PrincipalNameResolver PRINCIPAL_NAME_RESOLVER = new PrincipalNameResolver();

	private final JdbcOperations jdbcOperations;

	private final TransactionOperations transactionOperations;

	private final ResultSetExtractor<List<ExpiringSession>> extractor =
			new ExpiringSessionResultSetExtractor();

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

	private String deleteSessionsByLastAccessTimeQuery;

	/**
	 * If non-null, this value is used to override the default value for
	 * {@link JdbcSession#setMaxInactiveIntervalInSeconds(int)}.
	 */
	private Integer defaultMaxInactiveInterval;

	private ConversionService conversionService;

	private LobHandler lobHandler = new DefaultLobHandler();

	/**
	 * Create a new {@link JdbcOperationsSessionRepository} instance which uses the
	 * default {@link JdbcOperations} to manage sessions.
	 * @param dataSource the {@link DataSource} to use
	 * @param transactionManager the {@link PlatformTransactionManager} to use
	 */
	public JdbcOperationsSessionRepository(DataSource dataSource,
			PlatformTransactionManager transactionManager) {
		this(createDefaultJdbcTemplate(dataSource), transactionManager);
	}

	/**
	 * Create a new {@link JdbcOperationsSessionRepository} instance which uses the
	 * provided {@link JdbcOperations} to manage sessions.
	 * @param jdbcOperations the {@link JdbcOperations} to use
	 * @param transactionManager the {@link PlatformTransactionManager} to use
	 */
	public JdbcOperationsSessionRepository(JdbcOperations jdbcOperations,
			PlatformTransactionManager transactionManager) {
		Assert.notNull(jdbcOperations, "JdbcOperations must not be null");
		this.jdbcOperations = jdbcOperations;
		this.transactionOperations = createTransactionTemplate(transactionManager);
		this.conversionService = createDefaultConversionService();
		prepareQueries();
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
		this.createSessionQuery = createSessionQuery;
	}

	/**
	 * Set the custom SQL query used to create the session attribute.
	 * @param createSessionAttributeQuery the SQL query string
	 */
	public void setCreateSessionAttributeQuery(String createSessionAttributeQuery) {
		Assert.hasText(createSessionAttributeQuery, "Query must not be empty");
		this.createSessionAttributeQuery = createSessionAttributeQuery;
	}

	/**
	 * Set the custom SQL query used to retrieve the session.
	 * @param getSessionQuery the SQL query string
	 */
	public void setGetSessionQuery(String getSessionQuery) {
		Assert.hasText(getSessionQuery, "Query must not be empty");
		this.getSessionQuery = getSessionQuery;
	}

	/**
	 * Set the custom SQL query used to update the session.
	 * @param updateSessionQuery the SQL query string
	 */
	public void setUpdateSessionQuery(String updateSessionQuery) {
		Assert.hasText(updateSessionQuery, "Query must not be empty");
		this.updateSessionQuery = updateSessionQuery;
	}

	/**
	 * Set the custom SQL query used to update the session attribute.
	 * @param updateSessionAttributeQuery the SQL query string
	 */
	public void setUpdateSessionAttributeQuery(String updateSessionAttributeQuery) {
		Assert.hasText(updateSessionAttributeQuery, "Query must not be empty");
		this.updateSessionAttributeQuery = updateSessionAttributeQuery;
	}

	/**
	 * Set the custom SQL query used to delete the session attribute.
	 * @param deleteSessionAttributeQuery the SQL query string
	 */
	public void setDeleteSessionAttributeQuery(String deleteSessionAttributeQuery) {
		Assert.hasText(deleteSessionAttributeQuery, "Query must not be empty");
		this.deleteSessionAttributeQuery = deleteSessionAttributeQuery;
	}

	/**
	 * Set the custom SQL query used to delete the session.
	 * @param deleteSessionQuery the SQL query string
	 */
	public void setDeleteSessionQuery(String deleteSessionQuery) {
		Assert.hasText(deleteSessionQuery, "Query must not be empty");
		this.deleteSessionQuery = deleteSessionQuery;
	}

	/**
	 * Set the custom SQL query used to retrieve the sessions by principal name.
	 * @param listSessionsByPrincipalNameQuery the SQL query string
	 */
	public void setListSessionsByPrincipalNameQuery(String listSessionsByPrincipalNameQuery) {
		Assert.hasText(listSessionsByPrincipalNameQuery, "Query must not be empty");
		this.listSessionsByPrincipalNameQuery = listSessionsByPrincipalNameQuery;
	}

	/**
	 * Set the custom SQL query used to delete the sessions by last access time.
	 * @param deleteSessionsByLastAccessTimeQuery the SQL query string
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

	public JdbcSession createSession() {
		JdbcSession session = new JdbcSession();
		if (this.defaultMaxInactiveInterval != null) {
			session.setMaxInactiveIntervalInSeconds(this.defaultMaxInactiveInterval);
		}
		return session;
	}

	public void save(final JdbcSession session) {
		if (session.isNew()) {
			this.transactionOperations.execute(new TransactionCallbackWithoutResult() {

				protected void doInTransactionWithoutResult(TransactionStatus status) {
					JdbcOperationsSessionRepository.this.jdbcOperations.update(
							JdbcOperationsSessionRepository.this.createSessionQuery,
							new PreparedStatementSetter() {

								public void setValues(PreparedStatement ps) throws SQLException {
									ps.setString(1, session.getId());
									ps.setLong(2, session.getCreationTime());
									ps.setLong(3, session.getLastAccessedTime());
									ps.setInt(4, session.getMaxInactiveIntervalInSeconds());
									ps.setString(5, session.getPrincipalName());
								}

							});
					if (!session.getAttributeNames().isEmpty()) {
						final List<String> attributeNames = new ArrayList<String>(session.getAttributeNames());
						JdbcOperationsSessionRepository.this.jdbcOperations.batchUpdate(
								JdbcOperationsSessionRepository.this.createSessionAttributeQuery,
								new BatchPreparedStatementSetter() {

									public void setValues(PreparedStatement ps, int i) throws SQLException {
										String attributeName = attributeNames.get(i);
										ps.setString(1, session.getId());
										ps.setString(2, attributeName);
										serialize(ps, 3, session.getAttribute(attributeName));
									}

									public int getBatchSize() {
										return attributeNames.size();
									}

								});
					}
				}

			});
		}
		else {
			this.transactionOperations.execute(new TransactionCallbackWithoutResult() {

				protected void doInTransactionWithoutResult(TransactionStatus status) {
					if (session.isChanged()) {
						JdbcOperationsSessionRepository.this.jdbcOperations.update(
								JdbcOperationsSessionRepository.this.updateSessionQuery,
								new PreparedStatementSetter() {

									public void setValues(PreparedStatement ps)
											throws SQLException {
										ps.setLong(1, session.getLastAccessedTime());
										ps.setInt(2, session.getMaxInactiveIntervalInSeconds());
										ps.setString(3, session.getPrincipalName());
										ps.setString(4, session.getId());
									}

								});
					}
					Map<String, Object> delta = session.getDelta();
					if (!delta.isEmpty()) {
						for (final Map.Entry<String, Object> entry : delta.entrySet()) {
							if (entry.getValue() == null) {
								JdbcOperationsSessionRepository.this.jdbcOperations.update(
										JdbcOperationsSessionRepository.this.deleteSessionAttributeQuery,
										new PreparedStatementSetter() {

											public void setValues(PreparedStatement ps) throws SQLException {
												ps.setString(1, session.getId());
												ps.setString(2, entry.getKey());
											}

										});
							}
							else {
								int updatedCount = JdbcOperationsSessionRepository.this.jdbcOperations.update(
										JdbcOperationsSessionRepository.this.updateSessionAttributeQuery,
										new PreparedStatementSetter() {

											public void setValues(PreparedStatement ps) throws SQLException {
												serialize(ps, 1, entry.getValue());
												ps.setString(2, session.getId());
												ps.setString(3, entry.getKey());
											}

										});
								if (updatedCount == 0) {
									JdbcOperationsSessionRepository.this.jdbcOperations.update(
											JdbcOperationsSessionRepository.this.createSessionAttributeQuery,
											new PreparedStatementSetter() {

												public void setValues(PreparedStatement ps) throws SQLException {
													ps.setString(1, session.getId());
													ps.setString(2, entry.getKey());
													serialize(ps, 3, entry.getValue());
												}

											});
								}
							}
						}
					}
				}

			});
		}
		session.clearChangeFlags();
	}

	public JdbcSession getSession(final String id) {
		final ExpiringSession session = this.transactionOperations.execute(new TransactionCallback<ExpiringSession>() {

			public ExpiringSession doInTransaction(TransactionStatus status) {
				List<ExpiringSession> sessions = JdbcOperationsSessionRepository.this.jdbcOperations.query(
						JdbcOperationsSessionRepository.this.getSessionQuery,
						new PreparedStatementSetter() {

							public void setValues(PreparedStatement ps) throws SQLException {
								ps.setString(1, id);
							}

						},
						JdbcOperationsSessionRepository.this.extractor
				);
				if (sessions.isEmpty()) {
					return null;
				}
				return sessions.get(0);
			}

		});

		if (session != null) {
			if (session.isExpired()) {
				delete(id);
			}
			else {
				return new JdbcSession(session);
			}
		}
		return null;
	}

	public void delete(final String id) {
		this.transactionOperations.execute(new TransactionCallbackWithoutResult() {

			protected void doInTransactionWithoutResult(TransactionStatus status) {
				JdbcOperationsSessionRepository.this.jdbcOperations.update(
						JdbcOperationsSessionRepository.this.deleteSessionQuery, id);
			}

		});
	}

	public Map<String, JdbcSession> findByIndexNameAndIndexValue(String indexName,
			final String indexValue) {
		if (!PRINCIPAL_NAME_INDEX_NAME.equals(indexName)) {
			return Collections.emptyMap();
		}

		List<ExpiringSession> sessions = this.transactionOperations.execute(new TransactionCallback<List<ExpiringSession>>() {

			public List<ExpiringSession> doInTransaction(TransactionStatus status) {
				return JdbcOperationsSessionRepository.this.jdbcOperations.query(
						JdbcOperationsSessionRepository.this.listSessionsByPrincipalNameQuery,
						new PreparedStatementSetter() {

							public void setValues(PreparedStatement ps) throws SQLException {
								ps.setString(1, indexValue);
							}

						},
						JdbcOperationsSessionRepository.this.extractor
				);
			}

		});

		Map<String, JdbcSession> sessionMap = new HashMap<String, JdbcSession>(
				sessions.size());

		for (ExpiringSession session : sessions) {
			sessionMap.put(session.getId(), new JdbcSession(session));
		}

		return sessionMap;
	}

	@Scheduled(cron = "${spring.session.cleanup.cron.expression:0 * * * * *}")
	public void cleanUpExpiredSessions() {
		int deletedCount = this.transactionOperations.execute(new TransactionCallback<Integer>() {

			public Integer doInTransaction(TransactionStatus transactionStatus) {
				return JdbcOperationsSessionRepository.this.jdbcOperations.update(
						JdbcOperationsSessionRepository.this.deleteSessionsByLastAccessTimeQuery,
						System.currentTimeMillis());
			}

		});

		if (logger.isDebugEnabled()) {
			logger.debug("Cleaned up " + deletedCount + " expired sessions");
		}
	}

	private static JdbcTemplate createDefaultJdbcTemplate(DataSource dataSource) {
		JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
		jdbcTemplate.afterPropertiesSet();
		return jdbcTemplate;
	}

	private static TransactionTemplate createTransactionTemplate(
			PlatformTransactionManager transactionManager) {
		TransactionTemplate transactionTemplate = new TransactionTemplate(
				transactionManager);
		transactionTemplate.setPropagationBehavior(
				TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		transactionTemplate.afterPropertiesSet();
		return transactionTemplate;
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
		this.listSessionsByPrincipalNameQuery =
				getQuery(LIST_SESSIONS_BY_PRINCIPAL_NAME_QUERY);
		this.deleteSessionsByLastAccessTimeQuery =
				getQuery(DELETE_SESSIONS_BY_LAST_ACCESS_TIME_QUERY);
	}

	private void serialize(PreparedStatement ps, int paramIndex, Object attributeValue)
			throws SQLException {
		this.lobHandler.getLobCreator().setBlobAsBytes(ps, paramIndex,
				(byte[]) this.conversionService.convert(attributeValue,
						TypeDescriptor.valueOf(Object.class),
						TypeDescriptor.valueOf(byte[].class)));
	}

	private Object deserialize(ResultSet rs, String columnName)
			throws SQLException {
		return this.conversionService.convert(
				this.lobHandler.getBlobAsBytes(rs, columnName),
				TypeDescriptor.valueOf(byte[].class),
				TypeDescriptor.valueOf(Object.class));
	}

	/**
	 * The {@link ExpiringSession} to use for {@link JdbcOperationsSessionRepository}.
	 *
	 * @author Vedran Pavic
	 */
	final class JdbcSession implements ExpiringSession {

		private final ExpiringSession delegate;

		private boolean isNew;

		private boolean changed;

		private Map<String, Object> delta = new HashMap<String, Object>();

		JdbcSession() {
			this.delegate = new MapSession();
			this.isNew = true;
		}

		JdbcSession(ExpiringSession delegate) {
			Assert.notNull("ExpiringSession cannot be null");
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

		public <T> T getAttribute(String attributeName) {
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

		public long getCreationTime() {
			return this.delegate.getCreationTime();
		}

		public void setLastAccessedTime(long lastAccessedTime) {
			this.delegate.setLastAccessedTime(lastAccessedTime);
			this.changed = true;
		}

		public long getLastAccessedTime() {
			return this.delegate.getLastAccessedTime();
		}

		public void setMaxInactiveIntervalInSeconds(int interval) {
			this.delegate.setMaxInactiveIntervalInSeconds(interval);
			this.changed = true;
		}

		public int getMaxInactiveIntervalInSeconds() {
			return this.delegate.getMaxInactiveIntervalInSeconds();
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

	private class ExpiringSessionResultSetExtractor
			implements ResultSetExtractor<List<ExpiringSession>> {

		public List<ExpiringSession> extractData(ResultSet rs) throws SQLException, DataAccessException {
			List<ExpiringSession> sessions = new ArrayList<ExpiringSession>();
			while (rs.next()) {
				String id = rs.getString("SESSION_ID");
				MapSession session;
				if (sessions.size() > 0 && getLast(sessions).getId().equals(id)) {
					session = (MapSession) getLast(sessions);
				}
				else {
					session = new MapSession(id);
					session.setCreationTime(rs.getLong("CREATION_TIME"));
					session.setLastAccessedTime(rs.getLong("LAST_ACCESS_TIME"));
					session.setMaxInactiveIntervalInSeconds(rs.getInt("MAX_INACTIVE_INTERVAL"));
				}
				String attributeName = rs.getString("ATTRIBUTE_NAME");
				if (attributeName != null) {
					session.setAttribute(attributeName, deserialize(rs, "ATTRIBUTE_BYTES"));
				}
				sessions.add(session);
			}
			return sessions;
		}

		private ExpiringSession getLast(List<ExpiringSession> sessions) {
			return sessions.get(sessions.size() - 1);
		}

	}

}
