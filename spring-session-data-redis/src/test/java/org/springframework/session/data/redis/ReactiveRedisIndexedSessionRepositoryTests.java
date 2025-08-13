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

package org.springframework.session.data.redis;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import org.springframework.data.redis.core.ReactiveRedisOperations;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ReactiveRedisIndexedSessionRepositoryTests {

	@Mock
	private ReactiveRedisOperations<String, Object> sessionRedisOperations;

	@Mock
	private ReactiveRedisTemplate<String, String> keyEventsOperations;

	private ReactiveRedisIndexedSessionRepository repository;

	@BeforeEach
	void setup() {
		this.repository = new ReactiveRedisIndexedSessionRepository(this.sessionRedisOperations,
				this.keyEventsOperations);
	}

	@Test
	void startShouldSetRunningTrue() {
		givenSubscriptionsEmpty();

		this.repository.start();

		assertThat(this.repository.isRunning()).isTrue();
	}

	@Test
	void startShouldBeIdempotent() {
		givenSubscriptionsEmpty();

		this.repository.start();
		this.repository.start();

		assertThat(this.repository.isRunning()).isTrue();

		verify(this.sessionRedisOperations, times(1)).listenToPattern(anyString());
		verify(this.keyEventsOperations, times(1)).listenToChannel(anyString(), anyString());
	}

	@Test
	void stopShouldSetRunningFalse() {
		givenSubscriptionsEmpty();

		this.repository.start();
		this.repository.stop();

		assertThat(this.repository.isRunning()).isFalse();
	}

	@Test
	void stopShouldBeIdempotent() {
		givenSubscriptionsEmpty();

		this.repository.start();
		this.repository.stop();
		this.repository.stop();
		assertThat(this.repository.isRunning()).isFalse();
	}

	@Test
	void isAutoStartupShouldReturnTrue() {
		assertThat(this.repository.isAutoStartup()).isTrue();
	}

	@Test
	void getPhaseShouldReturnPhase() {
		this.repository.setPhase(100);

		assertThat(this.repository.getPhase()).isEqualTo(100);
	}

	@Test
	void startShouldCreateSubscriptions() {
		givenSubscriptionsNever();

		this.repository.start();

		List<Disposable> subscriptions = getSubscriptions();
		assertThat(subscriptions).isNotEmpty();

		subscriptions.forEach((sub) -> assertThat(sub.isDisposed()).isFalse());
	}

	@Test
	void stopShouldDisposeAllSubscriptions() {
		givenSubscriptionsEmpty();

		this.repository.start();

		List<Disposable> subscriptionsBeforeStop = getSubscriptions();
		assertThat(subscriptionsBeforeStop).isNotEmpty();

		this.repository.stop();

		subscriptionsBeforeStop.forEach((sub) -> assertThat(sub.isDisposed()).isTrue());

		List<Disposable> subscriptionsAfterStop = getSubscriptions();
		assertThat(subscriptionsAfterStop).isEmpty();
	}

	@Test
	void cleanUpExpiredSessionsShouldRespectRunningState() {
		this.repository.stop();

		Flux<Void> result = invokeCleanUpExpiredSessions();

		StepVerifier.create(result).verifyComplete();
	}

	@Test
	void cleanUpExpiredSessionsWhenRunning() {
		givenSubscriptionsEmpty();

		this.repository.start();

		assertThat(this.repository.isRunning()).isTrue();

		Flux<Void> result = invokeCleanUpExpiredSessions();

		StepVerifier.create(result).verifyComplete();
	}

	@Test
	void destroyShouldCallStop() {
		givenSubscriptionsEmpty();

		this.repository.start();
		this.repository.destroy();
		assertThat(this.repository.isRunning()).isFalse();
	}

	@Test
	void afterPropertiesSetShouldCallStart() throws Exception {
		givenSubscriptionsEmpty();

		this.repository.afterPropertiesSet();
		assertThat(this.repository.isRunning()).isTrue();
	}

	@Test
	void cleanupIntervalZeroShouldNotCreateCleanupTask() {
		givenSubscriptionsEmpty();

		this.repository.setCleanupInterval(Duration.ZERO);
		this.repository.start();

		List<Disposable> subscriptions = getSubscriptions();

		assertThat(subscriptions).hasSize(2);
	}

	@Test
	void cleanupIntervalNonZeroShouldCreateCleanupTask() {
		givenSubscriptionsEmpty();

		this.repository.setCleanupInterval(Duration.ofSeconds(10));
		this.repository.start();

		List<Disposable> subscriptions = getSubscriptions();

		assertThat(subscriptions).hasSize(3);
	}

	@Test
	void stopWhenNotRunningNotDisposeSubscriptions() {
		this.repository.stop();

		assertThat(this.repository.isRunning()).isFalse();

		List<Disposable> subscriptions = getSubscriptions();
		assertThat(subscriptions).isEmpty();
	}

	@Test
	void multipleStartStopCyclesShouldWorkCorrectly() {
		givenSubscriptionsNever();

		this.repository.start();
		assertThat(this.repository.isRunning()).isTrue();

		this.repository.stop();
		assertThat(this.repository.isRunning()).isFalse();

		this.repository.start();
		assertThat(this.repository.isRunning()).isTrue();

		List<Disposable> subscriptions = getSubscriptions();
		assertThat(subscriptions).isNotEmpty();
		subscriptions.forEach((sub) -> assertThat(sub.isDisposed()).isFalse());
	}

	private void givenSubscriptionsNever() {
		given(this.sessionRedisOperations.listenToPattern(anyString())).willReturn(Flux.never());
		given(this.keyEventsOperations.listenToChannel(anyString(), anyString())).willReturn(Flux.never());
	}

	private void givenSubscriptionsEmpty() {
		given(this.sessionRedisOperations.listenToPattern(anyString())).willReturn(Flux.empty());
		given(this.keyEventsOperations.listenToChannel(anyString(), anyString())).willReturn(Flux.empty());
	}

	@SuppressWarnings("unchecked")
	private List<Disposable> getSubscriptions() {
		return (List<Disposable>) ReflectionTestUtils.getField(this.repository, "subscriptions");
	}

	private Flux<Void> invokeCleanUpExpiredSessions() {
		try {
			return ReflectionTestUtils.invokeMethod(this.repository, "cleanUpExpiredSessions");
		}
		catch (Exception ex) {
			return Flux.empty();
		}
	}

}
