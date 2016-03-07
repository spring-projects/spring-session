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

/**
 * A {@link Session} that contains additional attributes that are useful for determining
 * if a session is expired.
 *
 * @author Rob Winch
 * @since 1.0
 */
public interface ExpiringSession extends Session {

	/**
	 * Gets the time when this session was created in milliseconds since midnight of
	 * 1/1/1970 GMT.
	 *
	 * @return the time when this session was created in milliseconds since midnight of
	 * 1/1/1970 GMT.
	 */
	long getCreationTime();

	/**
	 * Sets the last accessed time in milliseconds since midnight of 1/1/1970 GMT.
	 *
	 * @param lastAccessedTime the last accessed time in milliseconds since midnight of
	 * 1/1/1970 GMT
	 */
	void setLastAccessedTime(long lastAccessedTime);

	/**
	 * Gets the last time this {@link Session} was accessed expressed in milliseconds
	 * since midnight of 1/1/1970 GMT.
	 *
	 * @return the last time the client sent a request associated with the session
	 * expressed in milliseconds since midnight of 1/1/1970 GMT
	 */
	long getLastAccessedTime();

	/**
	 * Sets the maximum inactive interval in seconds between requests before this session
	 * will be invalidated. A negative time indicates that the session will never timeout.
	 *
	 * @param interval the number of seconds that the {@link Session} should be kept alive
	 * between client requests.
	 */
	void setMaxInactiveIntervalInSeconds(int interval);

	/**
	 * Gets the maximum inactive interval in seconds between requests before this session
	 * will be invalidated. A negative time indicates that the session will never timeout.
	 *
	 * @return the maximum inactive interval in seconds between requests before this
	 * session will be invalidated. A negative time indicates that the session will never
	 * timeout.
	 */
	int getMaxInactiveIntervalInSeconds();

	/**
	 * Returns true if the session is expired.
	 *
	 * @return true if the session is expired, else false.
	 */
	boolean isExpired();

}
