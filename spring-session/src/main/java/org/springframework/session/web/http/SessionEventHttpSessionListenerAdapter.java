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

package org.springframework.session.web.http;

import java.util.List;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpSessionEvent;
import javax.servlet.http.HttpSessionListener;

import org.springframework.context.ApplicationListener;
import org.springframework.session.ExpiringSession;
import org.springframework.session.events.AbstractSessionEvent;
import org.springframework.session.events.SessionCreatedEvent;
import org.springframework.session.events.SessionDestroyedEvent;
import org.springframework.web.context.ServletContextAware;

/**
 * Receives {@link SessionDestroyedEvent} and {@link SessionCreatedEvent} and translates
 * them into {@link HttpSessionEvent} and submits the {@link HttpSessionEvent} to every
 * registered {@link HttpSessionListener}.
 *
 * @author Rob Winch
 * @since 1.1
 */
public class SessionEventHttpSessionListenerAdapter
		implements ApplicationListener<AbstractSessionEvent>, ServletContextAware {
	private final List<HttpSessionListener> listeners;

	private ServletContext context;

	public SessionEventHttpSessionListenerAdapter(List<HttpSessionListener> listeners) {
		super();
		this.listeners = listeners;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see org.springframework.context.ApplicationListener#onApplicationEvent(org.
	 * springframework.context.ApplicationEvent)
	 */
	public void onApplicationEvent(AbstractSessionEvent event) {
		if (this.listeners.isEmpty()) {
			return;
		}

		HttpSessionEvent httpSessionEvent = createHttpSessionEvent(event);

		for (HttpSessionListener listener : this.listeners) {
			if (event instanceof SessionDestroyedEvent) {
				listener.sessionDestroyed(httpSessionEvent);
			}
			else if (event instanceof SessionCreatedEvent) {
				listener.sessionCreated(httpSessionEvent);
			}
		}
	}

	private HttpSessionEvent createHttpSessionEvent(AbstractSessionEvent event) {
		ExpiringSession session = event.getSession();
		HttpSession httpSession = new ExpiringSessionHttpSession<ExpiringSession>(session,
				this.context);
		HttpSessionEvent httpSessionEvent = new HttpSessionEvent(httpSession);
		return httpSessionEvent;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see
	 * org.springframework.web.context.ServletContextAware#setServletContext(javax.servlet
	 * .ServletContext)
	 */
	public void setServletContext(ServletContext servletContext) {
		this.context = servletContext;
	}
}
