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

package org.springframework.session.data.redis.config.annotation.web.http.gh109;

import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisServerCommands;
import org.springframework.data.redis.connection.SubscriptionListener;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.session.data.redis.RedisIndexedSessionRepository;
import org.springframework.session.data.redis.config.annotation.web.http.RedisHttpSessionConfiguration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.mock;

/**
 * This test must be in a different package than RedisHttpSessionConfiguration.
 *
 * @author Rob Winch
 * @author Mark Paluch
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration
@WebAppConfiguration
class Gh109Tests {

	@Test
	void loads() {

	}

	@Configuration
	static class Config extends RedisHttpSessionConfiguration {

		int sessionTimeout = 100;

		/**
		 * override sessionRepository construction to set the custom session-timeout
		 */
		@Bean
		RedisIndexedSessionRepository sessionRepository(RedisOperations<String, Object> sessionRedisTemplate,
				ApplicationEventPublisher applicationEventPublisher) {
			RedisIndexedSessionRepository sessionRepository = new RedisIndexedSessionRepository(sessionRedisTemplate);
			sessionRepository.setDefaultMaxInactiveInterval(this.sessionTimeout);
			return sessionRepository;
		}

		@Bean
		RedisConnectionFactory redisConnectionFactory() {
			RedisConnectionFactory factory = mock(RedisConnectionFactory.class);
			RedisConnection connection = mock(RedisConnection.class);
			RedisServerCommands commands = mock(RedisServerCommands.class);
			given(factory.getConnection()).willReturn(connection);
			given(connection.serverCommands()).willReturn(commands);
			given(commands.getConfig(anyString())).willReturn(new Properties());
			willAnswer((it) -> {
				SubscriptionListener listener = it.getArgument(0);
				listener.onPatternSubscribed(it.getArgument(1), 0);
				listener.onChannelSubscribed("__keyevent@0__:del".getBytes(), 0);
				listener.onChannelSubscribed("__keyevent@0__:expired".getBytes(), 0);

				return null;
			}).given(connection).pSubscribe(any(), any());
			return factory;
		}

	}

}
