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

package org.springframework.session.data.redis.config.annotation.web.http;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.SubscriptionListener;
import org.springframework.session.data.redis.config.ConfigureRedisAction;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.mock;

/**
 * @author Rob Winch
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration
@WebAppConfiguration
class RedisHttpSessionConfigurationNoOpConfigureRedisActionTests {

	@Test
	void redisConnectionFactoryNotUsedSinceNoValidation() {
	}

	@EnableRedisHttpSession
	@Configuration
	static class Config {

		@Bean
		ConfigureRedisAction configureRedisAction() {
			return ConfigureRedisAction.NO_OP;
		}

		@Bean
		RedisConnectionFactory redisConnectionFactory() {
			RedisConnectionFactory redisConnectionFactory = mock(RedisConnectionFactory.class);
			RedisConnection connection = mock(RedisConnection.class);
			given(redisConnectionFactory.getConnection()).willReturn(connection);

			willAnswer((it) -> {
				SubscriptionListener listener = it.getArgument(0);
				listener.onPatternSubscribed(it.getArgument(1), 0);
				listener.onChannelSubscribed("__keyevent@0__:del".getBytes(), 0);
				listener.onChannelSubscribed("__keyevent@0__:expired".getBytes(), 0);

				return null;
			}).given(connection).pSubscribe(any(), any());

			return redisConnectionFactory;
		}

	}

}
