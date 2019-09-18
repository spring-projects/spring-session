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

import org.springframework.data.redis.core.ReactiveRedisOperations;
import org.springframework.session.ReactiveSessionRepository;
import org.springframework.util.Assert;

/**
 * This {@link ReactiveSessionRepository} implementation is kept in order to support
 * migration to {@link ReactiveRedisSessionRepository} in a backwards compatible manner.
 *
 * @author Vedran Pavic
 * @since 2.0.0
 * @deprecated since 2.2.0 in favor of {@link ReactiveRedisSessionRepository}
 */
@Deprecated
public class ReactiveRedisOperationsSessionRepository extends ReactiveRedisSessionRepository {

	/**
	 * Create a new {@link ReactiveRedisOperationsSessionRepository} instance.
	 * @param sessionRedisOperations the {@link ReactiveRedisOperations} to use for
	 * managing sessions
	 * @see ReactiveRedisSessionRepository#ReactiveRedisSessionRepository(ReactiveRedisOperations)
	 */
	public ReactiveRedisOperationsSessionRepository(ReactiveRedisOperations<String, Object> sessionRedisOperations) {
		super(sessionRedisOperations);
	}

	/**
	 * Sets the redis flush mode. Default flush mode is {@link RedisFlushMode#ON_SAVE}.
	 * @param redisFlushMode the new redis flush mode
	 * @deprecated since 2.2.0 as support {@code IMMEDIATE} is removed
	 */
	@Deprecated
	public void setRedisFlushMode(RedisFlushMode redisFlushMode) {
		Assert.notNull(redisFlushMode, "redisFlushMode cannot be null");
	}

}
