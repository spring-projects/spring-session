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

import java.util.Set;

/**
 * Provides a way to identify a user in an agnostic way. This allows the session to be
 * used by an HttpSession, WebSocket Session, or even non web related sessions.
 *
 * @author Rob Winch
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
	 * Gets the Object associated with the specified name or null if no Object is
	 * associated to that name.
	 *
	 * @param attributeName the name of the attribute to get
	 * @return the Object associated with the specified name or null if no Object is
	 * associated to that name
	 * @param <T> The return type of the attribute
	 */
	<T> T getAttribute(String attributeName);

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
}
