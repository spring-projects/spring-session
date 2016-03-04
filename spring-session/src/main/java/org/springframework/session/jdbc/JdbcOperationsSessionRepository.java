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
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
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
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementSetter;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.lob.DefaultLobHandler;
import org.springframework.jdbc.support.lob.LobHandler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.session.ExpiringSession;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.MapSession;
import org.springframework.session.Session;
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
 * JdbcOperationsSessionRepository sessionRepository = new JdbcOperationsSessionRepository(jdbcTemplate);
 * </pre>
 *
 * For additional information on how to create and configure a JdbcTemplate, refer to the
 * <a href="http://docs.spring.io/spring/docs/current/spring-framework-reference/html/">
 * Spring Framework Reference Documentation</a>.
 * <p>
 * By default, this implementation uses <code>SPRING_SESSION</code> table to store
 * sessions. Note that the table name can be customized using the
 * {@link #setTableName(String)} method.
 *
 * Depending on your database, the table definition can be described as below:
 *
 * <pre class="code">
 * CREATE TABLE SPRING_SESSION (
 *   SESSION_ID CHAR(36),
 *   LAST_ACCESS_TIME BIGINT NOT NULL,
 *   PRINCIPAL_NAME VARCHAR(100),
 *   SESSION_BYTES BLOB,
 *   CONSTRAINT SPRING_SESSION_PK PRIMARY KEY (SESSION_ID)
 * );
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
public class JdbcOperationsSessionRepository
		implements FindByIndexNameSessionRepository<JdbcOperationsSessionRepository.JdbcSession> {

	private static final String DEFAULT_TABLE_NAME = "SPRING_SESSION";

	private static final String CREATE_SESSION_QUERY =
			"INSERT INTO %TABLE_NAME%(SESSION_ID, LAST_ACCESS_TIME, PRINCIPAL_NAME, SESSION_BYTES) VALUES (?, ?, ?, ?)";

	private static final String GET_SESSION_QUERY =
			"SELECT SESSION_BYTES FROM %TABLE_NAME% WHERE SESSION_ID = ?";

	private static final String UPDATE_SESSION_QUERY =
			"UPDATE %TABLE_NAME% SET LAST_ACCESS_TIME = ?, PRINCIPAL_NAME = ?, SESSION_BYTES = ? WHERE SESSION_ID = ?";

	private static final String UPDATE_SESSION_LAST_ACCESS_TIME_QUERY =
			"UPDATE %TABLE_NAME% SET LAST_ACCESS_TIME = ? WHERE SESSION_ID = ?";

	private static final String DELETE_SESSION_QUERY =
			"DELETE FROM %TABLE_NAME% WHERE SESSION_ID = ?";

	private static final String LIST_SESSIONS_BY_PRINCIPAL_NAME_QUERY =
			"SELECT SESSION_BYTES FROM %TABLE_NAME% WHERE PRINCIPAL_NAME = ?";

	private static final String DELETE_SESSIONS_BY_LAST_ACCESS_TIME_QUERY =
			"DELETE FROM %TABLE_NAME% WHERE LAST_ACCESS_TIME < ?";

	private static final Log logger = LogFactory.getLog(JdbcOperationsSessionRepository.class);

	private static final PrincipalNameResolver PRINCIPAL_NAME_RESOLVER = new PrincipalNameResolver();

	private final JdbcOperations jdbcOperations;

	private final RowMapper<ExpiringSession> mapper = new ExpiringSessionMapper();

	/**
	 * The name of database table used by Spring Session to store sessions.
	 */
	private String tableName = DEFAULT_TABLE_NAME;

	/**
	 * If non-null, this value is used to override the default value for
	 * {@link JdbcSession#setMaxInactiveIntervalInSeconds(int)}.
	 */
	private Integer defaultMaxInactiveInterval;

	private ConversionService conversionService;

	private LobHandler lobHandler = new DefaultLobHandler();

	/**
	 * Create a new {@link JdbcOperationsSessionRepository} instance which uses the
	 * default ${JdbcOperations} to manage sessions.
	 * @param dataSource the {@link DataSource} to use
	 */
	public JdbcOperationsSessionRepository(DataSource dataSource) {
		this(createDefaultTemplate(dataSource));
	}

	/**
	 * Create a new {@link JdbcOperationsSessionRepository} instance which uses the
	 * provided ${JdbcOperations} to manage sessions.
	 * @param jdbcOperations the {@link JdbcOperations} to use
	 */
	public JdbcOperationsSessionRepository(JdbcOperations jdbcOperations) {
		Assert.notNull(jdbcOperations, "JdbcOperations must not be null");
		this.jdbcOperations = jdbcOperations;

		this.conversionService = createDefaultConversionService();
	}

	/**
	 * Set the name of database table used to store sessions.
	 * @param tableName the database table name
	 */
	public void setTableName(String tableName) {
		Assert.hasText(tableName, "Table name must not be empty");
		this.tableName = tableName.trim();
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
			this.jdbcOperations.update(getQuery(CREATE_SESSION_QUERY), new PreparedStatementSetter() {

				public void setValues(PreparedStatement ps) throws SQLException {
					ps.setString(1, session.getId());
					ps.setLong(2, session.getLastAccessedTime());
					ps.setString(3, session.getPrincipalName());
					JdbcOperationsSessionRepository.this.lobHandler.getLobCreator()
							.setBlobAsBytes(ps, 4, serialize(session.delegate));
				}

			});
		}
		else {
			if (session.isAttributesChanged()) {
				this.jdbcOperations.update(getQuery(UPDATE_SESSION_QUERY), new PreparedStatementSetter() {

					public void setValues(PreparedStatement ps) throws SQLException {
						ps.setLong(1, session.getLastAccessedTime());
						ps.setString(2, session.getPrincipalName());
						JdbcOperationsSessionRepository.this.lobHandler.getLobCreator()
								.setBlobAsBytes(ps, 3, serialize(session.delegate));
						ps.setString(4, session.getId());
					}

				});
			}
			else if (session.isLastAccessTimeChanged()) {
				this.jdbcOperations.update(getQuery(UPDATE_SESSION_LAST_ACCESS_TIME_QUERY), new PreparedStatementSetter() {

					public void setValues(PreparedStatement ps) throws SQLException {
						ps.setLong(1, session.getLastAccessedTime());
						ps.setString(2, session.getId());
					}

				});
			}
			else {
				return;
			}
		}
		session.clearChangeFlags();
	}

	public JdbcSession getSession(String id) {
		ExpiringSession session = null;
		try {
			session = this.jdbcOperations.queryForObject(getQuery(GET_SESSION_QUERY),
					new Object[] { id }, this.mapper);
		}
		catch (EmptyResultDataAccessException ignored) {
		}

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

	public void delete(String id) {
		this.jdbcOperations.update(getQuery(DELETE_SESSION_QUERY), id);
	}

	public Map<String, JdbcSession> findByIndexNameAndIndexValue(String indexName, String indexValue) {
		if (!PRINCIPAL_NAME_INDEX_NAME.equals(indexName)) {
			return Collections.emptyMap();
		}

		List<ExpiringSession> sessions = this.jdbcOperations.query(
				getQuery(LIST_SESSIONS_BY_PRINCIPAL_NAME_QUERY), new Object[] { indexValue }, this.mapper);

		Map<String, JdbcSession> sessionMap = new HashMap<String, JdbcSession>(sessions.size());

		for (ExpiringSession session : sessions) {
			sessionMap.put(session.getId(), new JdbcSession(session));
		}

		return sessionMap;
	}

	@Scheduled(cron = "0 * * * * *")
	public void cleanUpExpiredSessions() {
		long now = System.currentTimeMillis();
		long roundedNow = roundDownMinute(now);

		if (logger.isDebugEnabled()) {
			logger.debug("Cleaning up sessions expiring at " + new Date(roundedNow));
		}

		int deletedCount = this.jdbcOperations.update(
				getQuery(DELETE_SESSIONS_BY_LAST_ACCESS_TIME_QUERY), roundedNow);

		if (logger.isDebugEnabled()) {
			logger.debug("Cleaned up " + deletedCount + " expired sessions");
		}
	}

	private static JdbcTemplate createDefaultTemplate(DataSource dataSource) {
		JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
		jdbcTemplate.afterPropertiesSet();
		return jdbcTemplate;
	}

	protected String getQuery(String base) {
		return StringUtils.replace(base, "%TABLE_NAME%", this.tableName);
	}

	private byte[] serialize(ExpiringSession session) {
		return (byte[]) this.conversionService.convert(session, TypeDescriptor.valueOf(ExpiringSession.class), TypeDescriptor.valueOf(byte[].class));
	}

	private static long roundDownMinute(long timeInMs) {
		Calendar date = Calendar.getInstance();
		date.setTimeInMillis(timeInMs);
		date.clear(Calendar.SECOND);
		date.clear(Calendar.MILLISECOND);
		return date.getTimeInMillis();
	}

	private static GenericConversionService createDefaultConversionService() {
		GenericConversionService converter = new GenericConversionService();
		converter.addConverter(ExpiringSession.class, byte[].class, new SerializingConverter());
		converter.addConverter(byte[].class, ExpiringSession.class, new DeserializingConverter());
		return converter;
	}


	final class JdbcSession implements ExpiringSession {

		private final ExpiringSession delegate;

		private boolean isNew;

		private boolean lastAccessTimeChanged;

		private boolean attributesChanged;

		public JdbcSession() {
			this.delegate = new MapSession();
			this.isNew = true;
		}

		public JdbcSession(ExpiringSession delegate) {
			Assert.notNull("ExpiringSession cannot be null");
			this.delegate = delegate;
		}

		public boolean isNew() {
			return this.isNew;
		}

		public boolean isLastAccessTimeChanged() {
			return this.lastAccessTimeChanged;
		}

		public boolean isAttributesChanged() {
			return this.attributesChanged;
		}

		public void clearChangeFlags() {
			this.isNew = false;
			this.lastAccessTimeChanged = false;
			this.attributesChanged = false;
		}

		public String getPrincipalName() {
			return PRINCIPAL_NAME_RESOLVER.resolvePrincipal(this);
		}

		public long getCreationTime() {
			return this.delegate.getCreationTime();
		}

		public void setLastAccessedTime(long lastAccessedTime) {
			this.delegate.setLastAccessedTime(lastAccessedTime);
			this.lastAccessTimeChanged = true;
		}

		public long getLastAccessedTime() {
			return this.delegate.getLastAccessedTime();
		}

		public void setMaxInactiveIntervalInSeconds(int interval) {
			this.delegate.setMaxInactiveIntervalInSeconds(interval);
			this.attributesChanged = true;
		}

		public int getMaxInactiveIntervalInSeconds() {
			return this.delegate.getMaxInactiveIntervalInSeconds();
		}

		public boolean isExpired() {
			return this.delegate.isExpired();
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
			this.attributesChanged = true;
		}

		public void removeAttribute(String attributeName) {
			this.delegate.removeAttribute(attributeName);
			this.attributesChanged = true;
		}

	}

	static class PrincipalNameResolver {

		private static final String SPRING_SECURITY_CONTEXT = "SPRING_SECURITY_CONTEXT";

		private SpelExpressionParser parser = new SpelExpressionParser();

		public String resolvePrincipal(Session session) {
			String principalName = session.getAttribute(PRINCIPAL_NAME_INDEX_NAME);
			if (principalName != null) {
				return principalName;
			}
			Object authentication = session.getAttribute(SPRING_SECURITY_CONTEXT);
			if (authentication != null) {
				Expression expression = this.parser.parseExpression("authentication?.name");
				return expression.getValue(authentication, String.class);
			}
			return null;
		}

	}

	private class ExpiringSessionMapper implements RowMapper<ExpiringSession> {

		public ExpiringSession mapRow(ResultSet rs, int rowNum) throws SQLException {
			return (ExpiringSession) JdbcOperationsSessionRepository.this.conversionService.convert(
					JdbcOperationsSessionRepository.this.lobHandler.getBlobAsBytes(rs, "SESSION_BYTES"), TypeDescriptor.valueOf(byte[].class), TypeDescriptor.valueOf(ExpiringSession.class));
		}

	}

}
