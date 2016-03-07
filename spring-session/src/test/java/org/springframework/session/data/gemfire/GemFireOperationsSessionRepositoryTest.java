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

package org.springframework.session.data.gemfire;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.gemstone.gemfire.cache.AttributesMutator;
import com.gemstone.gemfire.cache.Region;
import com.gemstone.gemfire.cache.query.SelectResults;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.runners.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;

import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.gemfire.GemfireAccessor;
import org.springframework.data.gemfire.GemfireOperations;
import org.springframework.session.ExpiringSession;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.events.AbstractSessionEvent;
import org.springframework.session.events.SessionDeletedEvent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.isA;
import static org.mockito.Matchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * The GemFireOperationsSessionRepositoryTest class is a test suite of test cases testing
 * the contract and functionality of the GemFireOperationsSessionRepository class.
 *
 * @author John Blum
 * @since 1.1.0
 * @see org.junit.Test
 * @see org.junit.runner.RunWith
 * @see org.mockito.Mock
 * @see org.mockito.Mockito
 * @see org.mockito.runners.MockitoJUnitRunner
 * @see org.springframework.session.data.gemfire.GemFireOperationsSessionRepository
 * @see com.gemstone.gemfire.cache.Region
 */
@RunWith(MockitoJUnitRunner.class)
public class GemFireOperationsSessionRepositoryTest {

	protected static final int MAX_INACTIVE_INTERVAL_IN_SECONDS = 600;

	@Mock
	private ApplicationEventPublisher mockApplicationEventPublisher;

	@Mock
	private AttributesMutator<Object, ExpiringSession> mockAttributesMutator;

	@Mock
	private Region<Object, ExpiringSession> mockRegion;

	@Mock
	private GemfireOperationsAccessor mockTemplate;

	private GemFireOperationsSessionRepository sessionRepository;

	@Before
	public void setup() throws Exception {
		given(this.mockRegion.getAttributesMutator())
				.willReturn(this.mockAttributesMutator);
		given(this.mockRegion.getFullPath()).willReturn("/Example");
		given(this.mockTemplate.<Object, ExpiringSession>getRegion())
				.willReturn(this.mockRegion);

		this.sessionRepository = new GemFireOperationsSessionRepository(
				this.mockTemplate);
		this.sessionRepository
				.setApplicationEventPublisher(this.mockApplicationEventPublisher);
		this.sessionRepository
				.setMaxInactiveIntervalInSeconds(MAX_INACTIVE_INTERVAL_IN_SECONDS);
		this.sessionRepository.afterPropertiesSet();

		assertThat(this.sessionRepository.getApplicationEventPublisher())
				.isSameAs(this.mockApplicationEventPublisher);
		assertThat(this.sessionRepository.getFullyQualifiedRegionName())
				.isEqualTo("/Example");
		assertThat(this.sessionRepository.getMaxInactiveIntervalInSeconds())
				.isEqualTo(MAX_INACTIVE_INTERVAL_IN_SECONDS);
	}

	@After
	public void tearDown() {
		verify(this.mockAttributesMutator, times(1))
				.addCacheListener(same(this.sessionRepository));
		verify(this.mockRegion, times(1)).getFullPath();
		verify(this.mockTemplate, times(1)).getRegion();
	}

	@Test
	@SuppressWarnings("unchecked")
	public void findByIndexNameValueFindsMatchingSession() {
		ExpiringSession mockSession = mock(ExpiringSession.class, "MockSession");

		given(mockSession.getId()).willReturn("1");

		SelectResults<Object> mockSelectResults = mock(SelectResults.class);

		given(mockSelectResults.asList())
				.willReturn(Collections.<Object>singletonList(mockSession));

		String indexName = "vip";
		String indexValue = "rwinch";

		String expectedQql = String.format(
				GemFireOperationsSessionRepository.FIND_SESSIONS_BY_INDEX_NAME_VALUE_QUERY,
				this.sessionRepository.getFullyQualifiedRegionName(), indexName);

		given(this.mockTemplate.find(eq(expectedQql), eq(indexValue)))
				.willReturn(mockSelectResults);

		Map<String, ExpiringSession> sessions = this.sessionRepository
				.findByIndexNameAndIndexValue(indexName, indexValue);

		assertThat(sessions).isNotNull();
		assertThat(sessions.size()).isEqualTo(1);
		assertThat(sessions.get("1")).isEqualTo(mockSession);

		verify(this.mockTemplate, times(1)).find(eq(expectedQql), eq(indexValue));
		verify(mockSelectResults, times(1)).asList();
		verify(mockSession, times(1)).getId();
	}

