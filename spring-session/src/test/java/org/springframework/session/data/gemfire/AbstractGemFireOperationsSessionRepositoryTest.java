/*
 * Copyright 2002-2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.session.data.gemfire;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Fail.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.isA;
import static org.mockito.Matchers.same;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.session.data.gemfire.AbstractGemFireOperationsSessionRepository.GemFireSession;
import static org.springframework.session.data.gemfire.AbstractGemFireOperationsSessionRepository.GemFireSessionAttributes;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.apache.commons.logging.Log;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.runners.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.gemfire.GemfireOperations;
import org.springframework.data.gemfire.GemfireTemplate;
import org.springframework.session.ExpiringSession;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.session.data.gemfire.config.annotation.web.http.GemFireHttpSessionConfiguration;
import org.springframework.session.events.AbstractSessionEvent;
import org.springframework.session.events.SessionCreatedEvent;
import org.springframework.session.events.SessionDeletedEvent;
import org.springframework.session.events.SessionDestroyedEvent;
import org.springframework.session.events.SessionExpiredEvent;

import com.gemstone.gemfire.cache.AttributesMutator;
import com.gemstone.gemfire.cache.EntryEvent;
import com.gemstone.gemfire.cache.Region;

import edu.umd.cs.mtc.MultithreadedTestCase;
import edu.umd.cs.mtc.TestFramework;

/**
 * The AbstractGemFireOperationsSessionRepositoryTest class is a test suite of test cases testing the contract
 * and functionality of the AbstractGemFireOperationsSessionRepository class.
 *
 * @author John Blum
 * @see org.junit.Rule
 * @see org.junit.Test
 * @see org.junit.rules.ExpectedException
 * @see org.junit.runner.RunWith
 * @see org.mockito.Mock
 * @see org.mockito.Mockito
 * @see org.mockito.runners.MockitoJUnitRunner
 * @see org.springframework.data.gemfire.GemfireOperations
 * @see org.springframework.session.ExpiringSession
 * @see org.springframework.session.Session
 * @see org.springframework.session.data.gemfire.AbstractGemFireOperationsSessionRepository
 * @see edu.umd.cs.mtc.MultithreadedTestCase
 * @see edu.umd.cs.mtc.TestFramework
 * @since 1.1.0
 */
@RunWith(MockitoJUnitRunner.class)
public class AbstractGemFireOperationsSessionRepositoryTest {

	protected static final int MAX_INACTIVE_INTERVAL_IN_SECONDS = 600;

	@Rule
	public ExpectedException expectedException = ExpectedException.none();

	@Mock
	private GemfireOperations mockGemfireOperations;

	@Mock
	private Log mockLog;

	private AbstractGemFireOperationsSessionRepository sessionRepository;

	@Before
	public void setup() {
		sessionRepository = new TestGemFireOperationsSessionRepository(mockGemfireOperations) {
			@Override Log newLogger() {
				return mockLog;
			}
		};
	}

	protected static <E> Set<E> asSet(E... elements) {
		Set<E> set = new HashSet<E>(elements.length);
		Collections.addAll(set, elements);
		return set;
	}

	protected ExpiringSession mockSession(String sessionId, long creationAndLastAccessedTime,
		int maxInactiveIntervalInSeconds) {

		return mockSession(sessionId, creationAndLastAccessedTime, creationAndLastAccessedTime,
			maxInactiveIntervalInSeconds);
	}

	protected ExpiringSession mockSession(String sessionId, long creationTime, long lastAccessedTime,
		int maxInactiveIntervalInSeconds) {

		ExpiringSession mockSession = mock(ExpiringSession.class, sessionId);

		when(mockSession.getId()).thenReturn(sessionId);
		when(mockSession.getCreationTime()).thenReturn(creationTime);
		when(mockSession.getLastAccessedTime()).thenReturn(lastAccessedTime);
		when(mockSession.getMaxInactiveIntervalInSeconds()).thenReturn(maxInactiveIntervalInSeconds);

		return mockSession;
	}

	@Test
	public void constructGemFireOperationsSessionRepositoryWithNullTemplate() {
		expectedException.expect(IllegalArgumentException.class);
		expectedException.expectMessage("GemfireOperations must not be null");
		new TestGemFireOperationsSessionRepository(null);
	}

	@Test
	@SuppressWarnings("unchecked")
	public void gemfireOperationsSessionRepositoryIsProperlyConstructedAndInitialized() throws Exception {
		ApplicationEventPublisher mockApplicationEventPublisher = mock(ApplicationEventPublisher.class);
		AttributesMutator<Object,ExpiringSession> mockAttributesMutator = mock(AttributesMutator.class);
		Region<Object,ExpiringSession> mockRegion = mock(Region.class);

		when(mockRegion.getFullPath()).thenReturn("/Example");
		when(mockRegion.getAttributesMutator()).thenReturn(mockAttributesMutator);

		GemfireTemplate template = new GemfireTemplate(mockRegion);

		AbstractGemFireOperationsSessionRepository sessionRepository =
			new TestGemFireOperationsSessionRepository(template);

		ApplicationEventPublisher applicationEventPublisher = sessionRepository.getApplicationEventPublisher();

		assertThat(applicationEventPublisher).isNotNull();
		assertThat(sessionRepository.getFullyQualifiedRegionName()).isNull();
		assertThat(sessionRepository.getMaxInactiveIntervalInSeconds()).isEqualTo(
			GemFireHttpSessionConfiguration.DEFAULT_MAX_INACTIVE_INTERVAL_IN_SECONDS);
		assertThat(sessionRepository.getTemplate()).isSameAs(template);

		sessionRepository.setApplicationEventPublisher(mockApplicationEventPublisher);
		sessionRepository.setMaxInactiveIntervalInSeconds(300);
		sessionRepository.afterPropertiesSet();

		assertThat(sessionRepository.getApplicationEventPublisher()).isSameAs(mockApplicationEventPublisher);
		assertThat(sessionRepository.getFullyQualifiedRegionName()).isEqualTo("/Example");
		assertThat(sessionRepository.getMaxInactiveIntervalInSeconds()).isEqualTo(300);
		assertThat(sessionRepository.getTemplate()).isSameAs(template);

		verify(mockRegion, times(1)).getAttributesMutator();
		verify(mockRegion, times(1)).getFullPath();
		verify(mockAttributesMutator, times(1)).addCacheListener(same(sessionRepository));
	}

	@Test
	public void maxInactiveIntervalInSecondsAllowsNegativeValuesAndExtremelyLargeValues() {
		assertThat(sessionRepository.getMaxInactiveIntervalInSeconds()).isEqualTo(
			GemFireHttpSessionConfiguration.DEFAULT_MAX_INACTIVE_INTERVAL_IN_SECONDS);

		sessionRepository.setMaxInactiveIntervalInSeconds(-1);

		assertThat(sessionRepository.getMaxInactiveIntervalInSeconds()).isEqualTo(-1);

		sessionRepository.setMaxInactiveIntervalInSeconds(Integer.MIN_VALUE);

		assertThat(sessionRepository.getMaxInactiveIntervalInSeconds()).isEqualTo(Integer.MIN_VALUE);

		sessionRepository.setMaxInactiveIntervalInSeconds(3600);

		assertThat(sessionRepository.getMaxInactiveIntervalInSeconds()).isEqualTo(3600);

		sessionRepository.setMaxInactiveIntervalInSeconds(Integer.MAX_VALUE);

		assertThat(sessionRepository.getMaxInactiveIntervalInSeconds()).isEqualTo(Integer.MAX_VALUE);
	}

