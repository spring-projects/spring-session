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

import java.util.Properties;
import java.util.concurrent.Executor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisServerCommands;
import org.springframework.data.redis.connection.SubscriptionListener;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * @author Vladimir Tsanev
 * @author Mark Paluch
 *
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration
@WebAppConfiguration
class RedisHttpSessionConfigurationOverrideSessionTaskExecutors {

	@Autowired
	RedisMessageListenerContainer redisMessageListenerContainer;

	@Autowired
	Executor springSessionRedisTaskExecutor;

	@Autowired
	Executor springSessionRedisSubscriptionExecutor;

	@Test
	void overrideSessionTaskExecutors() {
		verify(this.springSessionRedisSubscriptionExecutor, times(1)).execute(any(Runnable.class));
		verify(this.springSessionRedisTaskExecutor, never()).execute(any(Runnable.class));
	}

	@EnableRedisHttpSession
	@Configuration
	static class Config {

		@Bean
		Executor springSessionRedisTaskExecutor() {
			Executor executor = mock(Executor.class);
			willAnswer((it) -> {
				Runnable r = it.getArgument(0);
				new Thread(r).start();
				return null;
			}).given(executor).execute(any());
			return executor;
		}

		@Bean
		Executor springSessionRedisSubscriptionExecutor() {
			Executor executor = mock(Executor.class);
			willAnswer((it) -> {
				Runnable r = it.getArgument(0);
				new Thread(r).start();
				return null;
			}).given(executor).execute(any());
			return executor;
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
