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

package org.springframework.session.data.redis;

import org.testcontainers.containers.GenericContainer;

import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

/**
 * Base class for {@link RedisOperationsSessionRepository} integration tests.
 *
 * @author Vedran Pavic
 */
public abstract class AbstractRedisITests {

	private static final String DOCKER_IMAGE = "redis:4.0.11";

	protected static class BaseConfig {

		@Bean
		public GenericContainer redisContainer() {
			GenericContainer redisContainer = new GenericContainer(DOCKER_IMAGE)
					.withExposedPorts(6379);
			redisContainer.start();
			return redisContainer;
		}

		@Bean
		public LettuceConnectionFactory redisConnectionFactory() {
			RedisStandaloneConfiguration configuration = new RedisStandaloneConfiguration(
					redisContainer().getContainerIpAddress(),
					redisContainer().getFirstMappedPort());
			return new LettuceConnectionFactory(configuration);
		}

	}

}