	@Test
	@SuppressWarnings("unchecked")
	public void afterCreateWithSessionPublishesSessionCreatedEvent() {
		final String sessionId = "abc123";
		final ExpiringSession mockSession = mock(ExpiringSession.class);

		when(mockSession.getId()).thenReturn(sessionId);

		ApplicationEventPublisher mockApplicationEventPublisher = mock(ApplicationEventPublisher.class);

		doAnswer(new Answer<Void>() {
			public Void answer(final InvocationOnMock invocation) throws Throwable {
				ApplicationEvent applicationEvent = invocation.getArgumentAt(0, ApplicationEvent.class);

				assertThat(applicationEvent).isInstanceOf(SessionCreatedEvent.class);

				AbstractSessionEvent sessionEvent = (AbstractSessionEvent) applicationEvent;

				assertThat(sessionEvent.getSource()).isEqualTo(sessionRepository);
				assertThat(sessionEvent.getSession()).isEqualTo(mockSession);
				assertThat(sessionEvent.getSessionId()).isEqualTo(sessionId);

				return null;
			}
		}).when(mockApplicationEventPublisher).publishEvent(isA(ApplicationEvent.class));

		EntryEvent<Object, ExpiringSession> mockEntryEvent = mock(EntryEvent.class);

		when(mockEntryEvent.getKey()).thenReturn(sessionId);
		when(mockEntryEvent.getNewValue()).thenReturn(mockSession);

		sessionRepository.setApplicationEventPublisher(mockApplicationEventPublisher);
		sessionRepository.afterCreate(mockEntryEvent);

		assertThat(sessionRepository.getApplicationEventPublisher()).isSameAs(mockApplicationEventPublisher);

		verify(mockEntryEvent, times(1)).getKey();
		verify(mockEntryEvent, times(2)).getNewValue();
		verify(mockEntryEvent, never()).getOldValue();
		verify(mockSession, times(1)).getId();
		verify(mockApplicationEventPublisher, times(1)).publishEvent(isA(SessionCreatedEvent.class));
	}

	@Test
	@SuppressWarnings("unchecked")
	public void afterCreateWithSessionIdPublishesSessionCreatedEvent() {
		final String sessionId = "abc123";

		ApplicationEventPublisher mockApplicationEventPublisher = mock(ApplicationEventPublisher.class);

		doAnswer(new Answer<Void>() {
			public Void answer(final InvocationOnMock invocation) throws Throwable {
				ApplicationEvent applicationEvent = invocation.getArgumentAt(0, ApplicationEvent.class);

				assertThat(applicationEvent).isInstanceOf(SessionCreatedEvent.class);

				AbstractSessionEvent sessionEvent = (AbstractSessionEvent) applicationEvent;

				assertThat(sessionEvent.getSource()).isEqualTo(sessionRepository);
				assertThat(sessionEvent.getSession()).isNull();
				assertThat(sessionEvent.getSessionId()).isEqualTo(sessionId);

				return null;
			}
		}).when(mockApplicationEventPublisher).publishEvent(isA(ApplicationEvent.class));

		EntryEvent<Object, ExpiringSession> mockEntryEvent = mock(EntryEvent.class);

		when(mockEntryEvent.getKey()).thenReturn(sessionId);
		when(mockEntryEvent.getNewValue()).thenReturn(null);

		sessionRepository.setApplicationEventPublisher(mockApplicationEventPublisher);
		sessionRepository.afterCreate(mockEntryEvent);

		assertThat(sessionRepository.getApplicationEventPublisher()).isSameAs(mockApplicationEventPublisher);

		verify(mockEntryEvent, times(1)).getKey();
		verify(mockEntryEvent, times(2)).getNewValue();
		verify(mockEntryEvent, never()).getOldValue();
		verify(mockApplicationEventPublisher, times(1)).publishEvent(isA(SessionCreatedEvent.class));
	}

	@Test
	@SuppressWarnings("unchecked")
	public void afterCreatedWithNonSessionTypeDoesNotPublishSessionCreatedEvent() {
		TestGemFireOperationsSessionRepository sessionRepository = new TestGemFireOperationsSessionRepository(mockGemfireOperations) {
			@Override protected void handleCreated(final String sessionId, final ExpiringSession session) {
				fail("handleCreated(..) should not have been called");
			}
		};

		EntryEvent<Object, ?> mockEntryEvent = mock(EntryEvent.class);

		when(mockEntryEvent.getKey()).thenReturn("abc123");
		when(mockEntryEvent.getNewValue()).thenReturn(new Object());

		sessionRepository.afterCreate((EntryEvent<Object, ExpiringSession>) mockEntryEvent);

		verify(mockEntryEvent, never()).getKey();
		verify(mockEntryEvent, times(1)).getNewValue();
		verify(mockEntryEvent, never()).getOldValue();
	}

	@Test
	@SuppressWarnings("unchecked")
	public void afterDestroyWithSessionPublishesSessionDestroyedEvent() {
		final String sessionId = "def456";
		final ExpiringSession mockSession = mock(ExpiringSession.class);

		when(mockSession.getId()).thenReturn(sessionId);

		ApplicationEventPublisher mockApplicationEventPublisher = mock(ApplicationEventPublisher.class);

		doAnswer(new Answer<Void>() {
			public Void answer(final InvocationOnMock invocation) throws Throwable {
				ApplicationEvent applicationEvent = invocation.getArgumentAt(0, ApplicationEvent.class);

				assertThat(applicationEvent).isInstanceOf(SessionDestroyedEvent.class);

				AbstractSessionEvent sessionEvent = (AbstractSessionEvent) applicationEvent;

				assertThat(sessionEvent.getSource()).isEqualTo(sessionRepository);
				assertThat(sessionEvent.getSession()).isEqualTo(mockSession);
				assertThat(sessionEvent.getSessionId()).isEqualTo(sessionId);

				return null;
			}
		}).when(mockApplicationEventPublisher).publishEvent(isA(ApplicationEvent.class));

		EntryEvent<Object, ExpiringSession> mockEntryEvent = mock(EntryEvent.class);

		when(mockEntryEvent.getKey()).thenReturn(sessionId);
		when(mockEntryEvent.getOldValue()).thenReturn(mockSession);

		sessionRepository.setApplicationEventPublisher(mockApplicationEventPublisher);
		sessionRepository.afterDestroy(mockEntryEvent);

		assertThat(sessionRepository.getApplicationEventPublisher()).isSameAs(mockApplicationEventPublisher);

		verify(mockEntryEvent, times(1)).getKey();
		verify(mockEntryEvent, never()).getNewValue();
		verify(mockEntryEvent, times(1)).getOldValue();
		verify(mockSession, times(1)).getId();
		verify(mockApplicationEventPublisher, times(1)).publishEvent(isA(SessionDestroyedEvent.class));
	}

	@Test
	@SuppressWarnings("unchecked")
	public void afterDestroyWithSessionIdPublishesSessionDestroyedEvent() {
		final String sessionId = "def456";

		ApplicationEventPublisher mockApplicationEventPublisher = mock(ApplicationEventPublisher.class);

		doAnswer(new Answer<Void>() {
			public Void answer(final InvocationOnMock invocation) throws Throwable {
				ApplicationEvent applicationEvent = invocation.getArgumentAt(0, ApplicationEvent.class);

				assertThat(applicationEvent).isInstanceOf(SessionDestroyedEvent.class);

				AbstractSessionEvent sessionEvent = (AbstractSessionEvent) applicationEvent;

				assertThat(sessionEvent.getSource()).isEqualTo(sessionRepository);
				assertThat(sessionEvent.getSession()).isNull();
				assertThat(sessionEvent.getSessionId()).isEqualTo(sessionId);

				return null;
			}
		}).when(mockApplicationEventPublisher).publishEvent(isA(ApplicationEvent.class));

		EntryEvent<Object, ExpiringSession> mockEntryEvent = mock(EntryEvent.class);

		when(mockEntryEvent.getKey()).thenReturn(sessionId);
		when(mockEntryEvent.getOldValue()).thenReturn(null);

		sessionRepository.setApplicationEventPublisher(mockApplicationEventPublisher);
		sessionRepository.afterDestroy(mockEntryEvent);

		assertThat(sessionRepository.getApplicationEventPublisher()).isSameAs(mockApplicationEventPublisher);

		verify(mockEntryEvent, times(1)).getKey();
		verify(mockEntryEvent, never()).getNewValue();
		verify(mockEntryEvent, times(1)).getOldValue();
		verify(mockApplicationEventPublisher, times(1)).publishEvent(isA(SessionDestroyedEvent.class));
	}

