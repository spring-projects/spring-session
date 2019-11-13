/*
 * Copyright 2014-2019 the original author or authors.
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
import java.util.Collections;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.session.MapSession;
import org.springframework.session.config.annotation.web.http.EnableSpringHttpSession;
import org.springframework.session.data.redis.RedisSessionRepository.RedisSession;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

/**
 * Integration tests for {@link RedisSessionRepository}.
 *
 * @author Vedran Pavic
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration
@WebAppConfiguration
class RedisSessionRepositoryITests extends AbstractRedisITests {

	@Autowired
	private RedisSessionRepository sessionRepository;

	@Test
	void save_NewSession_ShouldSaveSession() {
		RedisSession session = createAndSaveSession(Instant.now());
		assertThat(session.getMaxInactiveInterval())
				.isEqualTo(Duration.ofSeconds(MapSession.DEFAULT_MAX_INACTIVE_INTERVAL_SECONDS));
		assertThat(session.getAttributeNames()).isEqualTo(Collections.singleton("attribute1"));
		assertThat(session.<String>getAttribute("attribute1")).isEqualTo("value1");
	}

	@Test
	void save_LastAccessedTimeInPast_ShouldExpireSession() {
		assertThat(createAndSaveSession(Instant.EPOCH)).isNull();
	}

	@Test
	void save_DeletedSession_ShouldThrowException() {
		RedisSession session = createAndSaveSession(Instant.now());
		this.sessionRepository.deleteById(session.getId());
		assertThatIllegalStateException().isThrownBy(() -> this.sessionRepository.save(session))
				.withMessage("Session was invalidated");
	}

	@Test
	void save_ConcurrentUpdates_ShouldSaveSession() {
		RedisSession copy1 = createAndSaveSession(Instant.now());
		String sessionId = copy1.getId();
		RedisSession copy2 = this.sessionRepository.findById(sessionId);
		Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
		updateSession(copy1, now.plusSeconds(1L), "attribute2", "value2");
		this.sessionRepository.save(copy1);
		updateSession(copy2, now.plusSeconds(2L), "attribute3", "value3");
		this.sessionRepository.save(copy2);
		RedisSession session = this.sessionRepository.findById(sessionId);
		assertThat(session.getLastAccessedTime()).isEqualTo(now.plusSeconds(2L));
		assertThat(session.getAttributeNames()).hasSize(3);
		assertThat(session.<String>getAttribute("attribute1")).isEqualTo("value1");
		assertThat(session.<String>getAttribute("attribute2")).isEqualTo("value2");
		assertThat(session.<String>getAttribute("attribute3")).isEqualTo("value3");
	}

	@Test
	void save_ChangeSessionIdAndUpdateAttribute_ShouldChangeSessionId() {
		RedisSession session = createAndSaveSession(Instant.now());
		String originalSessionId = session.getId();
		updateSession(session, Instant.now(), "attribute1", "value2");
		String newSessionId = "1";
		session.changeSessionId(newSessionId);
		this.sessionRepository.save(session);
		RedisSession loaded = this.sessionRepository.findById(newSessionId);
		assertThat(loaded).isNotNull();
		assertThat(loaded.getAttributeNames()).hasSize(1);
		assertThat(loaded.<String>getAttribute("attribute1")).isEqualTo("value2");
		assertThat(this.sessionRepository.findById(originalSessionId)).isNull();
	}

	@Test
	void save_OnlyChangeSessionId_ShouldChangeSessionId() {
		RedisSession session = createAndSaveSession(Instant.now());
		String originalSessionId = session.getId();
		String newSessionId = "1";
		session.changeSessionId(newSessionId);
		this.sessionRepository.save(session);
		assertThat(this.sessionRepository.findById(newSessionId)).isNotNull();
		assertThat(this.sessionRepository.findById(originalSessionId)).isNull();
	}

	@Test
	void save_ChangeSessionIdTwice_ShouldChangeSessionId() {
		RedisSession session = createAndSaveSession(Instant.now());
		String originalSessionId = session.getId();
		updateSession(session, Instant.now(), "attribute1", "value2");
		String newSessionId1 = "1";
		session.changeSessionId(newSessionId1);
		updateSession(session, Instant.now(), "attribute1", "value3");
		String newSessionId2 = "2";
		session.changeSessionId(newSessionId2);
		this.sessionRepository.save(session);
		assertThat(this.sessionRepository.findById(newSessionId1)).isNull();
		assertThat(this.sessionRepository.findById(newSessionId2)).isNotNull();
		assertThat(this.sessionRepository.findById(originalSessionId)).isNull();
	}

	@Test
	void save_ChangeSessionIdOnNewSession_ShouldChangeSessionId() {
		RedisSession session = this.sessionRepository.createSession();
		String originalSessionId = session.getId();
		updateSession(session, Instant.now(), "attribute1", "value1");
		String newSessionId = "1";
		session.changeSessionId(newSessionId);
		this.sessionRepository.save(session);
		assertThat(this.sessionRepository.findById(newSessionId)).isNotNull();
		assertThat(this.sessionRepository.findById(originalSessionId)).isNull();
	}

	@Test
	void save_ChangeSessionIdSaveTwice_ShouldChangeSessionId() {
		RedisSession session = createAndSaveSession(Instant.now());
		String originalSessionId;
		originalSessionId = session.getId();
		updateSession(session, Instant.now(), "attribute1", "value1");
		String newSessionId = "1";
		session.changeSessionId(newSessionId);
		this.sessionRepository.save(session);
		this.sessionRepository.save(session);
		assertThat(this.sessionRepository.findById(newSessionId)).isNotNull();
		assertThat(this.sessionRepository.findById(originalSessionId)).isNull();
	}

	@Test
	void save_ChangeSessionIdOnDeletedSession_ShouldThrowException() {
		RedisSession session = createAndSaveSession(Instant.now());
		String originalSessionId = session.getId();
		this.sessionRepository.deleteById(originalSessionId);
		updateSession(session, Instant.now(), "attribute1", "value1");
		String newSessionId = "1";
		session.changeSessionId(newSessionId);
		assertThatIllegalStateException().isThrownBy(() -> this.sessionRepository.save(session))
				.withMessage("Session was invalidated");
		assertThat(this.sessionRepository.findById(newSessionId)).isNull();
		assertThat(this.sessionRepository.findById(originalSessionId)).isNull();
	}

	@Test
	void save_ChangeSessionIdConcurrent_ShouldThrowException() {
		RedisSession copy1 = createAndSaveSession(Instant.now());
		String originalSessionId = copy1.getId();
		RedisSession copy2 = this.sessionRepository.findById(originalSessionId);
		Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
		updateSession(copy1, now.plusSeconds(1L), "attribute2", "value2");
		String newSessionId1 = "1";
		copy1.changeSessionId(newSessionId1);
		this.sessionRepository.save(copy1);
		updateSession(copy2, now.plusSeconds(2L), "attribute3", "value3");
		String newSessionId2 = "2";
		copy2.changeSessionId(newSessionId2);
		assertThatIllegalStateException().isThrownBy(() -> this.sessionRepository.save(copy2))
				.withMessage("Session was invalidated");
		assertThat(this.sessionRepository.findById(newSessionId1)).isNotNull();
		assertThat(this.sessionRepository.findById(newSessionId2)).isNull();
		assertThat(this.sessionRepository.findById(originalSessionId)).isNull();
	}

	@Test
	void deleteById_ValidSession_ShouldDeleteSession() {
		RedisSession session = createAndSaveSession(Instant.now());
		this.sessionRepository.deleteById(session.getId());
		assertThat(this.sessionRepository.findById(session.getId())).isNull();
	}

	@Test
	void deleteById_DeletedSession_ShouldDoNothing() {
		RedisSession session = createAndSaveSession(Instant.now());
		this.sessionRepository.deleteById(session.getId());
		this.sessionRepository.deleteById(session.getId());
		assertThat(this.sessionRepository.findById(session.getId())).isNull();
	}

	@Test
	void deleteById_NonexistentSession_ShouldDoNothing() {
		String sessionId = UUID.randomUUID().toString();
		this.sessionRepository.deleteById(sessionId);
		assertThat(this.sessionRepository.findById(sessionId)).isNull();
	}

	private RedisSession createAndSaveSession(Instant lastAccessedTime) {
		RedisSession session = this.sessionRepository.createSession();
		session.setLastAccessedTime(lastAccessedTime);
		session.setAttribute("attribute1", "value1");
		this.sessionRepository.save(session);
		return this.sessionRepository.findById(session.getId());
	}

	private static void updateSession(RedisSession session, Instant lastAccessedTime, String attributeName,
			Object attributeValue) {
		session.setLastAccessedTime(lastAccessedTime);
		session.setAttribute(attributeName, attributeValue);
	}

	@Configuration
	@EnableSpringHttpSession
	static class Config extends BaseConfig {

		@Bean
		RedisSessionRepository sessionRepository(RedisConnectionFactory redisConnectionFactory) {
			RedisTemplate<String, Object> redisTemplate = new RedisTemplate<>();
			redisTemplate.setConnectionFactory(redisConnectionFactory);
			redisTemplate.afterPropertiesSet();
			return new RedisSessionRepository(redisTemplate);
		}

	}

}
