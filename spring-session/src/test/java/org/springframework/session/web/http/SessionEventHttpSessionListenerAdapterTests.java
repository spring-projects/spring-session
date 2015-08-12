/*
 * Copyright 2002-2015 the original author or authors.
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
package org.springframework.session.web.http;

import static org.fest.assertions.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.util.Arrays;
import java.util.Collections;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpSessionEvent;
import javax.servlet.http.HttpSessionListener;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.session.MapSession;
import org.springframework.session.Session;
import org.springframework.session.events.SessionCreatedEvent;
import org.springframework.session.events.SessionDestroyedEvent;

/**
 * @author Rob Winch
 * @since 1.1
 */
@RunWith(MockitoJUnitRunner.class)
public class SessionEventHttpSessionListenerAdapterTests {
	@Mock
	HttpSessionListener listener1;
	@Mock
	HttpSessionListener listener2;
	@Mock
	ServletContext servletContext;
	@Captor
	ArgumentCaptor<HttpSessionEvent> sessionEvent;

	SessionDestroyedEvent destroyed;
	SessionCreatedEvent created;
	SessionEventHttpSessionListenerAdapter listener;

	@Before
	public void setup() {
		this.listener = new SessionEventHttpSessionListenerAdapter(Arrays.asList(listener1, listener2));

		Session session = new MapSession();
		destroyed = new SessionDestroyedEvent(this, session);
		created = new SessionCreatedEvent(this, session);
	}

	// We want relaxed constructor that will allow for an empty listeners to
	// make configuration easier (i.e. autowire all HttpSessionListeners and might get none)
	@Test
	public void constructorEmptyWorks() {
		new SessionEventHttpSessionListenerAdapter(Collections.<HttpSessionListener>emptyList());
	}

	/**
	 * Make sure that we short circuit onApplicationEvent as early as possible if no listeners
	 */
	@Test
	public void onApplicationEventEmptyListenersDoesNotUseEvent() {
		listener = new SessionEventHttpSessionListenerAdapter(Collections.<HttpSessionListener>emptyList());
		destroyed = mock(SessionDestroyedEvent.class);

		listener.onApplicationEvent(destroyed);

		verifyZeroInteractions(destroyed, listener1, listener2);
	}

	@Test
	public void onApplicationEventDestroyed() {
		listener.onApplicationEvent(destroyed);

		verify(listener1).sessionDestroyed(sessionEvent.capture());
		verify(listener2).sessionDestroyed(sessionEvent.capture());

		assertThat(sessionEvent.getValue().getSession().getId()).isEqualTo(destroyed.getSessionId());
	}

	@Test
	public void onApplicationEventCreated() {
		listener.onApplicationEvent(created);

		verify(listener1).sessionCreated(sessionEvent.capture());
		verify(listener2).sessionCreated(sessionEvent.capture());

		assertThat(sessionEvent.getValue().getSession().getId()).isEqualTo(created.getSessionId());
	}
}