	@Test
	@SuppressWarnings("unchecked")
	public void afterDestroyWithNonSessionTypePublishesSessionDestroyedEventWithSessionId() {
		final String sessionId = "def456";

		ApplicationEventPublisher mockApplicationEventPublisher = mock(ApplicationEventPublisher.class);

		doAnswer(new Answer<Void>() {
			public Void answer(final InvocationOnMock invocation) throws Throwable {
				ApplicationEvent applicationEvent = invocation.getArgumentAt(0, ApplicationEvent.class);

				assertThat(applicationEvent).isInstanceOf(SessionDestroyedEvent.class);

				AbstractSessionEvent sessionEvent = (AbstractSessionEvent) applicationEvent;

				assertThat(sessionEvent.getSource()).isEqualTo(sessionRepository);
				assertThat(sessionEvent.getSession()).isNull();
				assertThat(sessionEvent.getSessionId()).isEqualTo(sessionId);

				return null;
			}
		}).when(mockApplicationEventPublisher).publishEvent(isA(ApplicationEvent.class));

		EntryEvent<Object, ?> mockEntryEvent = mock(EntryEvent.class);

		when(mockEntryEvent.getKey()).thenReturn(sessionId);
		when(mockEntryEvent.getOldValue()).thenReturn(new Object());

		sessionRepository.setApplicationEventPublisher(mockApplicationEventPublisher);
		sessionRepository.afterDestroy((EntryEvent<Object, ExpiringSession>) mockEntryEvent);

		assertThat(sessionRepository.getApplicationEventPublisher()).isSameAs(mockApplicationEventPublisher);

		verify(mockEntryEvent, times(1)).getKey();
		verify(mockEntryEvent, never()).getNewValue();
		verify(mockEntryEvent, times(1)).getOldValue();
		verify(mockApplicationEventPublisher, times(1)).publishEvent(isA(SessionDestroyedEvent.class));
	}

	@Test
	@SuppressWarnings("unchecked")
	public void afterInvalidateWithSessionPublishesSessionExpiredEvent() {
		final String sessionId = "ghi789";
		final ExpiringSession mockSession = mock(ExpiringSession.class);

		when(mockSession.getId()).thenReturn(sessionId);

		ApplicationEventPublisher mockApplicationEventPublisher = mock(ApplicationEventPublisher.class);

		doAnswer(new Answer<Void>() {
			public Void answer(final InvocationOnMock invocation) throws Throwable {
				ApplicationEvent applicationEvent = invocation.getArgumentAt(0, ApplicationEvent.class);

				assertThat(applicationEvent).isInstanceOf(SessionExpiredEvent.class);

				AbstractSessionEvent sessionEvent = (AbstractSessionEvent) applicationEvent;

				assertThat(sessionEvent.getSource()).isEqualTo(sessionRepository);
				assertThat(sessionEvent.getSession()).isEqualTo(mockSession);
				assertThat(sessionEvent.getSessionId()).isEqualTo(sessionId);

				return null;
			}
		}).when(mockApplicationEventPublisher).publishEvent(isA(ApplicationEvent.class));

		EntryEvent<Object, ExpiringSession> mockEntryEvent = mock(EntryEvent.class);

		when(mockEntryEvent.getKey()).thenReturn(sessionId);
		when(mockEntryEvent.getOldValue()).thenReturn(mockSession);

		sessionRepository.setApplicationEventPublisher(mockApplicationEventPublisher);
		sessionRepository.afterInvalidate(mockEntryEvent);

		assertThat(sessionRepository.getApplicationEventPublisher()).isSameAs(mockApplicationEventPublisher);

		verify(mockEntryEvent, times(1)).getKey();
		verify(mockEntryEvent, never()).getNewValue();
		verify(mockEntryEvent, times(1)).getOldValue();
		verify(mockSession, times(1)).getId();
		verify(mockApplicationEventPublisher, times(1)).publishEvent(isA(SessionExpiredEvent.class));
	}

	@Test
	@SuppressWarnings("unchecked")
	public void afterInvalidateWithSessionIdPublishesSessionExpiredEvent() {
		final String sessionId = "ghi789";

		ApplicationEventPublisher mockApplicationEventPublisher = mock(ApplicationEventPublisher.class);

		doAnswer(new Answer<Void>() {
			public Void answer(final InvocationOnMock invocation) throws Throwable {
				ApplicationEvent applicationEvent = invocation.getArgumentAt(0, ApplicationEvent.class);

				assertThat(applicationEvent).isInstanceOf(SessionExpiredEvent.class);

				AbstractSessionEvent sessionEvent = (AbstractSessionEvent) applicationEvent;

				assertThat(sessionEvent.getSource()).isEqualTo(sessionRepository);
				assertThat(sessionEvent.getSession()).isNull();
				assertThat(sessionEvent.getSessionId()).isEqualTo(sessionId);

				return null;
			}
		}).when(mockApplicationEventPublisher).publishEvent(isA(ApplicationEvent.class));

		EntryEvent<Object, ExpiringSession> mockEntryEvent = mock(EntryEvent.class);

		when(mockEntryEvent.getKey()).thenReturn(sessionId);
		when(mockEntryEvent.getOldValue()).thenReturn(null);

		sessionRepository.setApplicationEventPublisher(mockApplicationEventPublisher);
		sessionRepository.afterInvalidate(mockEntryEvent);

		assertThat(sessionRepository.getApplicationEventPublisher()).isSameAs(mockApplicationEventPublisher);

		verify(mockEntryEvent, times(1)).getKey();
		verify(mockEntryEvent, never()).getNewValue();
		verify(mockEntryEvent, times(1)).getOldValue();
		verify(mockApplicationEventPublisher, times(1)).publishEvent(isA(SessionExpiredEvent.class));
	}

	@Test
	@SuppressWarnings("unchecked")
	public void afterInvalidateWithNonSessionTypePublishesSessionExpiredEventWithSessionId() {
		final String sessionId = "ghi789";

		ApplicationEventPublisher mockApplicationEventPublisher = mock(ApplicationEventPublisher.class);

		doAnswer(new Answer<Void>() {
			public Void answer(final InvocationOnMock invocation) throws Throwable {
				ApplicationEvent applicationEvent = invocation.getArgumentAt(0, ApplicationEvent.class);

				assertThat(applicationEvent).isInstanceOf(SessionExpiredEvent.class);

				AbstractSessionEvent sessionEvent = (AbstractSessionEvent) applicationEvent;

				assertThat(sessionEvent.getSource()).isEqualTo(sessionRepository);
				assertThat(sessionEvent.getSession()).isNull();
				assertThat(sessionEvent.getSessionId()).isEqualTo(sessionId);

				return null;
			}
		}).when(mockApplicationEventPublisher).publishEvent(isA(ApplicationEvent.class));

		EntryEvent<Object, ?> mockEntryEvent = mock(EntryEvent.class);

		when(mockEntryEvent.getKey()).thenReturn(sessionId);
		when(mockEntryEvent.getOldValue()).thenReturn(new Object());

		sessionRepository.setApplicationEventPublisher(mockApplicationEventPublisher);
		sessionRepository.afterInvalidate((EntryEvent<Object, ExpiringSession>) mockEntryEvent);

		assertThat(sessionRepository.getApplicationEventPublisher()).isSameAs(mockApplicationEventPublisher);

		verify(mockEntryEvent, times(1)).getKey();
		verify(mockEntryEvent, never()).getNewValue();
		verify(mockEntryEvent, times(1)).getOldValue();
		verify(mockApplicationEventPublisher, times(1)).publishEvent(isA(SessionExpiredEvent.class));
	}

	@Test
	public void handleDeletedWithSessionPublishesSessionDeletedEvent() {
		final String sessionId = "abc123";
		final ExpiringSession mockSession = mock(ExpiringSession.class);

		when(mockSession.getId()).thenReturn(sessionId);

		ApplicationEventPublisher mockApplicationEventPublisher = mock(ApplicationEventPublisher.class);

		doAnswer(new Answer<Void>() {
			public Void answer(final InvocationOnMock invocation) throws Throwable {
				ApplicationEvent applicationEvent = invocation.getArgumentAt(0, ApplicationEvent.class);

				assertThat(applicationEvent).isInstanceOf(SessionDeletedEvent.class);

				AbstractSessionEvent sessionEvent = (AbstractSessionEvent) applicationEvent;

				assertThat(sessionEvent.getSource()).isEqualTo(sessionRepository);
				assertThat(sessionEvent.getSession()).isEqualTo(mockSession);
				assertThat(sessionEvent.getSessionId()).isEqualTo(sessionId);

				return null;
			}
		}).when(mockApplicationEventPublisher).publishEvent(isA(ApplicationEvent.class));

		sessionRepository.setApplicationEventPublisher(mockApplicationEventPublisher);
		sessionRepository.handleDeleted(sessionId, mockSession);

		assertThat(sessionRepository.getApplicationEventPublisher()).isSameAs(mockApplicationEventPublisher);

		verify(mockSession, times(1)).getId();
		verify(mockApplicationEventPublisher, times(1)).publishEvent(isA(SessionDeletedEvent.class));
	}

