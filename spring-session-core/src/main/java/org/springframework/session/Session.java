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

package org.springframework.session;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

/**
 * Provides a way to identify a user in an agnostic way. This allows the session to be
 * used by an HttpSession, WebSocket Session, or even non web related sessions.
 *
 * @author Rob Winch
 * @author Vedran Pavic
 * @since 1.0
 */
public interface Session {

	/**
	 * Gets a unique string that identifies the {@link Session}.
	 *
	 * @return a unique string that identifies the {@link Session}
	 */
	String getId();

	/**
	 * Changes the session id. After invoking the {@link #getId()} will return a new identifier.
	 * @return the new session id which {@link #getId()} will now return
	 */
	String changeSessionId();

	/**
	 * Gets the Object associated with the specified name or null if no Object is
	 * associated to that name.
	 *
	 * @param <T> the return type of the attribute
	 * @param attributeName the name of the attribute to get
	 * @return the Object associated with the specified name or null if no Object is
	 * associated to that name
	 */
	<T> T getAttribute(String attributeName);

	/**
	 * Return the session attribute value or if not present raise an
	 * {@link IllegalArgumentException}.
	 * @param name the attribute name
	 * @param <T> the attribute type
	 * @return the attribute value
	 */
	@SuppressWarnings("unchecked")
	default <T> T getRequiredAttribute(String name) {
		T result = getAttribute(name);
		if (result == null) {
			throw new IllegalArgumentException(
					"Required attribute '" + name + "' is missing.");
		}
		return result;
	}

	/**
	 * Return the session attribute value, or a default, fallback value.
	 * @param name the attribute name
	 * @param defaultValue a default value to return instead
	 * @param <T> the attribute type
	 * @return the attribute value
	 */
	@SuppressWarnings("unchecked")
	default <T> T getAttributeOrDefault(String name, T defaultValue) {
		T result = getAttribute(name);
		return (result != null) ? result : defaultValue;
	}

	/**
	 * Gets the attribute names that have a value associated with it. Each value can be
	 * passed into {@link org.springframework.session.Session#getAttribute(String)} to
	 * obtain the attribute value.
	 *
	 * @return the attribute names that have a value associated with it.
	 * @see #getAttribute(String)
	 */
	Set<String> getAttributeNames();

	/**
	 * Sets the attribute value for the provided attribute name. If the attributeValue is
	 * null, it has the same result as removing the attribute with
	 * {@link org.springframework.session.Session#removeAttribute(String)} .
	 *
	 * @param attributeName the attribute name to set
	 * @param attributeValue the value of the attribute to set. If null, the attribute
	 * will be removed.
	 */
	void setAttribute(String attributeName, Object attributeValue);

	/**
	 * Removes the attribute with the provided attribute name.
	 * @param attributeName the name of the attribute to remove
	 */
	void removeAttribute(String attributeName);

	/**
	 * Gets the time when this session was created.
	 *
	 * @return the time when this session was created.
	 */
	Instant getCreationTime();

	/**
	 * Sets the last accessed time.
	 *
	 * @param lastAccessedTime the last accessed time
	 */
	void setLastAccessedTime(Instant lastAccessedTime);

	/**
	 * Gets the last time this {@link Session} was accessed.
	 *
	 * @return the last time the client sent a request associated with the session
	 */
	Instant getLastAccessedTime();

	/**
	 * Sets the maximum inactive interval between requests before this session will be
	 * invalidated. A negative time indicates that the session will never timeout.
	 *
	 * @param interval the amount of time that the {@link Session} should be kept alive
	 * between client requests.
	 */
	void setMaxInactiveInterval(Duration interval);

	/**
	 * Gets the maximum inactive interval between requests before this session will be
	 * invalidated. A negative time indicates that the session will never timeout.
	 *
	 * @return the maximum inactive interval between requests before this session will be
	 * invalidated. A negative time indicates that the session will never timeout.
	 */
	Duration getMaxInactiveInterval();

	/**
	 * Returns true if the session is expired.
	 *
	 * @return true if the session is expired, else false.
	 */
	boolean isExpired();

}
