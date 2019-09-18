/*
 * Copyright 2014-2019 the original author or authors.
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

import org.springframework.data.redis.core.RedisOperations;
import org.springframework.session.FlushMode;
import org.springframework.session.SessionRepository;
import org.springframework.util.Assert;

/**
 * This {@link SessionRepository} implementation is kept in order to support migration to
 * {@link RedisIndexedSessionRepository} in a backwards compatible manner.
 *
 * @author Rob Winch
 * @author Vedran Pavic
 * @since 1.0
 * @deprecated since 2.2.0 in favor of {@link RedisIndexedSessionRepository}
 */
@Deprecated
public class RedisOperationsSessionRepository extends RedisIndexedSessionRepository {

	/**
	 * Creates a new instance. For an example, refer to the class level javadoc.
	 * @param sessionRedisOperations the {@link RedisOperations} to use for managing the
	 * sessions. Cannot be null.
	 * @see RedisIndexedSessionRepository#RedisIndexedSessionRepository(RedisOperations)
	 */
	public RedisOperationsSessionRepository(RedisOperations<Object, Object> sessionRedisOperations) {
		super(sessionRedisOperations);
	}

	/**
	 * Sets the redis flush mode. Default flush mode is {@link RedisFlushMode#ON_SAVE}.
	 * @param redisFlushMode the new redis flush mode
	 * @deprecated since 2.2.0 in favor of {@link #setFlushMode(FlushMode)}
	 */
	@Deprecated
	public void setRedisFlushMode(RedisFlushMode redisFlushMode) {
		Assert.notNull(redisFlushMode, "redisFlushMode cannot be null");
		setFlushMode(redisFlushMode.getFlushMode());
	}

}