	@Test
	public void handleDeletedWithSessionIdPublishesSessionDeletedEvent() {
		final String sessionId = "abc123";

		ApplicationEventPublisher mockApplicationEventPublisher = mock(ApplicationEventPublisher.class);

		doAnswer(new Answer<Void>() {
			public Void answer(final InvocationOnMock invocation) throws Throwable {
				ApplicationEvent applicationEvent = invocation.getArgumentAt(0, ApplicationEvent.class);

				assertThat(applicationEvent).isInstanceOf(SessionDeletedEvent.class);

				AbstractSessionEvent sessionEvent = (AbstractSessionEvent) applicationEvent;

				assertThat(sessionEvent.getSource()).isEqualTo(sessionRepository);
				assertThat(sessionEvent.getSession()).isNull();
				assertThat(sessionEvent.getSessionId()).isEqualTo(sessionId);

				return null;
			}
		}).when(mockApplicationEventPublisher).publishEvent(isA(ApplicationEvent.class));

		sessionRepository.setApplicationEventPublisher(mockApplicationEventPublisher);
		sessionRepository.handleDeleted(sessionId, null);

		assertThat(sessionRepository.getApplicationEventPublisher()).isSameAs(mockApplicationEventPublisher);

		verify(mockApplicationEventPublisher, times(1)).publishEvent(isA(SessionDeletedEvent.class));
	}

	@Test
	public void publishEventHandlesThrowable() {
		ApplicationEvent mockApplicationEvent = mock(ApplicationEvent.class);

		ApplicationEventPublisher mockApplicationEventPublisher = mock(ApplicationEventPublisher.class);

		doThrow(new IllegalStateException("test")).when(mockApplicationEventPublisher)
			.publishEvent(any(ApplicationEvent.class));

		sessionRepository.setApplicationEventPublisher(mockApplicationEventPublisher);
		sessionRepository.publishEvent(mockApplicationEvent);

		assertThat(sessionRepository.getApplicationEventPublisher()).isSameAs(mockApplicationEventPublisher);

		verify(mockApplicationEventPublisher, times(1)).publishEvent(eq(mockApplicationEvent));
		verify(mockLog, times(1)).error(eq(String.format("error occurred publishing event (%1$s)", mockApplicationEvent)),
			isA(IllegalStateException.class));
	}

	@Test
	public void constructGemFireSessionWithDefaultInitialization() {
		final long beforeOrAtCreationTime = System.currentTimeMillis();

		GemFireSession session = new GemFireSession();

		assertThat(session.getId()).isNotNull();
		assertThat(session.getCreationTime()).isGreaterThanOrEqualTo(beforeOrAtCreationTime);
		assertThat(session.getLastAccessedTime()).isGreaterThanOrEqualTo(beforeOrAtCreationTime);
		assertThat(session.getMaxInactiveIntervalInSeconds()).isEqualTo(0);
		assertThat(session.getAttributeNames()).isNotNull();
		assertThat(session.getAttributeNames().isEmpty()).isTrue();
	}

	@Test
	public void constructGemFireSessionWithId() {
		final long beforeOrAtCreationTime = System.currentTimeMillis();

		GemFireSession session = new GemFireSession("1");

		assertThat(session.getId()).isEqualTo("1");
		assertThat(session.getCreationTime()).isGreaterThanOrEqualTo(beforeOrAtCreationTime);
		assertThat(session.getLastAccessedTime()).isGreaterThanOrEqualTo(beforeOrAtCreationTime);
		assertThat(session.getMaxInactiveIntervalInSeconds()).isEqualTo(0);
		assertThat(session.getAttributeNames()).isNotNull();
		assertThat(session.getAttributeNames().isEmpty()).isTrue();
	}

	@Test
	public void constructGemFireSessionWithSession() {
		final long expectedCreationTime = 1l;
		final long expectedLastAccessTime = 2l;

		ExpiringSession mockSession = mockSession("2", expectedCreationTime, expectedLastAccessTime,
			MAX_INACTIVE_INTERVAL_IN_SECONDS);

		Set<String> expectedAttributedNames = asSet("attrOne", "attrTwo");

		when(mockSession.getAttributeNames()).thenReturn(expectedAttributedNames);
		when(mockSession.getAttribute(eq("attrOne"))).thenReturn("testOne");
		when(mockSession.getAttribute(eq("attrTwo"))).thenReturn("testTwo");

		GemFireSession gemfireSession = new GemFireSession(mockSession);

		assertThat(gemfireSession.getId()).isEqualTo("2");
		assertThat(gemfireSession.getCreationTime()).isEqualTo(expectedCreationTime);
		assertThat(gemfireSession.getLastAccessedTime()).isEqualTo(expectedLastAccessTime);
		assertThat(gemfireSession.getMaxInactiveIntervalInSeconds()).isEqualTo(MAX_INACTIVE_INTERVAL_IN_SECONDS);
		assertThat(gemfireSession.getAttributeNames()).isEqualTo(expectedAttributedNames);
		assertThat(String.valueOf(gemfireSession.getAttribute("attrOne"))).isEqualTo("testOne");
		assertThat(String.valueOf(gemfireSession.getAttribute("attrTwo"))).isEqualTo("testTwo");

		verify(mockSession, times(1)).getId();
		verify(mockSession, times(1)).getCreationTime();
		verify(mockSession, times(1)).getLastAccessedTime();
		verify(mockSession, times(1)).getMaxInactiveIntervalInSeconds();
		verify(mockSession, times(1)).getAttributeNames();
		verify(mockSession, times(1)).getAttribute(eq("attrOne"));
		verify(mockSession, times(1)).getAttribute(eq("attrTwo"));
	}

	@Test
	public void constructGemFireSessionWithNullSession() {
		expectedException.expect(IllegalArgumentException.class);
		expectedException.expectMessage("The ExpiringSession to copy cannot be null");
		new GemFireSession((ExpiringSession) null);
	}

	@Test
	public void constructGemFireSessionWithUnspecifiedId() {
		expectedException.expect(IllegalArgumentException.class);
		expectedException.expectMessage("ID must be specified");
		new GemFireSession(" ");
	}

	@Test
	public void createNewGemFireSession() {
		final long beforeOrAtCreationTime = System.currentTimeMillis();

		GemFireSession session = GemFireSession.create(120);

		assertThat(session).isNotNull();
		assertThat(session.getId()).isNotNull();
		assertThat(session.getCreationTime()).isGreaterThanOrEqualTo(beforeOrAtCreationTime);
		assertThat(session.getLastAccessedTime()).isEqualTo(session.getCreationTime());
		assertThat(session.getMaxInactiveIntervalInSeconds()).isEqualTo(120);
		assertThat(session.getAttributeNames()).isNotNull();
		assertThat(session.getAttributeNames().isEmpty()).isTrue();
	}

	@Test
	public void fromExistingSession() {
		final long expectedCreationTime = 1l;
		final long expectedLastAccessedTime = 2l;

		ExpiringSession mockSession = mockSession("4", expectedCreationTime, expectedLastAccessedTime,
			MAX_INACTIVE_INTERVAL_IN_SECONDS);

		when(mockSession.getAttributeNames()).thenReturn(Collections.<String>emptySet());

		GemFireSession gemfireSession = GemFireSession.from(mockSession);

		assertThat(gemfireSession).isNotNull();
		assertThat(gemfireSession.getId()).isEqualTo("4");
		assertThat(gemfireSession.getCreationTime()).isEqualTo(expectedCreationTime);
		assertThat(gemfireSession.getLastAccessedTime()).isNotEqualTo(expectedLastAccessedTime);
		assertThat(gemfireSession.getLastAccessedTime()).isGreaterThanOrEqualTo(expectedCreationTime);
		assertThat(gemfireSession.getLastAccessedTime()).isLessThanOrEqualTo(System.currentTimeMillis());
		assertThat(gemfireSession.getMaxInactiveIntervalInSeconds()).isEqualTo(MAX_INACTIVE_INTERVAL_IN_SECONDS);
		assertThat(gemfireSession.getAttributeNames()).isNotNull();
		assertThat(gemfireSession.getAttributeNames().isEmpty()).isTrue();

		verify(mockSession, times(1)).getId();
		verify(mockSession, times(1)).getCreationTime();
		verify(mockSession, times(1)).getLastAccessedTime();
		verify(mockSession, times(1)).getMaxInactiveIntervalInSeconds();
		verify(mockSession, times(1)).getAttributeNames();
		verify(mockSession, never()).getAttribute(anyString());
	}