	@Test
	@SuppressWarnings("unchecked")
	public void findByPrincipalNameFindsMatchingSessions() throws Exception {
		ExpiringSession mockSessionOne = mock(ExpiringSession.class, "MockSessionOne");
		ExpiringSession mockSessionTwo = mock(ExpiringSession.class, "MockSessionTwo");
		ExpiringSession mockSessionThree = mock(ExpiringSession.class,
				"MockSessionThree");

		given(mockSessionOne.getId()).willReturn("1");
		given(mockSessionTwo.getId()).willReturn("2");
		given(mockSessionThree.getId()).willReturn("3");

		SelectResults<Object> mockSelectResults = mock(SelectResults.class);

		given(mockSelectResults.asList()).willReturn(
				Arrays.<Object>asList(mockSessionOne, mockSessionTwo, mockSessionThree));

		String principalName = "jblum";

		String expectedOql = String.format(
				GemFireOperationsSessionRepository.FIND_SESSIONS_BY_PRINCIPAL_NAME_QUERY,
				this.sessionRepository.getFullyQualifiedRegionName());

		given(this.mockTemplate.find(eq(expectedOql), eq(principalName)))
				.willReturn(mockSelectResults);

		Map<String, ExpiringSession> sessions = this.sessionRepository
				.findByIndexNameAndIndexValue(
						FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME,
						principalName);

		assertThat(sessions).isNotNull();
		assertThat(sessions.size()).isEqualTo(3);
		assertThat(sessions.get("1")).isEqualTo(mockSessionOne);
		assertThat(sessions.get("2")).isEqualTo(mockSessionTwo);
		assertThat(sessions.get("3")).isEqualTo(mockSessionThree);

		verify(this.mockTemplate, times(1)).find(eq(expectedOql), eq(principalName));
		verify(mockSelectResults, times(1)).asList();
		verify(mockSessionOne, times(1)).getId();
		verify(mockSessionTwo, times(1)).getId();
		verify(mockSessionThree, times(1)).getId();
	}

	@Test
	@SuppressWarnings("unchecked")
	public void findByPrincipalNameReturnsNoMatchingSessions() {
		SelectResults<Object> mockSelectResults = mock(SelectResults.class);

		given(mockSelectResults.asList()).willReturn(Collections.emptyList());

		String principalName = "jblum";

		String expectedOql = String.format(
				GemFireOperationsSessionRepository.FIND_SESSIONS_BY_PRINCIPAL_NAME_QUERY,
				this.sessionRepository.getFullyQualifiedRegionName());

		given(this.mockTemplate.find(eq(expectedOql), eq(principalName)))
				.willReturn(mockSelectResults);

		Map<String, ExpiringSession> sessions = this.sessionRepository
				.findByIndexNameAndIndexValue(
						FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME,
						principalName);

		assertThat(sessions).isNotNull();
		assertThat(sessions.isEmpty()).isTrue();

		verify(this.mockTemplate, times(1)).find(eq(expectedOql), eq(principalName));
		verify(mockSelectResults, times(1)).asList();
	}

	@Test
	public void prepareQueryReturnsPrincipalNameOql() {
		String actualQql = this.sessionRepository
				.prepareQuery(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME);
		String expectedOql = String.format(
				GemFireOperationsSessionRepository.FIND_SESSIONS_BY_PRINCIPAL_NAME_QUERY,
				this.sessionRepository.getFullyQualifiedRegionName());

		assertThat(actualQql).isEqualTo(expectedOql);
	}

	@Test
	public void prepareQueryReturnsIndexNameValueOql() {
		String attributeName = "testAttributeName";
		String actualOql = this.sessionRepository.prepareQuery(attributeName);
		String expectedOql = String.format(
				GemFireOperationsSessionRepository.FIND_SESSIONS_BY_INDEX_NAME_VALUE_QUERY,
				this.sessionRepository.getFullyQualifiedRegionName(), attributeName);

		assertThat(actualOql).isEqualTo(expectedOql);
	}

	@Test
	public void createProperlyInitializedSession() {
		final long beforeOrAtCreationTime = System.currentTimeMillis();

		ExpiringSession session = this.sessionRepository.createSession();

		assertThat(session).isInstanceOf(
				AbstractGemFireOperationsSessionRepository.GemFireSession.class);
		assertThat(session.getId()).isNotNull();
		assertThat(session.getAttributeNames().isEmpty()).isTrue();
		assertThat(session.getCreationTime())
				.isGreaterThanOrEqualTo(beforeOrAtCreationTime);
		assertThat(session.getLastAccessedTime())
				.isGreaterThanOrEqualTo(beforeOrAtCreationTime);
		assertThat(session.getMaxInactiveIntervalInSeconds())
				.isEqualTo(MAX_INACTIVE_INTERVAL_IN_SECONDS);
	}

