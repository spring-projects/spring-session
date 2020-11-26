/*
 * Copyright 2014-2019 the original author or authors.
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

package org.springframework.session.web.http;

import java.util.Arrays;
import java.util.Collections;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpSessionEvent;
import javax.servlet.http.HttpSessionListener;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import org.springframework.mock.web.MockServletContext;
import org.springframework.session.MapSession;
import org.springframework.session.Session;
import org.springframework.session.events.SessionCreatedEvent;
import org.springframework.session.events.SessionDestroyedEvent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

/**
 * Tests for {@link SessionEventHttpSessionListenerAdapter}.
 *
 * @author Rob Winch
 * @since 1.1
 */
class SessionEventHttpSessionListenerAdapterTests {

	@Mock
	private HttpSessionListener listener1;

	@Mock
	private HttpSessionListener listener2;

	@Mock
	private ServletContext servletContext;

	@Captor
	private ArgumentCaptor<HttpSessionEvent> sessionEvent;

	private SessionDestroyedEvent destroyed;

	private SessionCreatedEvent created;

	private SessionEventHttpSessionListenerAdapter listener;

	@BeforeEach
	void setup() {
		MockitoAnnotations.initMocks(this);
		this.listener = new SessionEventHttpSessionListenerAdapter(Arrays.asList(this.listener1, this.listener2));
		this.listener.setServletContext(new MockServletContext());

		Session session = new MapSession("1");
		this.destroyed = new SessionDestroyedEvent(this, session);
		this.created = new SessionCreatedEvent(this, session);
	}

	// We want relaxed constructor that will allow for an empty listeners to
	// make configuration easier (i.e. autowire all HttpSessionListeners and might get
	// none)
	@Test
	void constructorEmptyWorks() {
		new SessionEventHttpSessionListenerAdapter(Collections.emptyList());
	}

	/**
	 * Make sure that we short circuit onApplicationEvent as early as possible if no
	 * listeners
	 */
	@Test
	void onApplicationEventEmptyListenersDoesNotUseEvent() {
		this.listener = new SessionEventHttpSessionListenerAdapter(Collections.emptyList());
		this.destroyed = mock(SessionDestroyedEvent.class);

		this.listener.onApplicationEvent(this.destroyed);

		verifyZeroInteractions(this.destroyed, this.listener1, this.listener2);
	}

	@Test
	void onApplicationEventDestroyed() {
		this.listener.onApplicationEvent(this.destroyed);

		verify(this.listener1).sessionDestroyed(this.sessionEvent.capture());
		verify(this.listener2).sessionDestroyed(this.sessionEvent.capture());

		assertThat(this.sessionEvent.getValue().getSession().getId()).isEqualTo(this.destroyed.getSessionId());
	}

	@Test
	void onApplicationEventCreated() {
		this.listener.onApplicationEvent(this.created);

		verify(this.listener1).sessionCreated(this.sessionEvent.capture());
		verify(this.listener2).sessionCreated(this.sessionEvent.capture());

		assertThat(this.sessionEvent.getValue().getSession().getId()).isEqualTo(this.created.getSessionId());
	}

}