	@Test
	public void setGetAndRemoveAttribute() {
		GemFireSession session = GemFireSession.create(60);

		assertThat(session).isNotNull();
		assertThat(session.getMaxInactiveIntervalInSeconds()).isEqualTo(60);
		assertThat(session.getAttributeNames().isEmpty()).isTrue();

		session.setAttribute("attrOne", "testOne");

		assertThat(session.getAttributeNames()).isEqualTo(asSet("attrOne"));
		assertThat(String.valueOf(session.getAttribute("attrOne"))).isEqualTo("testOne");
		assertThat(session.getAttribute("attrTwo")).isNull();

		session.setAttribute("attrTwo", "testTwo");

		assertThat(session.getAttributeNames()).isEqualTo(asSet("attrOne", "attrTwo"));
		assertThat(String.valueOf(session.getAttribute("attrOne"))).isEqualTo("testOne");
		assertThat(String.valueOf(session.getAttribute("attrTwo"))).isEqualTo("testTwo");

		session.setAttribute("attrTwo", null);

		assertThat(session.getAttributeNames()).isEqualTo(asSet("attrOne"));
		assertThat(String.valueOf(session.getAttribute("attrOne"))).isEqualTo("testOne");
		assertThat(session.getAttribute("attrTwo")).isNull();

		session.removeAttribute("attrOne");

		assertThat(session.getAttribute("attrOne")).isNull();
		assertThat(session.getAttribute("attrTwo")).isNull();
		assertThat(session.getAttributeNames().isEmpty()).isTrue();
	}

	@Test
	public void isExpiredIsFalseWhenMaxInactiveIntervalIsNegative() {
		final int expectedMaxInactiveIntervalInSeconds = -1;

		GemFireSession session = GemFireSession.create(expectedMaxInactiveIntervalInSeconds);

		assertThat(session).isNotNull();
		assertThat(session.getMaxInactiveIntervalInSeconds()).isEqualTo(expectedMaxInactiveIntervalInSeconds);
		assertThat(session.isExpired()).isFalse();
	}

	@Test
	public void isExpiredIsFalseWhenSessionIsActive() {
		final int expectedMaxInactiveIntervalInSeconds = (int) TimeUnit.HOURS.toSeconds(2);

		GemFireSession session = GemFireSession.create(expectedMaxInactiveIntervalInSeconds);

		assertThat(session).isNotNull();
		assertThat(session.getMaxInactiveIntervalInSeconds()).isEqualTo(expectedMaxInactiveIntervalInSeconds);

		final long now = System.currentTimeMillis();

		session.setLastAccessedTime(now);

		assertThat(session.getLastAccessedTime()).isEqualTo(now);
		assertThat(session.isExpired()).isFalse();
	}

	@Test
	public void isExpiredIsTrueWhenSessionIsInactive() {
		final int expectedMaxInactiveIntervalInSeconds = 60;

		GemFireSession session = GemFireSession.create(expectedMaxInactiveIntervalInSeconds);

		assertThat(session).isNotNull();
		assertThat(session.getMaxInactiveIntervalInSeconds()).isEqualTo(expectedMaxInactiveIntervalInSeconds);

		final long twoHoursAgo = (System.currentTimeMillis() - TimeUnit.HOURS.toMillis(2));

		session.setLastAccessedTime(twoHoursAgo);

		assertThat(session.getLastAccessedTime()).isEqualTo(twoHoursAgo);
		assertThat(session.isExpired()).isTrue();
	}

	@Test
	public void setAndGetPrincipalName() {
		GemFireSession session = GemFireSession.create(0);

		assertThat(session).isNotNull();
		assertThat(session.getPrincipalName()).isNull();

		session.setPrincipalName("jblum");

		assertThat(session.getPrincipalName()).isEqualTo("jblum");
		assertThat(session.getAttributeNames()).isEqualTo(asSet(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME));
		assertThat(String.valueOf(session.getAttribute(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME))).isEqualTo("jblum");

		session.setAttribute(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME, "rwinch");

		assertThat(session.getAttributeNames()).isEqualTo(asSet(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME));
		assertThat(String.valueOf(session.getAttribute(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME))).isEqualTo("rwinch");
		assertThat(session.getPrincipalName()).isEqualTo("rwinch");

		session.removeAttribute(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME);

		assertThat(session.getPrincipalName()).isNull();
	}

	@Test
	public void sessionToData() throws Exception {
		@SuppressWarnings("serial")
		GemFireSession session = new GemFireSession("1") {
			@Override void writeObject(Object obj, DataOutput out) throws IOException {
				assertThat(obj).isInstanceOf(GemFireSessionAttributes.class);
				assertThat(out).isNotNull();
			}
		};

		session.setLastAccessedTime(123l);
		session.setMaxInactiveIntervalInSeconds(MAX_INACTIVE_INTERVAL_IN_SECONDS);
		session.setPrincipalName("jblum");

		DataOutput mockDataOutput = mock(DataOutput.class);

		session.toData(mockDataOutput);

		verify(mockDataOutput, times(1)).writeUTF(eq("1"));
		verify(mockDataOutput, times(1)).writeLong(eq(session.getCreationTime()));
		verify(mockDataOutput, times(1)).writeLong(eq(session.getLastAccessedTime()));
		verify(mockDataOutput, times(1)).writeInt(eq(session.getMaxInactiveIntervalInSeconds()));
		verify(mockDataOutput, times(1)).writeInt(eq("jblum".length()));
		verify(mockDataOutput, times(1)).writeUTF(eq(session.getPrincipalName()));
	}

	@Test
	public void sessionFromData() throws Exception {
		final long expectedCreationTime = 1l;
		final long expectedLastAccessedTime = 2l;

		final int expectedMaxInactiveIntervalInSeconds = (int) TimeUnit.HOURS.toSeconds(6);

		final String expectedPrincipalName = "jblum";

		DataInput mockDataInput = mock(DataInput.class);

		when(mockDataInput.readUTF()).thenReturn("2").thenReturn(expectedPrincipalName);
		when(mockDataInput.readLong()).thenReturn(expectedCreationTime).thenReturn(expectedLastAccessedTime);
		when(mockDataInput.readInt()).thenReturn(expectedMaxInactiveIntervalInSeconds);

		@SuppressWarnings("serial")
		GemFireSession session = new GemFireSession("1") {
			@Override @SuppressWarnings("unchecked")
			<T> T readObject(DataInput in) throws ClassNotFoundException, IOException {
				assertThat(in).isNotNull();

				GemFireSessionAttributes sessionAttributes = new GemFireSessionAttributes();

				sessionAttributes.setAttribute("attrOne", "testOne");
				sessionAttributes.setAttribute("attrTwo", "testTwo");

				return (T) sessionAttributes;
			}
		};

		session.fromData(mockDataInput);

		Set<String> expectedAttributeNames = asSet("attrOne", "attrTwo", FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME);

		assertThat(session.getId()).isEqualTo("2");
		assertThat(session.getCreationTime()).isEqualTo(expectedCreationTime);
		assertThat(session.getLastAccessedTime()).isEqualTo(expectedLastAccessedTime);
		assertThat(session.getMaxInactiveIntervalInSeconds()).isEqualTo(expectedMaxInactiveIntervalInSeconds);
		assertThat(session.getPrincipalName()).isEqualTo(expectedPrincipalName);
		assertThat(session.getAttributeNames().size()).isEqualTo(3);
		assertThat(session.getAttributeNames().containsAll(expectedAttributeNames)).isTrue();
		assertThat(String.valueOf(session.getAttribute("attrOne"))).isEqualTo("testOne");
		assertThat(String.valueOf(session.getAttribute("attrTwo"))).isEqualTo("testTwo");
		assertThat(String.valueOf(session.getAttribute(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME)))
			.isEqualTo(expectedPrincipalName);

		verify(mockDataInput, times(2)).readUTF();
		verify(mockDataInput, times(2)).readLong();
		verify(mockDataInput, times(2)).readInt();
	}

