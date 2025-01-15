/*
 * Copyright 2014-2023 the original author or authors.
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
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.ReactiveRedisOperations;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.session.ReactiveFindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.session.config.ReactiveSessionRepositoryCustomizer;
import org.springframework.session.data.SessionEventRegistry;
import org.springframework.session.data.redis.ReactiveRedisIndexedSessionRepository.RedisSession;
import org.springframework.session.data.redis.config.ConfigureReactiveRedisAction;
import org.springframework.session.data.redis.config.annotation.web.server.EnableRedisIndexedWebSession;
import org.springframework.session.events.SessionCreatedEvent;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@ExtendWith(SpringExtension.class)
class ReactiveRedisIndexedSessionRepositoryConfigurationITests {

	ReactiveRedisIndexedSessionRepository repository;

	ReactiveRedisOperations<String, Object> sessionRedisOperations;

	AnnotationConfigWebApplicationContext context = new AnnotationConfigWebApplicationContext();

	SecurityContext securityContext;

	@BeforeEach
	void setup() {
		this.securityContext = SecurityContextHolder.createEmptyContext();
		this.securityContext.setAuthentication(new UsernamePasswordAuthenticationToken("username-" + UUID.randomUUID(),
				"na", AuthorityUtils.createAuthorityList("ROLE_USER")));
	}

	@Test
	void cleanUpTaskWhenSessionIsExpiredThenAllRelatedKeysAreDeleted() {
		registerConfig(OneSecCleanUpIntervalConfig.class);
		RedisSession session = this.repository.createSession().block();
		session.setAttribute("SPRING_SECURITY_CONTEXT", this.securityContext);
		this.repository.save(session).block();
		await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> {
			assertThat(this.repository.findById(session.getId()).block()).isNull();
			Boolean hasSessionKey = this.sessionRedisOperations.hasKey("spring:session:sessions:" + session.getId())
				.block();
			Boolean hasSessionIndexesKey = this.sessionRedisOperations
				.hasKey("spring:session:sessions:" + session.getId() + ":idx")
				.block();
			Boolean hasPrincipalIndexKey = this.sessionRedisOperations
				.hasKey("spring:session:sessions:index:"
						+ ReactiveFindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME + ":"
						+ this.securityContext.getAuthentication().getName())
				.block();
			Long expirationsSize = this.sessionRedisOperations.opsForZSet()
				.size("spring:session:sessions:expirations")
				.block();
			assertThat(hasSessionKey).isFalse();
			assertThat(hasSessionIndexesKey).isFalse();
			assertThat(hasPrincipalIndexKey).isFalse();
			assertThat(expirationsSize).isZero();
		});
	}

	@Test
	void onSessionCreatedWhenUsingJsonSerializerThenEventDeserializedCorrectly() throws InterruptedException {
		registerConfig(SessionEventRegistryJsonSerializerConfig.class);
		RedisSession session = this.repository.createSession().block();
		this.repository.save(session).block();
		SessionEventRegistry registry = this.context.getBean(SessionEventRegistry.class);
		SessionCreatedEvent event = registry.getEvent(session.getId());
		Session eventSession = event.getSession();
		assertThat(eventSession).usingRecursiveComparison()
			.withComparatorForFields(new InstantComparator(), "cached.creationTime", "cached.lastAccessedTime")
			.isEqualTo(session);
	}

	@Test
	void sessionExpiredWhenNoCleanUpTaskAndNoKeyspaceEventsThenNoCleanup() {
		registerConfig(DisableCleanupTaskAndNoKeyspaceEventsConfig.class);
		RedisSession session = this.repository.createSession().block();
		this.repository.save(session).block();
		await().during(Duration.ofSeconds(3)).untilAsserted(() -> {
			Boolean exists = this.sessionRedisOperations.hasKey("spring:session:sessions:" + session.getId()).block();
			assertThat(exists).isTrue();
		});
	}

	private void registerConfig(Class<?> clazz) {
		this.context.register(clazz);
		this.context.refresh();
		this.repository = this.context.getBean(ReactiveRedisIndexedSessionRepository.class);
		this.sessionRedisOperations = this.repository.getSessionRedisOperations();
	}

	static class InstantComparator implements Comparator<Instant> {

		@Override
		public int compare(Instant o1, Instant o2) {
			return o1.truncatedTo(ChronoUnit.SECONDS).compareTo(o2.truncatedTo(ChronoUnit.SECONDS));
		}

	}

	@Configuration(proxyBeanMethods = false)
	@EnableRedisIndexedWebSession(maxInactiveIntervalInSeconds = 1)
	@Import(AbstractRedisITests.BaseConfig.class)
	static class OneSecCleanUpIntervalConfig {

		@Bean
		ReactiveSessionRepositoryCustomizer<ReactiveRedisIndexedSessionRepository> customizer() {
			return (sessionRepository) -> sessionRepository.setCleanupInterval(Duration.ofSeconds(1));
		}

	}

	@Configuration(proxyBeanMethods = false)
	@EnableRedisIndexedWebSession
	@Import(AbstractRedisITests.BaseConfig.class)
	static class SessionEventRegistryJsonSerializerConfig {

		@Bean
		SessionEventRegistry sessionEventRegistry() {
			return new SessionEventRegistry();
		}

		@Bean
		RedisSerializer<Object> springSessionDefaultRedisSerializer() {
			return RedisSerializer.json();
		}

	}

	@Configuration(proxyBeanMethods = false)
	@EnableRedisIndexedWebSession(maxInactiveIntervalInSeconds = 1)
	@Import(AbstractRedisITests.BaseConfig.class)
	static class DisableCleanupTaskAndNoKeyspaceEventsConfig {

		@Bean
		ReactiveSessionRepositoryCustomizer<ReactiveRedisIndexedSessionRepository> customizer() {
			return ReactiveRedisIndexedSessionRepository::disableCleanupTask;
		}

		@Bean
		ConfigureReactiveRedisAction configureReactiveRedisAction() {
			return (connection) -> connection.serverCommands().setConfig("notify-keyspace-events", "").then();
		}

	}

}
