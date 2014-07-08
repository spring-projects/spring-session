/*
 * Copyright 2002-2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.springframework.session.web;

import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.util.Assert;

import javax.servlet.FilterChain;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.*;
import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.NoSuchElementException;
import java.util.Set;

/**
 * Switches the {@link javax.servlet.http.HttpSession} implementation to be backed by a {@link org.springframework.session.Session}.
 *
 * The {@link SessionRepositoryFilter} wraps the {@link javax.servlet.http.HttpServletRequest} and overrides the methods
 * to get an {@link javax.servlet.http.HttpSession} to be backed by a {@link org.springframework.session.Session} returned
 * by the {@link org.springframework.session.SessionRepository}.
 *
 * The {@link SessionRepositoryFilter} uses a {@link HttpSessionStrategy} (default {@link CookieHttpSessionStrategy}  to
 * bridge logic between an {@link javax.servlet.http.HttpSession} and the {@link org.springframework.session.Session}
 * abstraction. Specifically:
 *
 * <ul>
 *     <li>The session id is looked up using {@link HttpSessionStrategy#getRequestedSessionId(javax.servlet.http.HttpServletRequest)}.
 *     The default is to look in a cookie named SESSION.</li>
 *     <li>The session id of newly created {@link org.springframework.session.Session} is sent to the client using
 *     {@link HttpSessionStrategy#onNewSession(org.springframework.session.Session, javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)} </li>
 *     <li>The client is notified that the session id is no longer valid with {@link HttpSessionStrategy#onInvalidateSession(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)}</li>
 * </ul>
 *
 * @since 1.0
 * @author Rob Winch
 */
public class SessionRepositoryFilter<S extends Session> extends OncePerRequestFilter {
	private final SessionRepository<S> sessionRepository;

	private HttpSessionStrategy httpSessionStrategy = new CookieHttpSessionStrategy();

	/**
	 * Creates a new instance
	 *
	 * @param sessionRepository the <code>SessionRepository</code> to use. Cannot be null.
	 */
	public SessionRepositoryFilter(SessionRepository<S> sessionRepository) {
		Assert.notNull(sessionRepository, "SessionRepository cannot be null");
		this.sessionRepository = sessionRepository;
	}

