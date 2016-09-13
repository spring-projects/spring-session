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

package org.springframework.session.hazelcast;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.hazelcast.core.IMap;
import com.hazelcast.query.impl.predicates.EqualPredicate;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.MapSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.isA;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

/**
 * Tests for {@link HazelcastSessionRepository}.
 *
 * @author Vedran Pavic
 */
@RunWith(MockitoJUnitRunner.class)
public class HazelcastSessionRepositoryTests {

	private static final String SPRING_SECURITY_CONTEXT = "SPRING_SECURITY_CONTEXT";

	@Rule
	public ExpectedException thrown = ExpectedException.none();

	@Mock
	private IMap<String, MapSession> sessions;

	private HazelcastSessionRepository repository;

	@Before
	public void setUp() {
		this.repository = new HazelcastSessionRepository(this.sessions);
	}

	@Test
	public void constructorNullHazelcastInstance() {
		this.thrown.expect(IllegalArgumentException.class);
		this.thrown.expectMessage("Sessions IMap must not be null");

		new HazelcastSessionRepository(null);
	}

	@Test
	public void createSessionDefaultMaxInactiveInterval() throws Exception {
		MapSession session = this.repository.createSession();

		assertThat(session.getMaxInactiveIntervalInSeconds())
				.isEqualTo(new MapSession().getMaxInactiveIntervalInSeconds());
		verifyZeroInteractions(this.sessions);
	}

	@Test
	public void createSessionCustomMaxInactiveInterval() throws Exception {
		int interval = 1;
		this.repository.setDefaultMaxInactiveInterval(interval);

		MapSession session = this.repository.createSession();

		assertThat(session.getMaxInactiveIntervalInSeconds()).isEqualTo(interval);
		verifyZeroInteractions(this.sessions);
	}

	@Test
	public void saveNew() {
		MapSession session = this.repository.createSession();

		this.repository.save(session);

		verify(this.sessions, times(1)).put(eq(session.getId()), eq(session),
				isA(Long.class), eq(TimeUnit.SECONDS));
	}

	@Test
	public void saveUpdatedAttributes() {
		MapSession session = new MapSession();
		session.setAttribute("testName", "testValue");

		this.repository.save(session);

		verify(this.sessions, times(1)).put(eq(session.getId()), eq(session),
				isA(Long.class), eq(TimeUnit.SECONDS));
	}

	@Test
	public void saveUpdatedLastAccessedTime() {
		MapSession session = new MapSession();
		session.setLastAccessedTime(System.currentTimeMillis());

		this.repository.save(session);

		verify(this.sessions, times(1)).put(eq(session.getId()), eq(session),
				isA(Long.class), eq(TimeUnit.SECONDS));
	}

	@Test
	public void saveUnchanged() {
		MapSession session = new MapSession();

		this.repository.save(session);

		verify(this.sessions, times(1)).put(eq(session.getId()), eq(session),
				isA(Long.class), eq(TimeUnit.SECONDS));
		// TODO - once save optimization is implemented, should be replaced with:
		//verifyZeroInteractions(this.sessions);
	}

	@Test
	public void getSessionNotFound() {
		String sessionId = "testSessionId";

		MapSession session = this.repository.getSession(sessionId);

		assertThat(session).isNull();
		verify(this.sessions, times(1)).get(eq(sessionId));
	}

	@Test
	public void getSessionExpired() {
		MapSession expired = new MapSession();
		expired.setLastAccessedTime(System.currentTimeMillis() -
				(MapSession.DEFAULT_MAX_INACTIVE_INTERVAL_SECONDS * 1000 + 1000));
		given(this.sessions.get(eq(expired.getId()))).willReturn(expired);

		MapSession session = this.repository.getSession(expired.getId());

		assertThat(session).isNull();
		verify(this.sessions, times(1)).get(eq(expired.getId()));
		verify(this.sessions, times(1)).remove(eq(expired.getId()));
	}

	@Test
	public void getSessionFound() {
		MapSession saved = new MapSession();
		saved.setAttribute("savedName", "savedValue");
		given(this.sessions.get(eq(saved.getId()))).willReturn(saved);

		MapSession session = this.repository.getSession(saved.getId());

		assertThat(session.getId()).isEqualTo(saved.getId());
		assertThat(session.getAttribute("savedName")).isEqualTo("savedValue");
		verify(this.sessions, times(1)).get(eq(saved.getId()));
	}

	@Test
	public void delete() {
		String sessionId = "testSessionId";

		this.repository.delete(sessionId);

		verify(this.sessions, times(1)).remove(eq(sessionId));
	}

	@Test
	public void findByIndexNameAndIndexValueUnknownIndexName() {
		String indexValue = "testIndexValue";

		Map<String, MapSession> sessions = this.repository.findByIndexNameAndIndexValue(
				"testIndexName", indexValue);

		assertThat(sessions).isEmpty();
		verifyZeroInteractions(this.sessions);
	}

	@Test
	public void findByIndexNameAndIndexValuePrincipalIndexNameNotFound() {
		String principal = "username";

		Map<String, MapSession> sessions = this.repository.findByIndexNameAndIndexValue(
				FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME, principal);

		assertThat(sessions).isEmpty();
		verify(this.sessions, times(1)).values(isA(EqualPredicate.class));
	}

	@Test
	public void findByIndexNameAndIndexValuePrincipalIndexNameFound() {
		String principal = "username";
		Authentication authentication = new UsernamePasswordAuthenticationToken(principal,
				"notused", AuthorityUtils.createAuthorityList("ROLE_USER"));
		List<MapSession> saved = new ArrayList<MapSession>(2);
		MapSession saved1 = new MapSession();
		saved1.setAttribute(SPRING_SECURITY_CONTEXT, authentication);
		saved.add(saved1);
		MapSession saved2 = new MapSession();
		saved2.setAttribute(SPRING_SECURITY_CONTEXT, authentication);
		saved.add(saved2);
		given(this.sessions.values(isA(EqualPredicate.class))).willReturn(saved);

		Map<String, MapSession> sessions = this.repository.findByIndexNameAndIndexValue(
				FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME, principal);

		assertThat(sessions).hasSize(2);
		verify(this.sessions, times(1)).values(isA(EqualPredicate.class));
	}

}
