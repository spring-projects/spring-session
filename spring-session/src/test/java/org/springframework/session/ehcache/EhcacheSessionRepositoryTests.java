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

package org.springframework.session.ehcache;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import net.sf.ehcache.Ehcache;
import net.sf.ehcache.Element;
import net.sf.ehcache.search.Query;
import net.sf.ehcache.search.Result;
import net.sf.ehcache.search.expression.Criteria;
import net.sf.ehcache.search.impl.ResultImpl;
import net.sf.ehcache.search.impl.ResultsImpl;
import net.sf.ehcache.store.StoreQuery;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.runners.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.session.ExpiringSession;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.MapSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.when;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;


/**
 * Test for {@link EhcacheSessionRepository}
 *
 * @author Jan Pichanič
 * @since 1.2.0
 */
@RunWith(MockitoJUnitRunner.class)
public class EhcacheSessionRepositoryTests {

	private static final String SPRING_SECURITY_CONTEXT = "SPRING_SECURITY_CONTEXT";

	@Rule
	public ExpectedException thrown = ExpectedException.none();

	@Mock
	private Ehcache cache;

	@Mock
	private Query query;

	@Mock
	private Criteria criteria;

	@Mock
	private StoreQuery storeQuery;

	private EhcacheSessionRepository repository;

	@Before
	public void setup() {
		this.repository = new EhcacheSessionRepository(this.cache);
		this.repository.setMaxInactiveIntervalInSeconds(MapSession.DEFAULT_MAX_INACTIVE_INTERVAL_SECONDS);
		// Need to reset before every test,
		// because repository initialization register DynamicAttributesExtractor
		// and increments interaction with mocked cache by 1
		reset(this.cache);
	}

	@Test
	public void createNewSessionTest() {
		EhcacheSessionRepository.EhcacheSession ehcacheSession = this.repository.createSession();
		verifyZeroInteractions(this.cache);
		assertThat(ehcacheSession).isNotNull();
	}

	@Test
	public void createSessionDefaultMaxInactiveInterval() throws Exception {
		ExpiringSession session = this.repository.createSession();
		verifyZeroInteractions(this.cache);
		assertThat(session.getMaxInactiveIntervalInSeconds())
				.isEqualTo(new MapSession().getMaxInactiveIntervalInSeconds());
	}

	@Test
	public void createSessionCustomMaxInactiveInterval() throws Exception {
		int interval = 1;
		this.repository.setMaxInactiveIntervalInSeconds(interval);
		ExpiringSession session = this.repository.createSession();
		verifyNoMoreInteractions(this.cache);
		assertThat(session.getMaxInactiveIntervalInSeconds()).isEqualTo(interval);
	}

	@Test
	public void getFoundSession() {
		EhcacheSessionRepository.EhcacheSession session = this.repository.createSession();
		String id = session.getId();
		final Element element = new Element(id, session);
		when(this.cache.get(id)).thenReturn(element);

		EhcacheSessionRepository.EhcacheSession retrievedSession = this.repository.getSession(id);
		verify(this.cache, times(1)).get(id);
		assertThat(retrievedSession).isNotNull();
		assertThat(session.getCreationTime() == retrievedSession.getCreationTime());
	}

	@Test
	public void getNonExistingSession() {
		String id = "1";
		when(this.cache.get(id)).thenReturn(null);

		EhcacheSessionRepository.EhcacheSession retrievedSession = this.repository.getSession(id);
		verify(this.cache, times(1)).get(id);
		assertThat(retrievedSession).isNull();
	}

	@Test
	public void letSessionExpireAndTryToGetIt() {
		EhcacheSessionRepository.EhcacheSession session = this.repository.createSession();
		String id = session.getId();
		final Element element = new Element(id, session);
		element.setTimeToIdle(1);

		when(this.cache.get(id)).thenAnswer(new Answer<Element>() {
			@Override
			public Element answer(InvocationOnMock invocation) throws Throwable {
				Thread.sleep(1001L);
				return element;
			}
		});
		assertThat(this.repository.getSession(id)).isNull();
		verify(this.cache, times(1)).get(id);
	}