	/**
	 * Sets the {@link HttpSessionStrategy} to be used. The default is a {@link CookieHttpSessionStrategy}.
	 *
	 * @param httpSessionStrategy the {@link HttpSessionStrategy} to use. Cannot be null.
	 */
	public void setHttpSessionStrategy(HttpSessionStrategy httpSessionStrategy) {
		Assert.notNull(httpSessionStrategy,"httpSessionIdStrategy cannot be null");
		this.httpSessionStrategy = httpSessionStrategy;
	}

	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
		SessionRepositoryRequestWrapper wrappedRequest = new SessionRepositoryRequestWrapper(request, response);
		SessionRepositoryResponseWrapper wrappedResponse = new SessionRepositoryResponseWrapper(wrappedRequest,response);
		try {
			filterChain.doFilter(wrappedRequest, wrappedResponse);
		} finally {
			wrappedRequest.commitSession();
		}
	}

	/**
	 * Allows ensuring that the session is saved if the response is committed.
	 *
	 * @author Rob Winch
	 * @since 1.0
	 */
	private final class SessionRepositoryResponseWrapper extends OnCommittedResponseWrapper {

		private final SessionRepositoryRequestWrapper request;

		/**
		 * @param response the response to be wrapped
		 */
		public SessionRepositoryResponseWrapper(SessionRepositoryRequestWrapper request, HttpServletResponse response) {
			super(response);
			Assert.notNull(request, "SessionRepositoryRequestWrapper cannot be null");
			this.request = request;
		}

		@Override
		protected void onResponseCommitted() {
			request.commitSession();
		}
	}

	/**
	 * A {@link javax.servlet.http.HttpServletRequest} that retrieves the {@link javax.servlet.http.HttpSession} using a
	 * {@link org.springframework.session.SessionRepository}.
	 *
	 * @author Rob Winch
	 * @since 1.0
	 */
	private final class SessionRepositoryRequestWrapper extends HttpServletRequestWrapper {
		private HttpSessionWrapper currentSession;
		private boolean requestedValidSession;
		private final HttpServletResponse response;

		private SessionRepositoryRequestWrapper(HttpServletRequest request, HttpServletResponse response) {
			super(request);
			this.response = response;
		}

		/**
		 * Uses the HttpSessionStrategy to write the session id tot he response and persist the Session.
		 */
		private void commitSession() {
			HttpSessionWrapper wrappedSession = currentSession;
			if(wrappedSession == null) {
				if(isInvalidateClientSession()) {
					httpSessionStrategy.onInvalidateSession(this, response);
				}
			} else {
				S session = wrappedSession.session;
				sessionRepository.save(session);
				httpSessionStrategy.onNewSession(session, this, response);
			}
		}

		private boolean isInvalidateClientSession() {
			return currentSession == null && requestedValidSession;
		}

		@Override
		public HttpSession getSession(boolean create) {
			if(currentSession != null) {
				return currentSession;
			}
			String requestedSessionId = getRequestedSessionId();
			if(requestedSessionId != null) {
				S session = sessionRepository.getSession(requestedSessionId);
				if(session != null) {
					this.requestedValidSession = true;
					session.setLastAccessedTime(System.currentTimeMillis());
					currentSession = new HttpSessionWrapper(session, getServletContext());
					currentSession.setNew(false);
					return currentSession;
				}
			}
			if(!create) {
				return null;
			}
			S session = sessionRepository.createSession();
			currentSession = new HttpSessionWrapper(session, getServletContext());
			return currentSession;
		}

		@Override
		public HttpSession getSession() {
			return getSession(true);
		}

		@Override
		public String getRequestedSessionId() {
			return httpSessionStrategy.getRequestedSessionId(this);
		}

		/**
		 * Allows creating an HttpSession from a Session instance.
		 *
		 * @author Rob Winch
		 * @since 1.0
		 */
		private final class HttpSessionWrapper implements HttpSession {
			private final S session;
			private final ServletContext servletContext;
			private boolean invalidated;
			private boolean old;

			public HttpSessionWrapper(S session, ServletContext servletContext) {
				this.session = session;
				this.servletContext = servletContext;
			}

			void updateLastAccessedTime() {
				checkState();
				session.setLastAccessedTime(System.currentTimeMillis());
			}

			@Override
			public long getCreationTime() {
				checkState();
				return session.getCreationTime();
			}

			@Override
			public String getId() {
				return session.getId();
			}

			@Override
			public long getLastAccessedTime() {
				checkState();
				return session.getLastAccessedTime();
			}

			@Override
			public ServletContext getServletContext() {
				return servletContext;
			}

			@Override
			public void setMaxInactiveInterval(int interval) {
				session.setMaxInactiveInterval(interval);
			}

			@Override
			public int getMaxInactiveInterval() {
				return session.getMaxInactiveInterval();
			}

			@Override
			public HttpSessionContext getSessionContext() {
				return NOOP_SESSION_CONTEXT;
			}

			@Override
			public Object getAttribute(String name) {
				checkState();
				return session.getAttribute(name);
			}

			@Override
			public Object getValue(String name) {
				return getAttribute(name);
			}

			@Override
			public Enumeration<String> getAttributeNames() {
				checkState();
				return Collections.enumeration(session.getAttributeNames());
			}

			@Override
			public String[] getValueNames() {
				checkState();
				Set<String> attrs = session.getAttributeNames();
				return attrs.toArray(new String[0]);
			}

			@Override
			public void setAttribute(String name, Object value) {
				checkState();
				session.setAttribute(name, value);
			}

			@Override
			public void putValue(String name, Object value) {
				setAttribute(name, value);
			}

			@Override
			public void removeAttribute(String name) {
				checkState();
				session.removeAttribute(name);
			}

			@Override
			public void removeValue(String name) {
				removeAttribute(name);
			}

			@Override
			public void invalidate() {
				checkState();
				this.invalidated = true;
				currentSession = null;
				sessionRepository.delete(getId());
			}

			public void setNew(boolean isNew) {
				this.old = !isNew;
			}

			@Override
			public boolean isNew() {
				checkState();
				return !old;
			}

			private void checkState() {
				if(invalidated) {
					throw new IllegalStateException("The HttpSession has already be invalidated.");
				}
			}
		}
	}

	private static final HttpSessionContext NOOP_SESSION_CONTEXT = new HttpSessionContext() {
		@Override
		public HttpSession getSession(String sessionId) {
			return null;
		}

		@Override
		public Enumeration<String> getIds() {
			return EMPTY_ENUMERATION;
		}
	};

	private static final Enumeration<String> EMPTY_ENUMERATION = new Enumeration<String>() {
		@Override
		public boolean hasMoreElements() {
			return false;
		}

		@Override
		public String nextElement() {
			throw new NoSuchElementException("a");
		}
	};
}