	@Test
	public void getSessionDeletesMatchingExpiredSessionById() {
		final String expectedSessionId = "1";

		final ExpiringSession mockSession = mock(ExpiringSession.class);

		given(mockSession.isExpired()).willReturn(true);
		given(mockSession.getId()).willReturn(expectedSessionId);
		given(this.mockTemplate.get(eq(expectedSessionId))).willReturn(mockSession);
		given(this.mockTemplate.remove(eq(expectedSessionId))).willReturn(mockSession);

		willAnswer(new Answer<Void>() {
			public Void answer(final InvocationOnMock invocation) throws Throwable {
				ApplicationEvent applicationEvent = invocation.getArgumentAt(0,
						ApplicationEvent.class);

				assertThat(applicationEvent).isInstanceOf(SessionDeletedEvent.class);

				AbstractSessionEvent sessionEvent = (AbstractSessionEvent) applicationEvent;

				assertThat(sessionEvent.getSource()).isSameAs(
						GemFireOperationsSessionRepositoryTest.this.sessionRepository);
				assertThat(sessionEvent.getSession()).isSameAs(mockSession);
				assertThat(sessionEvent.getSessionId()).isEqualTo(expectedSessionId);

				return null;
			}
		}).given(this.mockApplicationEventPublisher)
				.publishEvent(any(ApplicationEvent.class));

		assertThat(this.sessionRepository.getSession(expectedSessionId)).isNull();

		verify(this.mockTemplate, times(1)).get(eq(expectedSessionId));
		verify(this.mockTemplate, times(1)).remove(eq(expectedSessionId));
		verify(mockSession, times(1)).isExpired();
		verify(mockSession, times(2)).getId();
		verify(this.mockApplicationEventPublisher, times(1))
				.publishEvent(isA(SessionDeletedEvent.class));
	}

	@Test
	public void getSessionFindsMatchingNonExpiredSessionById() {
		final String expectedId = "1";

		final long expectedCreationTime = System.currentTimeMillis();
		final long currentLastAccessedTime = (expectedCreationTime
				+ TimeUnit.MINUTES.toMillis(5));

		ExpiringSession mockSession = mock(ExpiringSession.class);

		given(mockSession.isExpired()).willReturn(false);
		given(mockSession.getId()).willReturn(expectedId);
		given(mockSession.getCreationTime()).willReturn(expectedCreationTime);
		given(mockSession.getLastAccessedTime()).willReturn(currentLastAccessedTime);
		given(mockSession.getAttributeNames())
				.willReturn(Collections.singleton("attrOne"));
		given(mockSession.getAttribute(eq("attrOne"))).willReturn("test");
		given(this.mockTemplate.get(eq(expectedId))).willReturn(mockSession);

		ExpiringSession actualSession = this.sessionRepository.getSession(expectedId);

		assertThat(actualSession).isNotSameAs(mockSession);
		assertThat(actualSession.getId()).isEqualTo(expectedId);
		assertThat(actualSession.getCreationTime()).isEqualTo(expectedCreationTime);
		assertThat(actualSession.getLastAccessedTime())
				.isNotEqualTo(currentLastAccessedTime);
		assertThat(actualSession.getLastAccessedTime())
				.isGreaterThanOrEqualTo(expectedCreationTime);
		assertThat(actualSession.getAttributeNames())
				.isEqualTo(Collections.singleton("attrOne"));
		assertThat(String.valueOf(actualSession.getAttribute("attrOne")))
				.isEqualTo("test");

		verify(this.mockTemplate, times(1)).get(eq(expectedId));
		verify(mockSession, times(1)).isExpired();
		verify(mockSession, times(1)).getId();
		verify(mockSession, times(1)).getCreationTime();
		verify(mockSession, times(1)).getLastAccessedTime();
		verify(mockSession, times(1)).getAttributeNames();
		verify(mockSession, times(1)).getAttribute(eq("attrOne"));
	}

	@Test
	public void getSessionReturnsNull() {
		given(this.mockTemplate.get(anyString())).willReturn(null);
		assertThat(this.sessionRepository.getSession("1")).isNull();
	}

