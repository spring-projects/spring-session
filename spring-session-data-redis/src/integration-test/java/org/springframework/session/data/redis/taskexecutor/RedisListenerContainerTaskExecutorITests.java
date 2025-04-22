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

package org.springframework.session.data.redis.taskexecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.data.redis.core.BoundSetOperations;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.session.data.redis.AbstractRedisITests;
import org.springframework.session.data.redis.config.annotation.SpringSessionRedisOperations;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisIndexedHttpSession;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Vladimir Tsanev
 */
@SpringJUnitWebConfig
class RedisListenerContainerTaskExecutorITests extends AbstractRedisITests {

	@Autowired
	private SessionTaskExecutor executor;

	@SpringSessionRedisOperations
	private RedisOperations<Object, Object> redis;

	@Test
	void testRedisDelEventsAreDispatchedInSessionTaskExecutor() throws InterruptedException {
		BoundSetOperations<Object, Object> ops = this.redis
			.boundSetOps("spring:session:RedisListenerContainerTaskExecutorITests:expirations:dummy");
		ops.add("value");
		ops.remove("value");
		assertThat(this.executor.taskDispatched()).isTrue();

	}

	static class SessionTaskExecutor implements TaskExecutor {

		private final Object lock = new Object();

		private final Executor executor;

		private Boolean taskDispatched;

		SessionTaskExecutor(Executor executor) {
			this.executor = executor;
		}

		@Override
		public void execute(Runnable task) {
			synchronized (this.lock) {
				try {
					this.executor.execute(task);
				}
				finally {
					this.taskDispatched = true;
					this.lock.notifyAll();
				}
			}
		}

		boolean taskDispatched() throws InterruptedException {
			if (this.taskDispatched != null) {
				return this.taskDispatched;
			}
			synchronized (this.lock) {
				this.lock.wait(TimeUnit.SECONDS.toMillis(1));
			}
			return (this.taskDispatched != null) ? this.taskDispatched : Boolean.FALSE;
		}

	}

	@Configuration
	@EnableRedisIndexedHttpSession(redisNamespace = "RedisListenerContainerTaskExecutorITests")
	static class Config extends BaseConfig {

		@Bean
		Executor springSessionRedisTaskExecutor() {
			return new SessionTaskExecutor(Executors.newSingleThreadExecutor());
		}

		@Bean
		Executor springSessionRedisSubscriptionExecutor() {
			return new SimpleAsyncTaskExecutor();
		}

	}

}
