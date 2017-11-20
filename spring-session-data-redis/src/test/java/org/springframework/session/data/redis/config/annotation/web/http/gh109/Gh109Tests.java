/*
 * Copyright 2014-2017 the original author or authors.
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

import java.util.Properties;

import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.session.data.redis.RedisOperationsSessionRepository;
import org.springframework.session.data.redis.config.annotation.web.http.RedisHttpSessionConfiguration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.web.WebAppConfiguration;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * This test must be in a different package than RedisHttpSessionConfiguration.
 *
 * @author Rob Winch
 * @author Mark Paluch
 * @since 1.0.2
 */
@RunWith(SpringRunner.class)
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
		 * override sessionRepository construction to set the custom session-timeout
		 */
		@Bean
		public RedisOperationsSessionRepository sessionRepository(
				RedisOperations<Object, Object> sessionRedisTemplate,
				ApplicationEventPublisher applicationEventPublisher) {
			RedisOperationsSessionRepository sessionRepository = new RedisOperationsSessionRepository(
					sessionRedisTemplate);
			sessionRepository.setDefaultMaxInactiveInterval(this.sessionTimeout);
			return sessionRepository;
		}

		@Bean
		public RedisConnectionFactory redisConnectionFactory() {
			RedisConnectionFactory factory = mock(RedisConnectionFactory.class);
			RedisConnection connection = mock(RedisConnection.class);
			given(factory.getConnection()).willReturn(connection);
			given(connection.getConfig(anyString())).willReturn(new Properties());
			return factory;
		}
	}
}
