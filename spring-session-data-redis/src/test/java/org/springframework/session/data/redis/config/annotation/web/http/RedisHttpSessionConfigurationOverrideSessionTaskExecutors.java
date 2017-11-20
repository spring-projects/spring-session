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

package org.springframework.session.data.redis.config.annotation.web.http;

import java.util.Properties;
import java.util.concurrent.Executor;

import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.scheduling.SchedulingAwareRunnable;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.web.WebAppConfiguration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * @author Vladimir Tsanev
 * @author Mark Paluch
 *
 */
@RunWith(SpringRunner.class)
@ContextConfiguration
@WebAppConfiguration
public class RedisHttpSessionConfigurationOverrideSessionTaskExecutors {

	@Autowired
	RedisMessageListenerContainer redisMessageListenerContainer;

	@Autowired
	Executor springSessionRedisTaskExecutor;

	@Autowired
	Executor springSessionRedisSubscriptionExecutor;

	@Test
	public void overrideSessionTaskExecutors() {
		verify(this.springSessionRedisSubscriptionExecutor, times(1))
				.execute(any(SchedulingAwareRunnable.class));
		verify(this.springSessionRedisTaskExecutor, never()).execute(any(Runnable.class));
	}

	@EnableRedisHttpSession
	@Configuration
	static class Config {
		@Bean
		public Executor springSessionRedisTaskExecutor() {
			return mock(Executor.class);
		}

		@Bean
		public Executor springSessionRedisSubscriptionExecutor() {
			return mock(Executor.class);
		}

		@Bean
		public RedisConnectionFactory connectionFactory() {
			RedisConnectionFactory factory = mock(RedisConnectionFactory.class);
			RedisConnection connection = mock(RedisConnection.class);
			given(factory.getConnection()).willReturn(connection);
			given(connection.getConfig(anyString())).willReturn(new Properties());

			return factory;
		}
	}
}