	@Test
	public void sessionToDataThenFromDataWhenPrincipalNameIsNullGetsHandledProperly()
			throws ClassNotFoundException, IOException {

		final long beforeOrAtCreationTime = System.currentTimeMillis();

		@SuppressWarnings("serial")
		GemFireSession expectedSession = new GemFireSession("123") {
			@Override void writeObject(Object obj, DataOutput out) throws IOException {
				assertThat(obj).isInstanceOf(GemFireSessionAttributes.class);
				assertThat(out).isNotNull();
			}
		};

		assertThat(expectedSession.getId()).isEqualTo("123");
		assertThat(expectedSession.getCreationTime()).isGreaterThanOrEqualTo(beforeOrAtCreationTime);
		assertThat(expectedSession.getLastAccessedTime()).isGreaterThanOrEqualTo(expectedSession.getCreationTime());
		assertThat(expectedSession.getMaxInactiveIntervalInSeconds()).isEqualTo(0);
		assertThat(expectedSession.getPrincipalName()).isNull();

		ByteArrayOutputStream outBytes = new ByteArrayOutputStream();

		expectedSession.toData(new DataOutputStream(outBytes));

		@SuppressWarnings("serial")
		GemFireSession deserializedSession = new GemFireSession("0") {
			@SuppressWarnings("unchecked")
			@Override <T> T readObject(DataInput in) throws ClassNotFoundException, IOException {
				return (T) new GemFireSessionAttributes();
			}
		};

		deserializedSession.fromData(new DataInputStream(new ByteArrayInputStream(outBytes.toByteArray())));

		assertThat(deserializedSession).isEqualTo(expectedSession);
		assertThat(deserializedSession.getCreationTime()).isEqualTo(expectedSession.getCreationTime());
		assertThat(deserializedSession.getLastAccessedTime()).isEqualTo(expectedSession.getLastAccessedTime());
		assertThat(deserializedSession.getMaxInactiveIntervalInSeconds()).isEqualTo(
			expectedSession.getMaxInactiveIntervalInSeconds());
		assertThat(deserializedSession.getPrincipalName()).isNull();
	}

	@Test
	public void hasDeltaWhenNoSessionChangesIsFalse() {
		assertThat(new GemFireSession().hasDelta()).isFalse();
	}

	@Test
	public void hasDeltaWhenSessionAttributesChangeIsTrue() {
		GemFireSession session = new GemFireSession();

		assertThat(session.hasDelta()).isFalse();

		session.setAttribute("attrOne", "test");

		assertThat(session.hasDelta()).isTrue();
	}

	@Test
	public void hasDeltaWhenSessionLastAccessedTimeIsUpdatedIsTrue() {
		final long expectedLastAccessTime = 1l;

		GemFireSession session = new GemFireSession();

		assertThat(session.getLastAccessedTime()).isNotEqualTo(expectedLastAccessTime);
		assertThat(session.hasDelta()).isFalse();

		session.setLastAccessedTime(expectedLastAccessTime);

		assertThat(session.getLastAccessedTime()).isEqualTo(expectedLastAccessTime);
		assertThat(session.hasDelta()).isTrue();

		session.setLastAccessedTime(expectedLastAccessTime);

		assertThat(session.getLastAccessedTime()).isEqualTo(expectedLastAccessTime);
		assertThat(session.hasDelta()).isTrue();
	}

	@Test
	public void hasDeltaWhenSessionMaxInactiveIntervalInSecondsIsUpdatedIsTrue() {
		final int expectedMaxInactiveIntervalInSeconds = 300;

		GemFireSession session = new GemFireSession();

		assertThat(session.getMaxInactiveIntervalInSeconds()).isNotEqualTo(expectedMaxInactiveIntervalInSeconds);
		assertThat(session.hasDelta()).isFalse();

		session.setMaxInactiveIntervalInSeconds(expectedMaxInactiveIntervalInSeconds);

		assertThat(session.getMaxInactiveIntervalInSeconds()).isEqualTo(expectedMaxInactiveIntervalInSeconds);
		assertThat(session.hasDelta()).isTrue();

		session.setMaxInactiveIntervalInSeconds(expectedMaxInactiveIntervalInSeconds);

		assertThat(session.getMaxInactiveIntervalInSeconds()).isEqualTo(expectedMaxInactiveIntervalInSeconds);
		assertThat(session.hasDelta()).isTrue();
	}

	@Test
	public void sessionToDelta() throws Exception {
		final DataOutput mockDataOutput = mock(DataOutput.class);

		@SuppressWarnings("serial")
		GemFireSession session = new GemFireSession() {
			@Override void writeObject(Object obj, DataOutput out) throws IOException {
				assertThat(String.valueOf(obj)).isEqualTo("test");
				assertThat(out).isSameAs(mockDataOutput);
			}
		};

		session.setLastAccessedTime(1l);
		session.setMaxInactiveIntervalInSeconds(300);
		session.setAttribute("attrOne", "test");

		assertThat(session.hasDelta()).isTrue();

		session.toDelta(mockDataOutput);

		assertThat(session.hasDelta()).isFalse();

		verify(mockDataOutput, times(1)).writeLong(eq(1l));
		verify(mockDataOutput, times(1)).writeInt(eq(300));
		verify(mockDataOutput, times(1)).writeInt(eq(1));
		verify(mockDataOutput, times(1)).writeUTF(eq("attrOne"));
	}

	@Test
	public void sessionFromDelta() throws Exception {
		final DataInput mockDataInput = mock(DataInput.class);

		when(mockDataInput.readLong()).thenReturn(1l);
		when(mockDataInput.readInt()).thenReturn(600).thenReturn(0);

		@SuppressWarnings("serial")
		GemFireSession session = new GemFireSession() {
			@Override @SuppressWarnings("unchecked")
			<T> T readObject(DataInput in) throws ClassNotFoundException, IOException {
				assertThat(in).isSameAs(mockDataInput);
				return (T) "test";
			}
		};

		session.fromDelta(mockDataInput);

		assertThat(session.hasDelta()).isFalse();
		assertThat(session.getLastAccessedTime()).isEqualTo(1l);
		assertThat(session.getMaxInactiveIntervalInSeconds()).isEqualTo(600);
		assertThat(session.getAttributeNames().isEmpty()).isTrue();

		verify(mockDataInput, times(1)).readLong();
		verify(mockDataInput, times(2)).readInt();
		verify(mockDataInput, never()).readUTF();
	}

	@Test
	public void sessionComparisons() {
		final long twoHoursAgo = (System.currentTimeMillis() - TimeUnit.HOURS.toMillis(2));

		GemFireSession sessionOne = new GemFireSession(mockSession("1", twoHoursAgo, MAX_INACTIVE_INTERVAL_IN_SECONDS));
		GemFireSession sessionTwo = new GemFireSession("2");

		assertThat(sessionOne.getCreationTime()).isEqualTo(twoHoursAgo);
		assertThat(sessionTwo.getCreationTime()).isGreaterThan(twoHoursAgo);
		assertThat(sessionOne.compareTo(sessionTwo)).isLessThan(0);
		assertThat(sessionOne.compareTo(sessionOne)).isEqualTo(0);
		assertThat(sessionTwo.compareTo(sessionOne)).isGreaterThan(0);
	}

	@Test
	public void sessionEqualsDifferentSessionBasedOnId() {
		GemFireSession sessionOne = new GemFireSession("1");

		sessionOne.setLastAccessedTime(12345l);
		sessionOne.setMaxInactiveIntervalInSeconds(120);
		sessionOne.setPrincipalName("jblum");

		GemFireSession sessionTwo = new GemFireSession("1");

		sessionTwo.setLastAccessedTime(67890l);
		sessionTwo.setMaxInactiveIntervalInSeconds(300);
		sessionTwo.setPrincipalName("rwinch");

		assertThat(sessionOne.getId().equals(sessionTwo.getId())).isTrue();
		assertThat(sessionOne.getLastAccessedTime() == sessionTwo.getLastAccessedTime()).isFalse();
		assertThat(sessionOne.getMaxInactiveIntervalInSeconds() == sessionTwo.getMaxInactiveIntervalInSeconds()).isFalse();
		assertThat(sessionOne.getPrincipalName().equals(sessionTwo.getPrincipalName())).isFalse();
		assertThat(sessionOne.equals(sessionTwo)).isTrue();
	}

	@Test
	public void sessionHashCodeIsNotEqualToStringIdHashCode() {
		GemFireSession session = new GemFireSession("1");

		assertThat(session.getId()).isEqualTo("1");
		assertThat(session.hashCode()).isNotEqualTo("1".hashCode());
	}

