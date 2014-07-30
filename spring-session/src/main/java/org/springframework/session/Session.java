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
package org.springframework.session;

import java.io.Serializable;
import java.util.Set;

/**
 * Provides a way to identify a user in an agnostic way. This allows the session to be used by an HttpSession, WebSocket
 * Session, or even non web related sessions.
 *
 * @author Rob Winch
 * @since 1.0
 */
public interface Session {

    /**
     * Gets the time when this session was created in milliseconds since midnight of 1/1/1970 GMT.
     *
     * @return the time when this session was created in milliseconds since midnight of 1/1/1970 GMT.
     */
    long getCreationTime();

    /**
     * Gets a unique string that identifies the {@link Session}
     *
     * @return a unique string that identifies the {@link Session}
     */
    String getId();

    /**
     * Gets the last time this {@link Session} was accessed expressed in milliseconds since midnight of 1/1/1970 GMT
     *
     * @return the last time the client sent a request associated with the session expressed in milliseconds since midnight of 1/1/1970 GMT
     */
    long getLastAccessedTime();

    /**
     * Sets the maximum inactive interval in seconds between requests before this session will be invalidated. A negative time indicates that the session will never timeout.
     *
     * @param interval the number of seconds that the {@link Session} should be kept alive between client requests.
     */
    void setMaxInactiveInterval(int interval);

    /**
     * Gets the maximum inactive interval in seconds between requests before this session will be invalidated. A negative time indicates that the session will never timeout.
     *
     * @return the maximum inactive interval in seconds between requests before this session will be invalidated. A negative time indicates that the session will never timeout.
     */
    int getMaxInactiveInterval();

    /**
     * Gets the Object associated with the specified name or null if no Object is associated to that name.
     *
     * @param attributeName the name of the attribute to get
     * @return the Object associated with the specified name or null if no Object is associated to that name
     */
    Object getAttribute(String attributeName);

    /**
     * Gets the attribute names that have a value associated with it. Each value can be passed into {@link org.springframework.session.Session#getAttribute(String)} to obtain the attribute value.
     *
     * @return the attribute names that have a value associated with it.
     * @see #getAttribute(String)
     */
    Set<String> getAttributeNames();

    /**
     * Sets the attribute value for the provided attribute name. If the attributeValue is null, it has the same result as removing the attribute with {@link org.springframework.session.Session#removeAttribute(String)} .
     *
     * @param attributeName the attribute name to set
     * @param attributeValue the value of the attribute to set. If null, the attribute will be removed.
     */
    void setAttribute(String attributeName, Object attributeValue);

    /**
     * Removes the attribute with the provided attribute name
     * @param attributeName the name of the attribute to remove
     */
    void removeAttribute(String attributeName);
}