	@Test
	public void findByIndexNameAndIndexValueUnknownIndexName() {
		String indexValue = "testIndexValue";

		Map<String, EhcacheSessionRepository.EhcacheSession> sessions = this.repository
				.findByIndexNameAndIndexValue("testIndexName", indexValue);

		assertThat(sessions).isEmpty();
		verifyZeroInteractions(this.cache);
	}

	@Test
	@SuppressWarnings("unchecked")
	public void findByIndexNameAndIndexValuePrincipalIndexNameNotFound() {
		String principal = "username";

		ResultsImpl results = new ResultsImpl(Collections.<Result>emptyList(), true, true, false, false);

		given(this.storeQuery.requestsKeys()).willReturn(true);
		given(this.storeQuery.requestsValues()).willReturn(true);
		given(this.cache.createQuery()).willReturn(this.query);
		given(this.query.addCriteria(any(Criteria.class)))
				.willReturn(this.query);
		given(this.query.includeKeys()).willReturn(this.query);
		given(this.query.includeValues()).willReturn(this.query);
		given(this.query.end()).willReturn(this.query);
		given(this.query.execute()).willReturn(results);

		Map<String, EhcacheSessionRepository.EhcacheSession> sessions = this.repository
				.findByIndexNameAndIndexValue(
						FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME,
						principal);

		assertThat(sessions).isEmpty();
		verify(this.cache, times(1)).createQuery();
		verify(this.query, times(1)).addCriteria(any(Criteria.class));
		verify(this.storeQuery, times(0)).requestsKeys();
		verify(this.storeQuery, times(0)).requestsValues();
		verify(this.query, times(1)).includeKeys();
		verify(this.query, times(1)).includeValues();
		verify(this.query, times(1)).end();
		verify(this.query, times(1)).execute();
		verifyNoMoreInteractions(this.query);
		verifyNoMoreInteractions(this.cache);
		verifyNoMoreInteractions(this.storeQuery);
	}


	@Test
	@SuppressWarnings("unchecked")
	public void findByIndexNameAndIndexValuePrincipalIndexNameFound() {
		String principal = "username";
		Authentication authentication = new UsernamePasswordAuthenticationToken(principal,
				"notused", AuthorityUtils.createAuthorityList("ROLE_USER"));
		EhcacheSessionRepository.EhcacheSession session1 = this.repository.createSession();
		session1.setAttribute(SPRING_SECURITY_CONTEXT, authentication);
		EhcacheSessionRepository.EhcacheSession session2 = this.repository.createSession();
		session2.setAttribute(SPRING_SECURITY_CONTEXT, authentication);

		ResultImpl result1 = new ResultImpl(session1.getId(), session1, this.storeQuery, new HashMap<String, Object>(), new Object[]{});
		ResultImpl result2 = new ResultImpl(session2.getId(), session2, this.storeQuery, new HashMap<String, Object>(), new Object[]{});
		ResultsImpl results = new ResultsImpl(Arrays.asList(result1, result2), true, true, false, false);

		given(this.storeQuery.requestsKeys()).willReturn(true);
		given(this.storeQuery.requestsValues()).willReturn(true);
		given(this.cache.createQuery()).willReturn(this.query);
		given(this.query.addCriteria(any(Criteria.class)))
				.willReturn(this.query);
		given(this.query.includeKeys()).willReturn(this.query);
		given(this.query.includeValues()).willReturn(this.query);
		given(this.query.end()).willReturn(this.query);
		given(this.query.execute()).willReturn(results);

		Map<String, EhcacheSessionRepository.EhcacheSession> sessions = this.repository
				.findByIndexNameAndIndexValue(
						FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME,
						principal);

		assertThat(sessions).hasSize(2);
		verify(this.cache, times(1)).createQuery();
		verify(this.query, times(1)).addCriteria(any(Criteria.class));
		verify(this.storeQuery, times(2)).requestsKeys();
		verify(this.storeQuery, times(2)).requestsValues();
		verify(this.query, times(1)).includeKeys();
		verify(this.query, times(1)).includeValues();
		verify(this.query, times(1)).end();
		verify(this.query, times(1)).execute();
		verifyNoMoreInteractions(this.query);
		verifyNoMoreInteractions(this.cache);
		verifyNoMoreInteractions(this.storeQuery);
	}
}
