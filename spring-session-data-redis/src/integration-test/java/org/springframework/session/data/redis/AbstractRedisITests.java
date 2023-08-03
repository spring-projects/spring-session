/*
 * Copyright 2014-2022 the original author or authors.
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

import com.redis.testcontainers.RedisContainer;

import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

/**
 * Base class for Redis integration tests.
 *
 * @author Vedran Pavic
 */
public abstract class AbstractRedisITests {

	private static final String DOCKER_IMAGE = "redis:7.0.4-alpine";

	protected static class BaseConfig {

		@Bean
		public RedisContainer redisContainer() {
			RedisContainer redisContainer = new RedisContainer(
					RedisContainer.DEFAULT_IMAGE_NAME.withTag(RedisContainer.DEFAULT_TAG));
			redisContainer.start();
			return redisContainer;
		}

		@Bean
		public LettuceConnectionFactory redisConnectionFactory(RedisContainer redisContainer) {
			RedisStandaloneConfiguration configuration = new RedisStandaloneConfiguration(redisContainer.getHost(),
					redisContainer.getFirstMappedPort());
			return new LettuceConnectionFactory(configuration);
		}

	}

}
