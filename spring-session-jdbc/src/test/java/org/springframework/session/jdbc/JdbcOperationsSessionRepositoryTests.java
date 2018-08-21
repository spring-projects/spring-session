/*
 * Copyright 2014-2018 the original author or authors.
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

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.PreparedStatementSetter;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.MapSession;
import org.springframework.session.Session;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.endsWith;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

/**
 * Tests for {@link JdbcOperationsSessionRepository}.
 *
 * @author Vedran Pavic
 * @author Craig Andrews
 * @since 1.2.0
 */
public class JdbcOperationsSessionRepositoryTests {

	private static final String SPRING_SECURITY_CONTEXT = "SPRING_SECURITY_CONTEXT";

	private JdbcOperations jdbcOperations = mock(JdbcOperations.class);

	private PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);

	private JdbcOperationsSessionRepository repository;

	@Before
	public void setUp() {
		this.repository = new JdbcOperationsSessionRepository(this.jdbcOperations, this.transactionManager);
	}

	@Test
	public void constructorNullJdbcOperations() {
		assertThatThrownBy(
				() -> new JdbcOperationsSessionRepository(null, this.transactionManager))
						.isInstanceOf(IllegalArgumentException.class)
						.hasMessage("JdbcOperations must not be null");
	}

	@Test
	public void constructorNullTransactionManager() {
		assertThatThrownBy(
				() -> new JdbcOperationsSessionRepository(this.jdbcOperations, null))
						.isInstanceOf(IllegalArgumentException.class)
						.hasMessage("TransactionManager must not be null");
	}

	@Test
	public void setTableNameNull() {
		assertThatThrownBy(() -> this.repository.setTableName(null))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("Table name must not be empty");
	}

	@Test
	public void setTableNameEmpty() {
		assertThatThrownBy(() -> this.repository.setTableName(" "))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("Table name must not be empty");
	}

	@Test
	public void setCreateSessionQueryNull() {
		assertThatThrownBy(() -> this.repository.setCreateSessionQuery(null))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("Query must not be empty");
	}

	@Test
	public void setCreateSessionQueryEmpty() {
		assertThatThrownBy(() -> this.repository.setCreateSessionQuery(" "))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("Query must not be empty");
	}

	@Test
	public void setCreateSessionAttributeQueryNull() {
		assertThatThrownBy(() -> this.repository.setCreateSessionAttributeQuery(null))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("Query must not be empty");
	}

	@Test
	public void setCreateSessionAttributeQueryEmpty() {
		assertThatThrownBy(() -> this.repository.setCreateSessionAttributeQuery(" "))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("Query must not be empty");
	}

	@Test
	public void setGetSessionQueryNull() {
		assertThatThrownBy(() -> this.repository.setGetSessionQuery(null))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("Query must not be empty");
	}

	@Test
	public void setGetSessionQueryEmpty() {
		assertThatThrownBy(() -> this.repository.setGetSessionQuery(" "))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("Query must not be empty");
	}

	@Test
	public void setUpdateSessionQueryNull() {
		assertThatThrownBy(() -> this.repository.setUpdateSessionQuery(null))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("Query must not be empty");
	}

	@Test
	public void setUpdateSessionQueryEmpty() {
		assertThatThrownBy(() -> this.repository.setUpdateSessionQuery(" "))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("Query must not be empty");
	}

	@Test
	public void setUpdateSessionAttributeQueryNull() {
		assertThatThrownBy(() -> this.repository.setUpdateSessionAttributeQuery(null))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("Query must not be empty");
	}

	@Test
	public void setUpdateSessionAttributeQueryEmpty() {
		assertThatThrownBy(() -> this.repository.setUpdateSessionAttributeQuery(" "))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("Query must not be empty");
	}

	@Test
	public void setDeleteSessionAttributeQueryNull() {
		assertThatThrownBy(() -> this.repository.setDeleteSessionAttributeQuery(null))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("Query must not be empty");
	}

	@Test
	public void setDeleteSessionAttributeQueryEmpty() {
		assertThatThrownBy(() -> this.repository.setDeleteSessionAttributeQuery(" "))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("Query must not be empty");
	}

	@Test
	public void setDeleteSessionQueryNull() {
		assertThatThrownBy(() -> this.repository.setDeleteSessionQuery(null))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("Query must not be empty");
	}

	@Test
	public void setDeleteSessionQueryEmpty() {
		assertThatThrownBy(() -> this.repository.setDeleteSessionQuery(" "))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("Query must not be empty");
	}

	@Test
	public void setListSessionsByPrincipalNameQueryNull() {
		assertThatThrownBy(
				() -> this.repository.setListSessionsByPrincipalNameQuery(null))
						.isInstanceOf(IllegalArgumentException.class)
						.hasMessage("Query must not be empty");
	}

	@Test
	public void setListSessionsByPrincipalNameQueryEmpty() {
		assertThatThrownBy(() -> this.repository.setListSessionsByPrincipalNameQuery(" "))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("Query must not be empty");
	}

	@Test
	public void setDeleteSessionsByLastAccessTimeQueryNull() {
		assertThatThrownBy(() -> this.repository.setDeleteSessionsByExpiryTimeQuery(null))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("Query must not be empty");
	}

	@Test
	public void setDeleteSessionsByLastAccessTimeQueryEmpty() {
		assertThatThrownBy(() -> this.repository.setDeleteSessionsByExpiryTimeQuery(" "))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("Query must not be empty");
	}

	@Test
	public void setLobHandlerNull() {
		assertThatThrownBy(() -> this.repository.setLobHandler(null))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("LobHandler must not be null");
	}

	@Test
	public void setConversionServiceNull() {
		assertThatThrownBy(() -> this.repository.setConversionService(null))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("conversionService must not be null");
	}

	@Test
	public void createSessionDefaultMaxInactiveInterval() {
		JdbcOperationsSessionRepository.JdbcSession session = this.repository
				.createSession();

		assertThat(session.isNew()).isTrue();
		assertThat(session.getMaxInactiveInterval())
				.isEqualTo(new MapSession().getMaxInactiveInterval());
		verifyZeroInteractions(this.jdbcOperations);
	}

	@Test
	public void createSessionCustomMaxInactiveInterval() {
		int interval = 1;
		this.repository.setDefaultMaxInactiveInterval(interval);

		JdbcOperationsSessionRepository.JdbcSession session = this.repository
				.createSession();

		assertThat(session.isNew()).isTrue();
		assertThat(session.getMaxInactiveInterval()).isEqualTo(Duration.ofSeconds(interval));
		verifyZeroInteractions(this.jdbcOperations);
	}

	@Test
	public void saveNewWithoutAttributes() {
		JdbcOperationsSessionRepository.JdbcSession session = this.repository
				.createSession();

		this.repository.save(session);

		assertThat(session.isNew()).isFalse();
		assertPropagationRequiresNew();
		verify(this.jdbcOperations, times(1)).update(startsWith("INSERT"),
				isA(PreparedStatementSetter.class));
		verifyZeroInteractions(this.jdbcOperations);
	}

	@Test
	public void saveNewWithSingleAttribute() {
		JdbcOperationsSessionRepository.JdbcSession session = this.repository
				.createSession();
		session.setAttribute("testName", "testValue");

		this.repository.save(session);

		assertThat(session.isNew()).isFalse();
		assertPropagationRequiresNew();
		verify(this.jdbcOperations, times(1)).update(
				startsWith("INSERT INTO SPRING_SESSION("),
				isA(PreparedStatementSetter.class));
		verify(this.jdbcOperations, times(1)).update(
				startsWith("INSERT INTO SPRING_SESSION_ATTRIBUTES("),
				isA(PreparedStatementSetter.class));
		verifyZeroInteractions(this.jdbcOperations);
	}

	@Test
	public void saveNewWithMultipleAttributes() {
		JdbcOperationsSessionRepository.JdbcSession session = this.repository
				.createSession();
		session.setAttribute("testName1", "testValue1");
		session.setAttribute("testName2", "testValue2");

		this.repository.save(session);

		assertThat(session.isNew()).isFalse();
		assertPropagationRequiresNew();
		verify(this.jdbcOperations, times(1)).update(
				startsWith("INSERT INTO SPRING_SESSION("),
				isA(PreparedStatementSetter.class));
		verify(this.jdbcOperations, times(1)).batchUpdate(
				startsWith("INSERT INTO SPRING_SESSION_ATTRIBUTES("),
				isA(BatchPreparedStatementSetter.class));
		verifyZeroInteractions(this.jdbcOperations);
	}

	@Test
	public void saveUpdatedAddSingleAttribute() {
		JdbcOperationsSessionRepository.JdbcSession session = this.repository.new JdbcSession("primaryKey",
				new MapSession());
		session.setAttribute("testName", "testValue");

		this.repository.save(session);

		assertThat(session.isNew()).isFalse();
		assertPropagationRequiresNew();
		verify(this.jdbcOperations, times(1)).update(
				startsWith("INSERT INTO SPRING_SESSION_ATTRIBUTES("),
				isA(PreparedStatementSetter.class));
		verifyZeroInteractions(this.jdbcOperations);
	}

	@Test
	public void saveUpdatedAddMultipleAttributes() {
		JdbcOperationsSessionRepository.JdbcSession session = this.repository.new JdbcSession("primaryKey",
				new MapSession());
		session.setAttribute("testName1", "testValue1");
		session.setAttribute("testName2", "testValue2");

		this.repository.save(session);

		assertThat(session.isNew()).isFalse();
		assertPropagationRequiresNew();
		verify(this.jdbcOperations, times(1)).batchUpdate(
				startsWith("INSERT INTO SPRING_SESSION_ATTRIBUTES("),
				isA(BatchPreparedStatementSetter.class));
		verifyZeroInteractions(this.jdbcOperations);
	}

	@Test
	public void saveUpdatedModifySingleAttribute() {
		JdbcOperationsSessionRepository.JdbcSession session = this.repository.new JdbcSession("primaryKey",
				new MapSession());
		session.setAttribute("testName", "testValue");
		session.clearChangeFlags();
		session.setAttribute("testName", "testValue");

		this.repository.save(session);

		assertThat(session.isNew()).isFalse();
		assertPropagationRequiresNew();
		verify(this.jdbcOperations, times(1)).update(
				startsWith("UPDATE SPRING_SESSION_ATTRIBUTES SET"),
				isA(PreparedStatementSetter.class));
		verifyZeroInteractions(this.jdbcOperations);
	}

	@Test
	public void saveUpdatedModifyMultipleAttributes() {
		JdbcOperationsSessionRepository.JdbcSession session = this.repository.new JdbcSession("primaryKey",
				new MapSession());
		session.setAttribute("testName1", "testValue1");
		session.setAttribute("testName2", "testValue2");
		session.clearChangeFlags();
		session.setAttribute("testName1", "testValue1");
		session.setAttribute("testName2", "testValue2");

		this.repository.save(session);

		assertThat(session.isNew()).isFalse();
		assertPropagationRequiresNew();
		verify(this.jdbcOperations, times(1)).batchUpdate(
				startsWith("UPDATE SPRING_SESSION_ATTRIBUTES SET"),
				isA(BatchPreparedStatementSetter.class));
		verifyZeroInteractions(this.jdbcOperations);
	}

	@Test
	public void saveUpdatedRemoveSingleAttribute() {
		JdbcOperationsSessionRepository.JdbcSession session = this.repository.new JdbcSession("primaryKey",
				new MapSession());
		session.setAttribute("testName", "testValue");
		session.clearChangeFlags();
		session.removeAttribute("testName");

		this.repository.save(session);

		assertThat(session.isNew()).isFalse();
		assertPropagationRequiresNew();
		verify(this.jdbcOperations, times(1)).update(
				startsWith("DELETE FROM SPRING_SESSION_ATTRIBUTES WHERE"),
				isA(PreparedStatementSetter.class));
		verifyZeroInteractions(this.jdbcOperations);
	}

	@Test
	public void saveUpdatedRemoveNonExistingAttribute() {
		JdbcOperationsSessionRepository.JdbcSession session = this.repository.new JdbcSession("primaryKey",
				new MapSession());
		session.removeAttribute("testName");

		this.repository.save(session);

		assertThat(session.isNew()).isFalse();
		assertPropagationRequiresNew();
		verifyZeroInteractions(this.jdbcOperations);
	}

	@Test
	public void saveUpdatedRemoveMultipleAttributes() {
		JdbcOperationsSessionRepository.JdbcSession session = this.repository.new JdbcSession("primaryKey",
				new MapSession());
		session.setAttribute("testName1", "testValue1");
		session.setAttribute("testName2", "testValue2");
		session.clearChangeFlags();
		session.removeAttribute("testName1");
		session.removeAttribute("testName2");

		this.repository.save(session);

		assertThat(session.isNew()).isFalse();
		assertPropagationRequiresNew();
		verify(this.jdbcOperations, times(1)).batchUpdate(
				startsWith("DELETE FROM SPRING_SESSION_ATTRIBUTES WHERE"),
				isA(BatchPreparedStatementSetter.class));
		verifyZeroInteractions(this.jdbcOperations);
	}

	@Test // gh-1070
	public void saveUpdatedAddAndModifyAttribute() {
		JdbcOperationsSessionRepository.JdbcSession session = this.repository.new JdbcSession("primaryKey",
				new MapSession());
		session.setAttribute("testName", "testValue1");
		session.setAttribute("testName", "testValue2");

		this.repository.save(session);

		assertThat(session.isNew()).isFalse();
		assertPropagationRequiresNew();
		verify(this.jdbcOperations).update(
				startsWith("INSERT INTO SPRING_SESSION_ATTRIBUTES("),
				isA(PreparedStatementSetter.class));
		verifyZeroInteractions(this.jdbcOperations);
	}

	@Test // gh-1070
	public void saveUpdatedAddAndRemoveAttribute() {
		JdbcOperationsSessionRepository.JdbcSession session = this.repository.new JdbcSession("primaryKey",
				new MapSession());
		session.setAttribute("testName", "testValue");
		session.removeAttribute("testName");

		this.repository.save(session);

		assertThat(session.isNew()).isFalse();
		assertPropagationRequiresNew();
		verifyZeroInteractions(this.jdbcOperations);
	}

	@Test // gh-1070
	public void saveUpdatedModifyAndRemoveAttribute() {
		JdbcOperationsSessionRepository.JdbcSession session = this.repository.new JdbcSession("primaryKey",
				new MapSession());
		session.setAttribute("testName", "testValue1");
		session.clearChangeFlags();
		session.setAttribute("testName", "testValue2");
		session.removeAttribute("testName");

		this.repository.save(session);

		assertThat(session.isNew()).isFalse();
		assertPropagationRequiresNew();
		verify(this.jdbcOperations).update(
				startsWith("DELETE FROM SPRING_SESSION_ATTRIBUTES WHERE"),
				isA(PreparedStatementSetter.class));
		verifyZeroInteractions(this.jdbcOperations);
	}

	@Test // gh-1070
	public void saveUpdatedRemoveAndAddAttribute() {
		JdbcOperationsSessionRepository.JdbcSession session = this.repository.new JdbcSession("primaryKey",
				new MapSession());
		session.setAttribute("testName", "testValue1");
		session.clearChangeFlags();
		session.removeAttribute("testName");
		session.setAttribute("testName", "testValue2");

		this.repository.save(session);

		assertThat(session.isNew()).isFalse();
		assertPropagationRequiresNew();
		verify(this.jdbcOperations).update(
				startsWith("UPDATE SPRING_SESSION_ATTRIBUTES SET"),
				isA(PreparedStatementSetter.class));
		verifyZeroInteractions(this.jdbcOperations);
	}

	@Test
	public void saveUpdatedLastAccessedTime() {
		JdbcOperationsSessionRepository.JdbcSession session = this.repository.new JdbcSession("primaryKey",
				new MapSession());
		session.setLastAccessedTime(Instant.now());

		this.repository.save(session);

		assertThat(session.isNew()).isFalse();
		assertPropagationRequiresNew();
		verify(this.jdbcOperations, times(1)).update(
				startsWith("UPDATE SPRING_SESSION SET"),
				isA(PreparedStatementSetter.class));
		verifyZeroInteractions(this.jdbcOperations);
	}

	@Test
	public void saveUnchanged() {
		JdbcOperationsSessionRepository.JdbcSession session = this.repository.new JdbcSession("primaryKey",
				new MapSession());

		this.repository.save(session);

		assertThat(session.isNew()).isFalse();
		verifyZeroInteractions(this.jdbcOperations);
	}

	@Test
	@SuppressWarnings("unchecked")
	public void getSessionNotFound() {
		String sessionId = "testSessionId";
		given(this.jdbcOperations.query(isA(String.class),
				isA(PreparedStatementSetter.class), isA(ResultSetExtractor.class)))
				.willReturn(Collections.emptyList());

		JdbcOperationsSessionRepository.JdbcSession session = this.repository
				.findById(sessionId);

		assertThat(session).isNull();
		assertPropagationRequiresNew();
		verify(this.jdbcOperations, times(1)).query(isA(String.class),
				isA(PreparedStatementSetter.class), isA(ResultSetExtractor.class));
	}

	@Test
	@SuppressWarnings("unchecked")
	public void getSessionExpired() {
		Session expired = this.repository.new JdbcSession();
		expired.setLastAccessedTime(Instant.now().minusSeconds(
				MapSession.DEFAULT_MAX_INACTIVE_INTERVAL_SECONDS + 1));
		given(this.jdbcOperations.query(isA(String.class),
				isA(PreparedStatementSetter.class), isA(ResultSetExtractor.class)))
				.willReturn(Collections.singletonList(expired));

		JdbcOperationsSessionRepository.JdbcSession session = this.repository
				.findById(expired.getId());

		assertThat(session).isNull();
		assertPropagationRequiresNew();
		verify(this.jdbcOperations, times(1)).query(isA(String.class),
				isA(PreparedStatementSetter.class), isA(ResultSetExtractor.class));
		verify(this.jdbcOperations, times(1)).update(startsWith("DELETE"),
				eq(expired.getId()));
	}

	@Test
	@SuppressWarnings("unchecked")
	public void getSessionFound() {
		Session saved = this.repository.new JdbcSession("primaryKey", new MapSession());
		saved.setAttribute("savedName", "savedValue");
		given(this.jdbcOperations.query(isA(String.class),
				isA(PreparedStatementSetter.class), isA(ResultSetExtractor.class)))
				.willReturn(Collections.singletonList(saved));

		JdbcOperationsSessionRepository.JdbcSession session = this.repository
				.findById(saved.getId());

		assertThat(session.getId()).isEqualTo(saved.getId());
		assertThat(session.isNew()).isFalse();
		assertThat(session.<String>getAttribute("savedName")).isEqualTo("savedValue");
		assertPropagationRequiresNew();
		verify(this.jdbcOperations, times(1)).query(isA(String.class),
				isA(PreparedStatementSetter.class), isA(ResultSetExtractor.class));
	}

	@Test
	public void delete() {
		String sessionId = "testSessionId";

		this.repository.deleteById(sessionId);

		assertPropagationRequiresNew();
		verify(this.jdbcOperations, times(1)).update(startsWith("DELETE"), eq(sessionId));
	}

	@Test
	public void findByIndexNameAndIndexValueUnknownIndexName() {
		String indexValue = "testIndexValue";

		Map<String, JdbcOperationsSessionRepository.JdbcSession> sessions = this.repository
				.findByIndexNameAndIndexValue("testIndexName", indexValue);

		assertThat(sessions).isEmpty();
		verifyZeroInteractions(this.jdbcOperations);
	}

	@Test
	@SuppressWarnings("unchecked")
	public void findByIndexNameAndIndexValuePrincipalIndexNameNotFound() {
		String principal = "username";
		given(this.jdbcOperations.query(isA(String.class),
				isA(PreparedStatementSetter.class), isA(ResultSetExtractor.class)))
				.willReturn(Collections.emptyList());

		Map<String, JdbcOperationsSessionRepository.JdbcSession> sessions = this.repository
				.findByIndexNameAndIndexValue(
						FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME,
						principal);

		assertThat(sessions).isEmpty();
		assertPropagationRequiresNew();
		verify(this.jdbcOperations, times(1)).query(isA(String.class),
				isA(PreparedStatementSetter.class), isA(ResultSetExtractor.class));
	}

	@Test
	@SuppressWarnings("unchecked")
	public void findByIndexNameAndIndexValuePrincipalIndexNameFound() {
		String principal = "username";
		Authentication authentication = new UsernamePasswordAuthenticationToken(principal,
				"notused", AuthorityUtils.createAuthorityList("ROLE_USER"));
		List<Session> saved = new ArrayList<>(2);
		Session saved1 = this.repository.new JdbcSession();
		saved1.setAttribute(SPRING_SECURITY_CONTEXT, authentication);
		saved.add(saved1);
		Session saved2 = this.repository.new JdbcSession();
		saved2.setAttribute(SPRING_SECURITY_CONTEXT, authentication);
		saved.add(saved2);
		given(this.jdbcOperations.query(isA(String.class),
				isA(PreparedStatementSetter.class), isA(ResultSetExtractor.class)))
				.willReturn(saved);

		Map<String, JdbcOperationsSessionRepository.JdbcSession> sessions = this.repository
				.findByIndexNameAndIndexValue(
						FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME,
						principal);

		assertThat(sessions).hasSize(2);
		assertPropagationRequiresNew();
		verify(this.jdbcOperations, times(1)).query(isA(String.class),
				isA(PreparedStatementSetter.class), isA(ResultSetExtractor.class));
	}

	@Test
	public void cleanupExpiredSessions() {
		this.repository.cleanUpExpiredSessions();

		assertPropagationRequiresNew();
		verify(this.jdbcOperations, times(1)).update(startsWith("DELETE"), anyLong());
	}

	@Test // gh-1120
	public void getAttributeNamesAndRemove() {
		JdbcOperationsSessionRepository.JdbcSession session = this.repository.createSession();
		session.setAttribute("attribute1", "value1");
		session.setAttribute("attribute2", "value2");

		for (String attributeName : session.getAttributeNames()) {
			session.removeAttribute(attributeName);
		}

		assertThat(session.getAttributeNames()).isEmpty();
	}

	@Test
	public void saveNewWithoutTransaction() {
		this.repository = new JdbcOperationsSessionRepository(this.jdbcOperations);
		JdbcOperationsSessionRepository.JdbcSession session = this.repository
				.createSession();

		this.repository.save(session);

		verify(this.jdbcOperations, times(1)).update(
				startsWith("INSERT INTO SPRING_SESSION"),
				isA(PreparedStatementSetter.class));
		verifyZeroInteractions(this.jdbcOperations);
		verifyZeroInteractions(this.transactionManager);
	}

	@Test
	public void saveUpdatedWithoutTransaction() {
		this.repository = new JdbcOperationsSessionRepository(this.jdbcOperations);
		JdbcOperationsSessionRepository.JdbcSession session = this.repository.new JdbcSession(
				"primaryKey", new MapSession());
		session.setLastAccessedTime(Instant.now());

		this.repository.save(session);

		verify(this.jdbcOperations, times(1)).update(startsWith("UPDATE SPRING_SESSION"),
				isA(PreparedStatementSetter.class));
		verifyZeroInteractions(this.jdbcOperations);
		verifyZeroInteractions(this.transactionManager);
	}

	@Test
	@SuppressWarnings("unchecked")
	public void findByIdWithoutTransaction() {
		given(this.jdbcOperations.query(anyString(), any(PreparedStatementSetter.class),
				any(ResultSetExtractor.class))).willReturn(Collections.emptyList());
		this.repository = new JdbcOperationsSessionRepository(this.jdbcOperations);
		this.repository.findById("testSessionId");

		verify(this.jdbcOperations, times(1)).query(endsWith("WHERE S.SESSION_ID = ?"),
				isA(PreparedStatementSetter.class), isA(ResultSetExtractor.class));
		verifyZeroInteractions(this.jdbcOperations);
		verifyZeroInteractions(this.transactionManager);
	}

	@Test
	public void deleteByIdWithoutTransaction() {
		this.repository = new JdbcOperationsSessionRepository(this.jdbcOperations);
		this.repository.deleteById("testSessionId");

		verify(this.jdbcOperations, times(1)).update(
				eq("DELETE FROM SPRING_SESSION WHERE SESSION_ID = ?"), anyString());
		verifyZeroInteractions(this.jdbcOperations);
		verifyZeroInteractions(this.transactionManager);
	}

	@Test
	@SuppressWarnings("unchecked")
	public void findByIndexNameAndIndexValueWithoutTransaction() {
		given(this.jdbcOperations.query(anyString(), any(PreparedStatementSetter.class),
				any(ResultSetExtractor.class))).willReturn(Collections.emptyList());
		this.repository = new JdbcOperationsSessionRepository(this.jdbcOperations);
		this.repository.findByIndexNameAndIndexValue(
				FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME,
				"testIndexValue");

		verify(this.jdbcOperations, times(1)).query(
				endsWith("WHERE S.PRINCIPAL_NAME = ?"),
				isA(PreparedStatementSetter.class), isA(ResultSetExtractor.class));
		verifyZeroInteractions(this.jdbcOperations);
		verifyZeroInteractions(this.transactionManager);
	}

	@Test
	public void cleanUpExpiredSessionsWithoutTransaction() {
		this.repository = new JdbcOperationsSessionRepository(this.jdbcOperations);
		this.repository.cleanUpExpiredSessions();

		verify(this.jdbcOperations, times(1)).update(
				eq("DELETE FROM SPRING_SESSION WHERE EXPIRY_TIME < ?"), anyLong());
		verifyZeroInteractions(this.jdbcOperations);
		verifyZeroInteractions(this.transactionManager);
	}

	private void assertPropagationRequiresNew() {
		ArgumentCaptor<TransactionDefinition> argument =
				ArgumentCaptor.forClass(TransactionDefinition.class);
		verify(this.transactionManager, atLeastOnce()).getTransaction(argument.capture());
		assertThat(argument.getValue().getPropagationBehavior())
				.isEqualTo(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
	}

}
