/*
 * Copyright 2014-2025 the original author or authors.
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

import java.util.Properties;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisServerCommands;
import org.springframework.data.redis.connection.SubscriptionListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.session.data.redis.config.annotation.SpringSessionRedisOperations;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.mock;

/**
 * @author Rob Winch
 * @author Mark Paluch
 *
 */
@SpringJUnitWebConfig
class RedisHttpSessionConfigurationOverrideDefaultSerializerTests {

	@SpringSessionRedisOperations
	RedisTemplate<Object, Object> template;

	@Autowired
	RedisSerializer<Object> defaultRedisSerializer;

	@Test
	void overrideDefaultRedisTemplate() {
		assertThat(this.template.getDefaultSerializer()).isSameAs(this.defaultRedisSerializer);
	}

	@EnableRedisHttpSession
	@Configuration
	static class Config {

		@Bean
		@SuppressWarnings("unchecked")
		RedisSerializer<Object> springSessionDefaultRedisSerializer() {
			return mock(RedisSerializer.class);
		}

		@Bean
		RedisConnectionFactory connectionFactory() {
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