	@Test
	public void sessionAttributesFromSession() {
		Session mockSession = mock(Session.class);

		when(mockSession.getAttributeNames()).thenReturn(asSet("attrOne", "attrTwo"));
		when(mockSession.getAttribute(eq("attrOne"))).thenReturn("testOne");
		when(mockSession.getAttribute(eq("attrTwo"))).thenReturn("testTwo");

		GemFireSessionAttributes sessionAttributes = new GemFireSessionAttributes();

		assertThat(sessionAttributes.getAttributeNames().isEmpty()).isTrue();

		sessionAttributes.from(mockSession);

		assertThat(sessionAttributes.getAttributeNames().size()).isEqualTo(2);
		assertThat(sessionAttributes.getAttributeNames().containsAll(asSet("attrOne", "attrTwo"))).isTrue();
		assertThat(String.valueOf(sessionAttributes.getAttribute("attrOne"))).isEqualTo("testOne");
		assertThat(String.valueOf(sessionAttributes.getAttribute("attrTwo"))).isEqualTo("testTwo");

		verify(mockSession, times(1)).getAttributeNames();
		verify(mockSession, times(1)).getAttribute(eq("attrOne"));
		verify(mockSession, times(1)).getAttribute(eq("attrTwo"));
	}

	@Test
	public void sessionAttributesFromSessionAttributes() {
		GemFireSessionAttributes source = new GemFireSessionAttributes();

		source.setAttribute("attrOne", "testOne");
		source.setAttribute("attrTwo", "testTwo");

		GemFireSessionAttributes target = new GemFireSessionAttributes();

		assertThat(target.getAttributeNames().isEmpty()).isTrue();

		target.from(source);

		assertThat(target.getAttributeNames().size()).isEqualTo(2);
		assertThat(target.getAttributeNames().containsAll(asSet("attrOne", "attrTwo"))).isTrue();
		assertThat(String.valueOf(target.getAttribute("attrOne"))).isEqualTo("testOne");
		assertThat(String.valueOf(target.getAttribute("attrTwo"))).isEqualTo("testTwo");
	}

	@Test
	public void sessionAttributesToData() throws Exception {
		final DataOutput mockDataOutput = mock(DataOutput.class);

		@SuppressWarnings("serial")
		GemFireSessionAttributes sessionAttributes = new GemFireSessionAttributes() {
			private int count = 0;
			@Override void writeObject(Object obj, DataOutput out) throws IOException {
				assertThat(Arrays.asList("testOne", "testTwo").get(count++)).isEqualTo(String.valueOf(obj));
				assertThat(out).isSameAs(mockDataOutput);
			}
		};

		sessionAttributes.setAttribute("attrOne", "testOne");
		sessionAttributes.setAttribute("attrTwo", "testTwo");

		sessionAttributes.toData(mockDataOutput);

		verify(mockDataOutput, times(1)).writeInt(eq(2));
		verify(mockDataOutput, times(1)).writeUTF(eq("attrOne"));
		verify(mockDataOutput, times(1)).writeUTF(eq("attrTwo"));
	}

	@Test
	public void sessionAttributesFromData() throws Exception {
		final DataInput mockDataInput = mock(DataInput.class);

		when(mockDataInput.readInt()).thenReturn(2);
		when(mockDataInput.readUTF()).thenReturn("attrOne").thenReturn("attrTwo");

		@SuppressWarnings("serial")
		GemFireSessionAttributes sessionAttributes = new GemFireSessionAttributes() {
			private int count = 0;
			@Override @SuppressWarnings("unchecked")
			<T> T readObject(DataInput in) throws ClassNotFoundException, IOException {
				assertThat(in).isSameAs(mockDataInput);
				return (T) Arrays.asList("testOne", "testTwo").get(count++);
			}
		};

		assertThat(sessionAttributes.getAttributeNames().isEmpty()).isTrue();

		sessionAttributes.fromData(mockDataInput);

		assertThat(sessionAttributes.getAttributeNames().size()).isEqualTo(2);
		assertThat(sessionAttributes.getAttributeNames().containsAll(asSet("attrOne", "attrTwo"))).isTrue();
		assertThat(String.valueOf(sessionAttributes.getAttribute("attrOne"))).isEqualTo("testOne");
		assertThat(String.valueOf(sessionAttributes.getAttribute("attrTwo"))).isEqualTo("testTwo");

		verify(mockDataInput, times(1)).readInt();
		verify(mockDataInput, times(2)).readUTF();
	}

	@Test
	public void sessionAttributesHasDeltaIsFalse() {
		assertThat(new GemFireSessionAttributes().hasDelta()).isFalse();
	}

	@Test
	public void sessionAttributesHasDeltaIsTrue() {
		GemFireSessionAttributes sessionAttributes = new GemFireSessionAttributes();

		assertThat(sessionAttributes.hasDelta()).isFalse();

		sessionAttributes.setAttribute("attrOne", "testOne");

		assertThat(String.valueOf(sessionAttributes.getAttribute("attrOne"))).isEqualTo("testOne");
		assertThat(sessionAttributes.hasDelta()).isTrue();
	}

	@Test
	public void sessionAttributesToDelta() throws Exception {
		final DataOutput mockDataOutput = mock(DataOutput.class);

		@SuppressWarnings("serial")
		GemFireSessionAttributes sessionAttributes = new GemFireSessionAttributes() {
			private int count = 0;
			@Override void writeObject(Object obj, DataOutput out) throws IOException {
				assertThat(Arrays.asList("testOne", "testTwo", "testThree").get(count++)).isEqualTo(String.valueOf(obj));
				assertThat(out).isSameAs(mockDataOutput);
			}
		};

		sessionAttributes.setAttribute("attrOne", "testOne");
		sessionAttributes.setAttribute("attrTwo", "testTwo");

		assertThat(sessionAttributes.hasDelta()).isTrue();

		sessionAttributes.toDelta(mockDataOutput);

		assertThat(sessionAttributes.hasDelta()).isFalse();

		verify(mockDataOutput, times(1)).writeInt(eq(2));
		verify(mockDataOutput, times(1)).writeUTF("attrOne");
		verify(mockDataOutput, times(1)).writeUTF("attrTwo");
		reset(mockDataOutput);

		sessionAttributes.setAttribute("attrOne", "testOne");

		assertThat(sessionAttributes.hasDelta()).isFalse();

		sessionAttributes.toDelta(mockDataOutput);

		verify(mockDataOutput, times(1)).writeInt(eq(0));
		verify(mockDataOutput, never()).writeUTF(any(String.class));
		reset(mockDataOutput);

		sessionAttributes.setAttribute("attrTwo", "testThree");

		assertThat(sessionAttributes.hasDelta()).isTrue();

		sessionAttributes.toDelta(mockDataOutput);

		verify(mockDataOutput, times(1)).writeInt(eq(1));
		verify(mockDataOutput, times(1)).writeUTF(eq("attrTwo"));
	}

	@Test
	public void sessionAttributesFromDelta() throws Exception {
		final DataInput mockDataInput = mock(DataInput.class);

		when(mockDataInput.readInt()).thenReturn(2);
		when(mockDataInput.readUTF()).thenReturn("attrOne").thenReturn("attrTwo");

		@SuppressWarnings("serial")
		GemFireSessionAttributes sessionAttributes = new GemFireSessionAttributes() {
			private int count = 0;
			@Override @SuppressWarnings("unchecked")
			<T> T readObject(DataInput in) throws ClassNotFoundException, IOException {
				assertThat(in).isSameAs(mockDataInput);
				return (T) Arrays.asList("testOne", "testTwo", "testThree").get(count++);
			}
		};

		sessionAttributes.setAttribute("attrOne", "one");
		sessionAttributes.setAttribute("attrTwo", "two");

		assertThat(sessionAttributes.getAttributeNames().size()).isEqualTo(2);
		assertThat(sessionAttributes.getAttributeNames().containsAll(asSet("attrOne", "attrTwo"))).isTrue();
		assertThat(String.valueOf(sessionAttributes.getAttribute("attrOne"))).isEqualTo("one");
		assertThat(String.valueOf(sessionAttributes.getAttribute("attrTwo"))).isEqualTo("two");
		assertThat(sessionAttributes.hasDelta()).isTrue();

		sessionAttributes.fromDelta(mockDataInput);

		assertThat(sessionAttributes.getAttributeNames().size()).isEqualTo(2);
		assertThat(sessionAttributes.getAttributeNames().containsAll(asSet("attrOne", "attrTwo"))).isTrue();
		assertThat(String.valueOf(sessionAttributes.getAttribute("attrOne"))).isEqualTo("testOne");
		assertThat(String.valueOf(sessionAttributes.getAttribute("attrTwo"))).isEqualTo("testTwo");
		assertThat(sessionAttributes.hasDelta()).isFalse();

		verify(mockDataInput, times(1)).readInt();
		verify(mockDataInput, times(2)).readUTF();
		reset(mockDataInput);

		when(mockDataInput.readInt()).thenReturn(1);
		when(mockDataInput.readUTF()).thenReturn("attrTwo");

		sessionAttributes.setAttribute("attrOne", "one");
		sessionAttributes.setAttribute("attrTwo", "two");

		assertThat(sessionAttributes.getAttributeNames().size()).isEqualTo(2);
		assertThat(sessionAttributes.getAttributeNames().containsAll(asSet("attrOne", "attrTwo"))).isTrue();
		assertThat(String.valueOf(sessionAttributes.getAttribute("attrOne"))).isEqualTo("one");
		assertThat(String.valueOf(sessionAttributes.getAttribute("attrTwo"))).isEqualTo("two");
		assertThat(sessionAttributes.hasDelta()).isTrue();

		sessionAttributes.fromDelta(mockDataInput);

		assertThat(sessionAttributes.getAttributeNames().size()).isEqualTo(2);
		assertThat(sessionAttributes.getAttributeNames().containsAll(asSet("attrOne", "attrTwo"))).isTrue();
		assertThat(String.valueOf(sessionAttributes.getAttribute("attrOne"))).isEqualTo("one");
		assertThat(String.valueOf(sessionAttributes.getAttribute("attrTwo"))).isEqualTo("testThree");
		assertThat(sessionAttributes.hasDelta()).isTrue();

		verify(mockDataInput, times(1)).readInt();
		verify(mockDataInput, times(1)).readUTF();
	}

