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

import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.testcontainers.containers.GenericContainer;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.session.data.redis.config.annotation.SpringSessionRedisOperations;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author jitendra
 * @author Vedran Pavic
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = Application.class)
@ContextConfiguration(initializers = RedisSerializerTest.Initializer.class)
public class RedisSerializerTest {

	private static final String DOCKER_IMAGE = "redis:4.0.7";

	@ClassRule
	public static GenericContainer redisContainer = new GenericContainer(DOCKER_IMAGE)
			.withExposedPorts(6379);

	@SpringSessionRedisOperations
	private RedisTemplate<Object, Object> sessionRedisTemplate;

	@Test
	public void testRedisTemplate() {
		assertThat(this.sessionRedisTemplate).isNotNull();
		assertThat(this.sessionRedisTemplate.getDefaultSerializer()).isNotNull();
		assertThat(this.sessionRedisTemplate.getDefaultSerializer())
				.isInstanceOf(GenericJackson2JsonRedisSerializer.class);
	}

	static class Initializer
			implements ApplicationContextInitializer<ConfigurableApplicationContext> {

		@Override
		public void initialize(
				ConfigurableApplicationContext configurableApplicationContext) {
			TestPropertyValues
					.of("spring.redis.host=" + redisContainer.getContainerIpAddress(),
							"spring.redis.port=" + redisContainer.getFirstMappedPort())
					.applyTo(configurableApplicationContext.getEnvironment());
		}

	}

}
