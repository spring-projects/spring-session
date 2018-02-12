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

package sample;

import java.io.IOException;

import org.testcontainers.containers.GenericContainer;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

@Configuration
@Profile("embedded-redis")
public class EmbeddedRedisConfig {

	private static final String REDIS_DOCKER_IMAGE = "redis:4.0.8";

	@Bean(initMethod = "start")
	public GenericContainer redisContainer() {
		return new GenericContainer(REDIS_DOCKER_IMAGE) {

			@Override
			public void close() {
				super.close();
				try {
					this.dockerClient.close();
				}
				catch (IOException ignored) {
				}
			}

		}.withExposedPorts(6379);
	}

	@Bean
	@Primary
	public LettuceConnectionFactory redisConnectionFactory() {
		return new LettuceConnectionFactory(redisContainer().getContainerIpAddress(),
				redisContainer().getFirstMappedPort());
	}

}