	@Test
	public void sessionAttributesEntrySetIteratesAttributeNameValues() {
		GemFireSessionAttributes sessionAttributes = new GemFireSessionAttributes();

		sessionAttributes.setAttribute("keyOne", "valueOne");
		sessionAttributes.setAttribute("keyTwo", "valueTwo");

		Set<Map.Entry<String, Object>> sessionAttributeEntries = sessionAttributes.entrySet();

		assertThat(sessionAttributeEntries).isNotNull();
		assertThat(sessionAttributeEntries.size()).isEqualTo(2);

		Set<String> expectedNames = asSet("keyOne", "keyTwo");
		Set<?> expectedValues = asSet("valueOne", "valueTwo");

		for (Map.Entry<String, Object> entry : sessionAttributeEntries) {
			expectedNames.remove(entry.getKey());
			expectedValues.remove(entry.getValue());
		}

		assertThat(expectedNames.isEmpty()).isTrue();
		assertThat(expectedValues.isEmpty()).isTrue();

		sessionAttributes.setAttribute("keyThree", "valueThree");

		assertThat(sessionAttributeEntries.size()).isEqualTo(3);

		expectedNames = asSet("keyOne", "keyTwo");
		expectedValues = asSet("valueOne", "valueTwo");

		for (Map.Entry<String, Object> entry : sessionAttributeEntries) {
			expectedNames.remove(entry.getKey());
			expectedValues.remove(entry.getValue());
		}

		assertThat(expectedNames.isEmpty()).isTrue();
		assertThat(expectedValues.isEmpty()).isTrue();

		sessionAttributes.removeAttribute("keyOne");
		sessionAttributes.removeAttribute("keyTwo");

		assertThat(sessionAttributeEntries.size()).isEqualTo(1);

		Map.Entry<String, ?> entry = sessionAttributeEntries.iterator().next();

		assertThat(entry.getKey()).isEqualTo("keyThree");
		assertThat(entry.getValue()).isEqualTo("valueThree");
	}

	@Test
	public void sessionWithAttributesAreThreadSafe() throws Throwable {
		TestFramework.runOnce(new ThreadSafeSessionTest());
	}

	protected static final class ThreadSafeSessionTest extends MultithreadedTestCase {

		private final long beforeOrAtCreationTime = System.currentTimeMillis();

		private GemFireSession session;

		private volatile long expectedCreationTime;

		@Override
		public void initialize() {
			session = new GemFireSession("1");

			assertThat(session).isNotNull();
			assertThat(session.getId()).isEqualTo("1");
			assertThat(session.getCreationTime()).isGreaterThanOrEqualTo(beforeOrAtCreationTime);
			assertThat(session.getLastAccessedTime()).isEqualTo(session.getCreationTime());
			assertThat(session.getMaxInactiveIntervalInSeconds()).isEqualTo(0);
			assertThat(session.getPrincipalName()).isNull();
			assertThat(session.getAttributeNames().isEmpty()).isTrue();

			expectedCreationTime = session.getCreationTime();

			session.setLastAccessedTime(0l);
			session.setMaxInactiveIntervalInSeconds(60);
			session.setPrincipalName("jblum");
		}

		public void thread1() {
			assertTick(0);

			Thread.currentThread().setName("HTTP Request Processing Thread 1");

			assertThat(session).isNotNull();
			assertThat(session.getId()).isEqualTo("1");
			assertThat(session.getCreationTime()).isEqualTo(expectedCreationTime);
			assertThat(session.getLastAccessedTime()).isEqualTo(0l);
			assertThat(session.getMaxInactiveIntervalInSeconds()).isEqualTo(60);
			assertThat(session.getPrincipalName()).isEqualTo("jblum");
			assertThat(session.getAttributeNames().size()).isEqualTo(1);
			assertThat(String.valueOf(session.getAttribute(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME))).isEqualTo("jblum");

			session.setAttribute("tennis", "ping");
			session.setAttribute("junk", "test");
			session.setLastAccessedTime(1l);
			session.setMaxInactiveIntervalInSeconds(120);
			session.setPrincipalName("rwinch");

			waitForTick(2);

			assertThat(session).isNotNull();
			assertThat(session.getId()).isEqualTo("1");
			assertThat(session.getCreationTime()).isEqualTo(expectedCreationTime);
			assertThat(session.getLastAccessedTime()).isEqualTo(2l);
			assertThat(session.getMaxInactiveIntervalInSeconds()).isEqualTo(180);
			assertThat(session.getPrincipalName()).isEqualTo("ogierke");
			assertThat(session.getAttributeNames().size()).isEqualTo(3);
			assertThat(session.getAttributeNames().containsAll(asSet("tennis", "greeting"))).isTrue();
			assertThat(session.getAttributeNames().contains("junk")).isFalse();
			assertThat(session.getAttribute("junk")).isNull();
			assertThat(String.valueOf(session.getAttribute("tennis"))).isEqualTo("pong");
			assertThat(String.valueOf(session.getAttribute("greeting"))).isEqualTo("hello");
		}

		public void thread2() {
			assertTick(0);

			Thread.currentThread().setName("HTTP Request Processing Thread 2");

			waitForTick(1);
			assertTick(1);

			assertThat(session).isNotNull();
			assertThat(session.getId()).isEqualTo("1");
			assertThat(session.getCreationTime()).isEqualTo(expectedCreationTime);
			assertThat(session.getLastAccessedTime()).isEqualTo(1l);
			assertThat(session.getMaxInactiveIntervalInSeconds()).isEqualTo(120);
			assertThat(session.getPrincipalName()).isEqualTo("rwinch");
			assertThat(session.getAttributeNames().size()).isEqualTo(3);
			assertThat(session.getAttributeNames().containsAll(asSet("tennis", "junk"))).isTrue();
			assertThat(String.valueOf(session.getAttribute("junk"))).isEqualTo("test");
			assertThat(String.valueOf(session.getAttribute("tennis"))).isEqualTo("ping");

			session.setAttribute("tennis", "pong");
			session.setAttribute("greeting", "hello");
			session.removeAttribute("junk");
			session.setLastAccessedTime(2l);
			session.setMaxInactiveIntervalInSeconds(180);
			session.setPrincipalName("ogierke");
		}

		@Override
		public void finish() {
			session = null;
		}
	}

	protected static class TestGemFireOperationsSessionRepository extends AbstractGemFireOperationsSessionRepository {

		protected TestGemFireOperationsSessionRepository(GemfireOperations gemfireOperations) {
			super(gemfireOperations);
		}

		public Map<String, ExpiringSession> findByIndexNameAndIndexValue(String indexName, String indexValue) {
			throw new UnsupportedOperationException("not implemented");
		}

		public ExpiringSession createSession() {
			throw new UnsupportedOperationException("not implemented");
		}

		public ExpiringSession getSession(String id) {
			throw new UnsupportedOperationException("not implemented");
		}

		public void save(ExpiringSession session) {
			throw new UnsupportedOperationException("not implemented");
		}

		public void delete(String id) {
			throw new UnsupportedOperationException("not implemented");
		}
	}

}
