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

import java.util.Collections;
import java.util.Enumeration;
import java.util.NoSuchElementException;
import java.util.Set;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpSessionContext;

import org.springframework.session.ExpiringSession;

/**
 * Adapts Spring Session's {@link ExpiringSession} to an {@link HttpSession}.
 *
 * @author Rob Winch
 * @since 1.1
 */
@SuppressWarnings("deprecation")
class ExpiringSessionHttpSession<S extends ExpiringSession> implements HttpSession {
	private S session;
	private final ServletContext servletContext;
	private boolean invalidated;
	private boolean old;

	public ExpiringSessionHttpSession(S session, ServletContext servletContext) {
		this.session = session;
		this.servletContext = servletContext;
	}

	public void setSession(S session) {
		this.session = session;
	}

	public S getSession() {
		return session;
	}

	public long getCreationTime() {
		checkState();
		return session.getCreationTime();
	}

	public String getId() {
		return session.getId();
	}

	public long getLastAccessedTime() {
		checkState();
		return session.getLastAccessedTime();
	}

	public ServletContext getServletContext() {
		return servletContext;
	}

	public void setMaxInactiveInterval(int interval) {
		session.setMaxInactiveIntervalInSeconds(interval);
	}

	public int getMaxInactiveInterval() {
		return session.getMaxInactiveIntervalInSeconds();
	}

	public HttpSessionContext getSessionContext() {
		return NOOP_SESSION_CONTEXT;
	}

	public Object getAttribute(String name) {
		checkState();
		return session.getAttribute(name);
	}

	public Object getValue(String name) {
		return getAttribute(name);
	}

	public Enumeration<String> getAttributeNames() {
		checkState();
		return Collections.enumeration(session.getAttributeNames());
	}

	public String[] getValueNames() {
		checkState();
		Set<String> attrs = session.getAttributeNames();
		return attrs.toArray(new String[0]);
	}

	public void setAttribute(String name, Object value) {
		checkState();
		session.setAttribute(name, value);
	}

	public void putValue(String name, Object value) {
		setAttribute(name, value);
	}

	public void removeAttribute(String name) {
		checkState();
		session.removeAttribute(name);
	}

	public void removeValue(String name) {
		removeAttribute(name);
	}

	public void invalidate() {
		checkState();
		this.invalidated = true;
	}

	public void setNew(boolean isNew) {
		this.old = !isNew;
	}

	public boolean isNew() {
		checkState();
		return !old;
	}

	private void checkState() {
		if(invalidated) {
			throw new IllegalStateException("The HttpSession has already be invalidated.");
		}
	}

	private static final HttpSessionContext NOOP_SESSION_CONTEXT = new HttpSessionContext() {
		public HttpSession getSession(String sessionId) {
			return null;
		}

		public Enumeration<String> getIds() {
			return EMPTY_ENUMERATION;
		}
	};

	private static final Enumeration<String> EMPTY_ENUMERATION = new Enumeration<String>() {
		public boolean hasMoreElements() {
			return false;
		}

		public String nextElement() {
			throw new NoSuchElementException("a");
		}
	};
}
