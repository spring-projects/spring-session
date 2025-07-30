/*
 * Copyright 2014-present the original author or authors.
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

package org.springframework.session.data.redis;

/**
 * An interface for storing {@link RedisIndexedSessionRepository.RedisSession} instances
 * with their expected expiration time. This approach is necessary because Redis does not
 * guarantee when the expired event will be fired if the key has not been accessed. For
 * more details, see the Redis documentation on
 * <a href="https://redis.io/commands/expire/#:~:text=How%20Redis%20expires%20keys"> how
 * keys expire</a>. To address the uncertainty of expired events, sessions can be stored
 * with their expected expiration time, ensuring each key is accessed when it is expected
 * to expire. This interface defines common operations for tracking sessions and their
 * expiration times, and allows for a strategy to clean up expired sessions.
 *
 * @author Marcus da Coregio
 * @since 3.4
 */
public interface RedisSessionExpirationStore {

	/**
	 * Saves the session and its expected expiration time, so it can be found later on by
	 * its expiration time in order for clean up to happen.
	 * @param session the session to save
	 */
	void save(RedisIndexedSessionRepository.RedisSession session);

	/**
	 * Removes the session id from the expiration store.
	 * @param sessionId the session id
	 */
	void remove(String sessionId);

	/**
	 * Performs clean up on the expired sessions.
	 */
	void cleanupExpiredSessions();

}
