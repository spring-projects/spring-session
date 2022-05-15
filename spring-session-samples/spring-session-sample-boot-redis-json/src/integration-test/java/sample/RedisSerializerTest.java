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

package sample;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testcontainers.containers.GenericContainer;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.session.data.redis.config.annotation.SpringSessionRedisOperations;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author jitendra
 * @author Vedran Pavic
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest
class RedisSerializerTest {

	private static final String DOCKER_IMAGE = "redis:5.0.14";

	@SpringSessionRedisOperations
	private RedisTemplate<Object, Object> sessionRedisTemplate;

	@Test
	void testRedisTemplate() {
		assertThat(this.sessionRedisTemplate).isNotNull();
		assertThat(this.sessionRedisTemplate.getDefaultSerializer()).isNotNull();
		assertThat(this.sessionRedisTemplate.getDefaultSerializer())
				.isInstanceOf(GenericJackson2JsonRedisSerializer.class);
	}

	@TestConfiguration
	static class Config {

		@Bean
		GenericContainer redisContainer() {
			GenericContainer redisContainer = new GenericContainer(DOCKER_IMAGE).withExposedPorts(6379);
			redisContainer.start();
			return redisContainer;
		}

		@Bean
		LettuceConnectionFactory redisConnectionFactory() {
			return new LettuceConnectionFactory(redisContainer().getHost(), redisContainer().getFirstMappedPort());
		}

	}

}
