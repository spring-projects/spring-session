/*
 * Copyright 2002-2015 the original author or authors.
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
package org.springframework.session.data.redis.config.annotation.web.http.gh109;

import static org.mockito.Mockito.*;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.session.ExpiringSession;
import org.springframework.session.data.redis.RedisOperationsSessionRepository;
import org.springframework.session.data.redis.config.annotation.web.http.RedisHttpSessionConfiguration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;

/**
 * This test must be in a different package than RedisHttpSessionConfiguration.
 *
 * @author Rob Winch
 * @since 1.0.2
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration
@WebAppConfiguration
public class Gh109Tests {

	@Test
	public void loads() {

	}

	@Configuration
	static class Config extends RedisHttpSessionConfiguration {

		int sessionTimeout = 100;

		/**
		 * override sessionRepository construction to set the custom
		 * session-timeout
		 */
		@Bean
		@Override
		public RedisOperationsSessionRepository sessionRepository(
				RedisTemplate<String, ExpiringSession> sessionRedisTemplate) {
			RedisOperationsSessionRepository sessionRepository = new RedisOperationsSessionRepository(
					sessionRedisTemplate);
			sessionRepository.setDefaultMaxInactiveInterval(sessionTimeout);
			return sessionRepository;
		}

		@Bean
		public RedisConnectionFactory redisConnectionFactory() {
			RedisConnectionFactory factory = mock(RedisConnectionFactory.class);
			RedisConnection connection = mock(RedisConnection.class);

			when(factory.getConnection()).thenReturn(connection);
			return factory;
		}
	}
}
