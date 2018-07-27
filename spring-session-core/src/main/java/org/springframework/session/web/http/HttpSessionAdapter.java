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

package org.springframework.session.web.http;

import java.time.Duration;
import java.util.Collections;
import java.util.Enumeration;
import java.util.NoSuchElementException;
import java.util.Set;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpSessionBindingEvent;
import javax.servlet.http.HttpSessionBindingListener;
import javax.servlet.http.HttpSessionContext;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.session.Session;

/**
 * Adapts Spring Session's {@link Session} to an {@link HttpSession}.
 *
 * @param <S> the {@link Session} type
 * @author Rob Winch
 * @author Vedran Pavic
 * @since 1.1
 */
@SuppressWarnings("deprecation")
class HttpSessionAdapter<S extends Session> implements HttpSession {

	private static final Log logger = LogFactory.getLog(HttpSessionAdapter.class);

	private S session;

	private final ServletContext servletContext;

	private boolean invalidated;

	private boolean old;

	HttpSessionAdapter(S session, ServletContext servletContext) {
		if (session == null) {
			throw new IllegalArgumentException("session cannot be null");
		}
		if (servletContext == null) {
			throw new IllegalArgumentException("servletContext cannot be null");
		}
		this.session = session;
		this.servletContext = servletContext;
	}

	public void setSession(S session) {
		this.session = session;
	}

	public S getSession() {
		return this.session;
	}

	@Override
	public long getCreationTime() {
		checkState();
		return this.session.getCreationTime().toEpochMilli();
	}

	@Override
	public String getId() {
		return this.session.getId();
	}

	@Override
	public long getLastAccessedTime() {
		checkState();
		return this.session.getLastAccessedTime().toEpochMilli();
	}

	@Override
	public ServletContext getServletContext() {
		return this.servletContext;
	}

	@Override
	public void setMaxInactiveInterval(int interval) {
		this.session.setMaxInactiveInterval(Duration.ofSeconds(interval));
	}

	@Override
	public int getMaxInactiveInterval() {
		return (int) this.session.getMaxInactiveInterval().getSeconds();
	}

	@Override
	public HttpSessionContext getSessionContext() {
		return NOOP_SESSION_CONTEXT;
	}

	@Override
	public Object getAttribute(String name) {
		checkState();
		return this.session.getAttribute(name);
	}

	@Override
	public Object getValue(String name) {
		return getAttribute(name);
	}

	@Override
	public Enumeration<String> getAttributeNames() {
		checkState();
		return Collections.enumeration(this.session.getAttributeNames());
	}

	@Override
	public String[] getValueNames() {
		checkState();
		Set<String> attrs = this.session.getAttributeNames();
		return attrs.toArray(new String[0]);
	}

	@Override
	public void setAttribute(String name, Object value) {
		checkState();
		Object oldValue = this.session.getAttribute(name);
		this.session.setAttribute(name, value);
		if (value != oldValue) {
			if (oldValue instanceof HttpSessionBindingListener) {
				try {
					((HttpSessionBindingListener) oldValue).valueUnbound(
							new HttpSessionBindingEvent(this, name, oldValue));
				}
				catch (Throwable th) {
					logger.error("Error invoking session binding event listener", th);
				}
			}
			if (value instanceof HttpSessionBindingListener) {
				try {
					((HttpSessionBindingListener) value)
							.valueBound(new HttpSessionBindingEvent(this, name, value));
				}
				catch (Throwable th) {
					logger.error("Error invoking session binding event listener", th);
				}
			}
		}
	}

	@Override
	public void putValue(String name, Object value) {
		setAttribute(name, value);
	}

	@Override
	public void removeAttribute(String name) {
		checkState();
		Object oldValue = this.session.getAttribute(name);
		this.session.removeAttribute(name);
		if (oldValue instanceof HttpSessionBindingListener) {
			try {
				((HttpSessionBindingListener) oldValue)
						.valueUnbound(new HttpSessionBindingEvent(this, name, oldValue));
			}
			catch (Throwable th) {
				logger.error("Error invoking session binding event listener", th);
			}
		}
	}

	@Override
	public void removeValue(String name) {
		removeAttribute(name);
	}

	@Override
	public void invalidate() {
		checkState();
		this.invalidated = true;
	}

	public void setNew(boolean isNew) {
		this.old = !isNew;
	}

	@Override
	public boolean isNew() {
		checkState();
		return !this.old;
	}

	private void checkState() {
		if (this.invalidated) {
			throw new IllegalStateException(
					"The HttpSession has already be invalidated.");
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
