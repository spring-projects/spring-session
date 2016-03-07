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

package org.springframework.session;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 * A {@link Session} implementation that is backed by a {@link java.util.Map}. The
 * defaults for the properties are:
 * </p>
 * <ul>
 * <li>id - a secure random generated id</li>
 * <li>creationTime - the moment the {@link MapSession} was instantiated</li>
 * <li>lastAccessedTime - the moment the {@link MapSession} was instantiated</li>
 * <li>maxInactiveInterval - 30 minutes</li>
 * </ul>
 *
 * <p>
 * This implementation has no synchronization, so it is best to use the copy constructor
 * when working on multiple threads.
 * </p>
 *
 * @author Rob Winch
 * @since 1.0
 */
public final class MapSession implements ExpiringSession, Serializable {
	/**
	 * Default {@link #setMaxInactiveIntervalInSeconds(int)} (30 minutes).
	 */
	public static final int DEFAULT_MAX_INACTIVE_INTERVAL_SECONDS = 1800;

	private String id;
	private Map<String, Object> sessionAttrs = new HashMap<String, Object>();
	private long creationTime = System.currentTimeMillis();
	private long lastAccessedTime = this.creationTime;

	/**
	 * Defaults to 30 minutes.
	 */
	private int maxInactiveInterval = DEFAULT_MAX_INACTIVE_INTERVAL_SECONDS;

	/**
	 * Creates a new instance with a secure randomly generated identifier.
	 */
	public MapSession() {
		this(UUID.randomUUID().toString());
	}

	/**
	 * Creates a new instance with the specified id. This is preferred to the default
	 * constructor when the id is known to prevent unnecessary consumption on entropy
	 * which can be slow.
	 *
	 * @param id the identifier to use
	 */
	public MapSession(String id) {
		this.id = id;
	}

	/**
	 * Creates a new instance from the provided {@link Session}.
	 *
	 * @param session the {@link Session} to initialize this {@link Session} with. Cannot
	 * be null.
	 */
	public MapSession(ExpiringSession session) {
		if (session == null) {
			throw new IllegalArgumentException("session cannot be null");
		}
		this.id = session.getId();
		this.sessionAttrs = new HashMap<String, Object>(
				session.getAttributeNames().size());
		for (String attrName : session.getAttributeNames()) {
			Object attrValue = session.getAttribute(attrName);
			this.sessionAttrs.put(attrName, attrValue);
		}
		this.lastAccessedTime = session.getLastAccessedTime();
		this.creationTime = session.getCreationTime();
		this.maxInactiveInterval = session.getMaxInactiveIntervalInSeconds();
	}

	public void setLastAccessedTime(long lastAccessedTime) {
		this.lastAccessedTime = lastAccessedTime;
	}

	public long getCreationTime() {
		return this.creationTime;
	}

	public String getId() {
		return this.id;
	}

	public long getLastAccessedTime() {
		return this.lastAccessedTime;
	}

	public void setMaxInactiveIntervalInSeconds(int interval) {
		this.maxInactiveInterval = interval;
	}

	public int getMaxInactiveIntervalInSeconds() {
		return this.maxInactiveInterval;
	}

	public boolean isExpired() {
		return isExpired(System.currentTimeMillis());
	}

	boolean isExpired(long now) {
		if (this.maxInactiveInterval < 0) {
			return false;
		}
		return now - TimeUnit.SECONDS
				.toMillis(this.maxInactiveInterval) >= this.lastAccessedTime;
	}

	@SuppressWarnings("unchecked")
	public <T> T getAttribute(String attributeName) {
		return (T) this.sessionAttrs.get(attributeName);
	}

	public Set<String> getAttributeNames() {
		return this.sessionAttrs.keySet();
	}

	public void setAttribute(String attributeName, Object attributeValue) {
		if (attributeValue == null) {
			removeAttribute(attributeName);
		}
		else {
			this.sessionAttrs.put(attributeName, attributeValue);
		}
	}

	public void removeAttribute(String attributeName) {
		this.sessionAttrs.remove(attributeName);
	}

	/**
	 * Sets the time that this {@link Session} was created in milliseconds since midnight
	 * of 1/1/1970 GMT. The default is when the {@link Session} was instantiated.
	 * @param creationTime the time that this {@link Session} was created in milliseconds
	 * since midnight of 1/1/1970 GMT.
	 */
	public void setCreationTime(long creationTime) {
		this.creationTime = creationTime;
	}

	/**
	 * Sets the identifier for this {@link Session}. The id should be a secure random
	 * generated value to prevent malicious users from guessing this value. The default is
	 * a secure random generated identifier.
	 *
	 * @param id the identifier for this session.
	 */
	public void setId(String id) {
		this.id = id;
	}

	public boolean equals(Object obj) {
		return obj instanceof Session && this.id.equals(((Session) obj).getId());
	}

	public int hashCode() {
		return this.id.hashCode();
	}

	private static final long serialVersionUID = 7160779239673823561L;
}
