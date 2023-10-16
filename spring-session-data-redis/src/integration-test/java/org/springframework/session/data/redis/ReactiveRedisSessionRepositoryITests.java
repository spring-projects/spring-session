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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import reactor.core.publisher.Mono;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.ReactiveHashOperations;
import org.springframework.data.redis.core.ReactiveRedisOperations;
import org.springframework.session.Session;
import org.springframework.session.data.redis.ReactiveRedisSessionRepository.RedisSession;
import org.springframework.session.data.redis.config.annotation.web.server.EnableRedisWebSession;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.endsWith;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;

/**
 * Integration tests for {@link ReactiveRedisSessionRepository}.
 *
 * @author Vedran Pavic
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration
@WebAppConfiguration
class ReactiveRedisSessionRepositoryITests extends AbstractRedisITests {

	@Autowired
	private ReactiveRedisSessionRepository repository;

	private ReactiveRedisOperations<String, Object> sessionRedisOperations;

	@BeforeEach
	void setup() {
		this.sessionRedisOperations = this.repository.getSessionRedisOperations();
	}

	@Test
	void saves() {
		RedisSession toSave = this.repository.createSession().block();

		String expectedAttributeName = "a";
		String expectedAttributeValue = "b";

		toSave.setAttribute(expectedAttributeName, expectedAttributeValue);
		this.repository.save(toSave).block();

		Session session = this.repository.findById(toSave.getId()).block();

		assertThat(session.getId()).isEqualTo(toSave.getId());
		assertThat(session.getAttributeNames()).isEqualTo(toSave.getAttributeNames());
		assertThat(session.<String>getAttribute(expectedAttributeName))
				.isEqualTo(toSave.getAttribute(expectedAttributeName));

		this.repository.deleteById(toSave.getId()).block();

		assertThat(this.repository.findById(toSave.getId()).block()).isNull();
	}

	@Test
	// gh-1399
	void saveMultipleTimes() {
		RedisSession session = this.repository.createSession().block();
		session.setAttribute("attribute1", "value1");
		Mono<Void> save1 = this.repository.save(session);
		session.setAttribute("attribute2", "value2");
		Mono<Void> save2 = this.repository.save(session);
		Mono.zip(save1, save2).block();
	}

	@Test
	void putAllOnSingleAttrDoesNotRemoveOld() {
		RedisSession toSave = this.repository.createSession().block();
		toSave.setAttribute("a", "b");

		this.repository.save(toSave).block();
		toSave = this.repository.findById(toSave.getId()).block();

		toSave.setAttribute("1", "2");

		this.repository.save(toSave).block();
		toSave = this.repository.findById(toSave.getId()).block();

		Session session = this.repository.findById(toSave.getId()).block();
		assertThat(session.getAttributeNames().size()).isEqualTo(2);
		assertThat(session.<String>getAttribute("a")).isEqualTo("b");
		assertThat(session.<String>getAttribute("1")).isEqualTo("2");

		this.repository.deleteById(toSave.getId()).block();
	}

	@Test
	void changeSessionIdWhenOnlyChangeId() {
		String attrName = "changeSessionId";
		String attrValue = "changeSessionId-value";
		RedisSession toSave = this.repository.createSession().block();
		toSave.setAttribute(attrName, attrValue);

		this.repository.save(toSave).block();

		RedisSession findById = this.repository.findById(toSave.getId()).block();

		assertThat(findById.<String>getAttribute(attrName)).isEqualTo(attrValue);

		String originalFindById = findById.getId();
		String changeSessionId = findById.changeSessionId();

		this.repository.save(findById).block();

		assertThat(this.repository.findById(originalFindById).block()).isNull();

		RedisSession findByChangeSessionId = this.repository.findById(changeSessionId).block();

		assertThat(findByChangeSessionId.<String>getAttribute(attrName)).isEqualTo(attrValue);
	}

	@Test
	void changeSessionIdWhenChangeTwice() {
		RedisSession toSave = this.repository.createSession().block();

		this.repository.save(toSave).block();

		String originalId = toSave.getId();
		String changeId1 = toSave.changeSessionId();
		String changeId2 = toSave.changeSessionId();

		this.repository.save(toSave).block();

		assertThat(this.repository.findById(originalId).block()).isNull();
		assertThat(this.repository.findById(changeId1).block()).isNull();
		assertThat(this.repository.findById(changeId2).block()).isNotNull();
	}

	@Test
	void changeSessionIdWhenSetAttributeOnChangedSession() {
		String attrName = "changeSessionId";
		String attrValue = "changeSessionId-value";

		RedisSession toSave = this.repository.createSession().block();

		this.repository.save(toSave).block();

		RedisSession findById = this.repository.findById(toSave.getId()).block();

		findById.setAttribute(attrName, attrValue);

		String originalFindById = findById.getId();
		String changeSessionId = findById.changeSessionId();

		this.repository.save(findById).block();

		assertThat(this.repository.findById(originalFindById).block()).isNull();

		RedisSession findByChangeSessionId = this.repository.findById(changeSessionId).block();

		assertThat(findByChangeSessionId.<String>getAttribute(attrName)).isEqualTo(attrValue);
	}

	@Test
	void changeSessionIdWhenHasNotSaved() {
		RedisSession toSave = this.repository.createSession().block();
		String originalId = toSave.getId();
		toSave.changeSessionId();

		this.repository.save(toSave).block();

		assertThat(this.repository.findById(toSave.getId()).block()).isNotNull();
		assertThat(this.repository.findById(originalId).block()).isNull();
	}

	// gh-954
	@Test
	void changeSessionIdSaveTwice() {
		RedisSession toSave = this.repository.createSession().block();
		String originalId = toSave.getId();
		toSave.changeSessionId();

		this.repository.save(toSave).block();
		this.repository.save(toSave).block();

		assertThat(this.repository.findById(toSave.getId()).block()).isNotNull();
		assertThat(this.repository.findById(originalId).block()).isNull();
	}

	// gh-1111
	@Test
	void changeSessionSaveOldSessionInstance() {
		RedisSession toSave = this.repository.createSession().block();
		String sessionId = toSave.getId();

		this.repository.save(toSave).block();

		RedisSession session = this.repository.findById(sessionId).block();
		session.changeSessionId();
		session.setLastAccessedTime(Instant.now());
		this.repository.save(session).block();

		toSave.setLastAccessedTime(Instant.now());

		assertThatIllegalStateException().isThrownBy(() -> this.repository.save(toSave).block())
				.withMessage("Session was invalidated");

		assertThat(this.repository.findById(sessionId).block()).isNull();
		assertThat(this.repository.findById(session.getId()).block()).isNotNull();
	}

	// gh-2281
	@Test
	@SuppressWarnings("unchecked")
	void saveChangeSessionIdAfterCheckWhenOriginalKeyDoesNotExistsThenIgnoreError() {
		ReactiveRedisOperations<String, Object> sessionRedisOperations = (ReactiveRedisOperations<String, Object>) ReflectionTestUtils
				.getField(this.repository, "sessionRedisOperations");
		ReactiveRedisOperations<String, Object> spyOperations = spy(sessionRedisOperations);
		ReflectionTestUtils.setField(this.repository, "sessionRedisOperations", spyOperations);

		RedisSession toSave = this.repository.createSession().block();
		String sessionId = toSave.getId();

		given(spyOperations.hasKey(endsWith(sessionId))).willReturn(Mono.just(true));

		this.repository.save(toSave).block();
		RedisSession session = this.repository.findById(sessionId).block();
		this.repository.deleteById(sessionId).block();
		String newSessionId = session.changeSessionId();
		this.repository.save(session).block();
		assertThat(this.repository.findById(sessionId).block()).isNull();
		assertThat(this.repository.findById(newSessionId).block()).isNull();
		reset(spyOperations);
	}

	// gh-2464
	@Test
	void saveWhenPutAllIsDelayedThenExpireShouldBeSet() {
		ReactiveRedisOperations<String, Object> spy = spy(this.sessionRedisOperations);
		ReflectionTestUtils.setField(this.repository, "sessionRedisOperations", spy);
		ReactiveHashOperations<String, Object, Object> opsForHash = spy(this.sessionRedisOperations.opsForHash());
		given(spy.opsForHash()).willReturn(opsForHash);
		willAnswer((invocation) -> Mono.delay(Duration.ofSeconds(1)).then((Mono<Void>) invocation.callRealMethod()))
				.given(opsForHash).putAll(anyString(), any());
		RedisSession toSave = this.repository.createSession().block();

		String expectedAttributeName = "a";
		String expectedAttributeValue = "b";

		toSave.setAttribute(expectedAttributeName, expectedAttributeValue);
		this.repository.save(toSave).block();

		String id = toSave.getId();
		Duration expireDuration = this.sessionRedisOperations.getExpire("spring:session:sessions:" + id).block();

		assertThat(expireDuration).isNotEqualTo(Duration.ZERO);
		reset(spy);
	}

	@Configuration
	@EnableRedisWebSession
	static class Config extends BaseConfig {

	}

}