	@Test
	public void saveStoresSession() {
		final String expectedSessionId = "1";

		final long expectedCreationTime = System.currentTimeMillis();
		final long expectedLastAccessTime = (expectedCreationTime
				+ TimeUnit.MINUTES.toMillis(5));

		ExpiringSession mockSession = mock(ExpiringSession.class);

		given(mockSession.getId()).willReturn(expectedSessionId);
		given(mockSession.getCreationTime()).willReturn(expectedCreationTime);
		given(mockSession.getLastAccessedTime()).willReturn(expectedLastAccessTime);
		given(mockSession.getMaxInactiveIntervalInSeconds())
				.willReturn(MAX_INACTIVE_INTERVAL_IN_SECONDS);
		given(mockSession.getAttributeNames()).willReturn(Collections.<String>emptySet());

		given(this.mockTemplate.put(eq(expectedSessionId),
				isA(AbstractGemFireOperationsSessionRepository.GemFireSession.class)))
						.willAnswer(new Answer<ExpiringSession>() {
							public ExpiringSession answer(
									final InvocationOnMock invocation) throws Throwable {
								ExpiringSession session = invocation.getArgumentAt(1,
										ExpiringSession.class);

								assertThat(session).isNotNull();
								assertThat(session.getId()).isEqualTo(expectedSessionId);
								assertThat(session.getCreationTime())
										.isEqualTo(expectedCreationTime);
								assertThat(session.getLastAccessedTime())
										.isEqualTo(expectedLastAccessTime);
								assertThat(session.getMaxInactiveIntervalInSeconds())
										.isEqualTo(MAX_INACTIVE_INTERVAL_IN_SECONDS);
								assertThat(session.getAttributeNames().isEmpty())
										.isTrue();

								return null;
							}
						});

		this.sessionRepository.save(mockSession);

		verify(mockSession, times(2)).getId();
		verify(mockSession, times(1)).getCreationTime();
		verify(mockSession, times(1)).getLastAccessedTime();
		verify(mockSession, times(1)).getMaxInactiveIntervalInSeconds();
		verify(mockSession, times(1)).getAttributeNames();
		verify(this.mockTemplate, times(1)).put(eq(expectedSessionId),
				isA(AbstractGemFireOperationsSessionRepository.GemFireSession.class));
	}

	@Test
	public void deleteRemovesExistingSessionAndHandlesDelete() {
		final String expectedSessionId = "1";

		final ExpiringSession mockSession = mock(ExpiringSession.class);

		given(mockSession.getId()).willReturn(expectedSessionId);
		given(this.mockTemplate.remove(eq(expectedSessionId))).willReturn(mockSession);

		willAnswer(new Answer<Void>() {
			public Void answer(final InvocationOnMock invocation) throws Throwable {
				ApplicationEvent applicationEvent = invocation.getArgumentAt(0,
						ApplicationEvent.class);

				assertThat(applicationEvent).isInstanceOf(SessionDeletedEvent.class);

				AbstractSessionEvent sessionEvent = (AbstractSessionEvent) applicationEvent;

				assertThat(sessionEvent.getSource()).isSameAs(
						GemFireOperationsSessionRepositoryTest.this.sessionRepository);
				assertThat(sessionEvent.getSession()).isSameAs(mockSession);
				assertThat(sessionEvent.getSessionId()).isEqualTo(expectedSessionId);

				return null;
			}
		}).given(this.mockApplicationEventPublisher)
				.publishEvent(isA(SessionDeletedEvent.class));

		this.sessionRepository.delete(expectedSessionId);

		verify(mockSession, times(1)).getId();
		verify(this.mockTemplate, times(1)).remove(eq(expectedSessionId));
		verify(this.mockApplicationEventPublisher, times(1))
				.publishEvent(isA(SessionDeletedEvent.class));
	}

	@Test
	public void deleteRemovesNonExistingSessionAndHandlesDelete() {
		final String expectedSessionId = "1";

		given(this.mockTemplate.remove(anyString())).willReturn(null);

		willAnswer(new Answer<Void>() {
			public Void answer(final InvocationOnMock invocation) throws Throwable {
				ApplicationEvent applicationEvent = invocation.getArgumentAt(0,
						ApplicationEvent.class);

				assertThat(applicationEvent).isInstanceOf(SessionDeletedEvent.class);

				AbstractSessionEvent sessionEvent = (AbstractSessionEvent) applicationEvent;

				assertThat(sessionEvent.getSource()).isSameAs(
						GemFireOperationsSessionRepositoryTest.this.sessionRepository);
				assertThat(sessionEvent.getSession()).isNull();
				assertThat(sessionEvent.getSessionId()).isEqualTo(expectedSessionId);

				return null;
			}
		}).given(this.mockApplicationEventPublisher)
				.publishEvent(isA(SessionDeletedEvent.class));

		this.sessionRepository.delete(expectedSessionId);

		verify(this.mockTemplate, times(1)).remove(eq(expectedSessionId));
		verify(this.mockApplicationEventPublisher, times(1))
				.publishEvent(isA(SessionDeletedEvent.class));
	}

	protected abstract class GemfireOperationsAccessor extends GemfireAccessor
			implements GemfireOperations {
	}

}
