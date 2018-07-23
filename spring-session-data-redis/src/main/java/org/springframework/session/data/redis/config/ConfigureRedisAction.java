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

package org.springframework.session.data.redis.config;

import org.springframework.data.redis.connection.RedisConnection;

/**
 * Allows specifying a strategy for configuring and validating Redis.
 *
 * @author Rob Winch
 * @since 1.0.1
 */
public interface ConfigureRedisAction {

	void configure(RedisConnection connection);

	/**
	 * A do nothing implementation of {@link ConfigureRedisAction}.
	 */
	ConfigureRedisAction NO_OP = (connection) -> {
	};

}
