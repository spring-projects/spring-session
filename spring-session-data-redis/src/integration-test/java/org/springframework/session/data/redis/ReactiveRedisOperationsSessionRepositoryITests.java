/*
 * Copyright 2014-2018 the original author or authors.
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

package org.springframework.session.data.redis;

import java.time.Instant;

import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.Session;
import org.springframework.session.data.redis.config.annotation.web.server.EnableRedisWebSession;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.web.WebAppConfiguration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Integration tests for {@link ReactiveRedisOperationsSessionRepository}.
 *
 * @author Vedran Pavic
 */
@RunWith(SpringRunner.class)
@ContextConfiguration
@WebAppConfiguration
public class ReactiveRedisOperationsSessionRepositoryITests extends AbstractRedisITests {

	@Autowired
	private ReactiveRedisOperationsSessionRepository repository;

	@Test
	public void saves() throws InterruptedException {
		ReactiveRedisOperationsSessionRepository.RedisSession toSave = this.repository
				.createSession().block();

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
	public void putAllOnSingleAttrDoesNotRemoveOld() {
		ReactiveRedisOperationsSessionRepository.RedisSession toSave = this.repository
				.createSession().block();
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
	public void changeSessionIdWhenOnlyChangeId() throws Exception {
		String attrName = "changeSessionId";
		String attrValue = "changeSessionId-value";
		ReactiveRedisOperationsSessionRepository.RedisSession toSave = this.repository
				.createSession().block();
		toSave.setAttribute(attrName, attrValue);

		this.repository.save(toSave).block();

		ReactiveRedisOperationsSessionRepository.RedisSession findById = this.repository
				.findById(toSave.getId()).block();

		assertThat(findById.<String>getAttribute(attrName)).isEqualTo(attrValue);

		String originalFindById = findById.getId();
		String changeSessionId = findById.changeSessionId();

		this.repository.save(findById).block();

		assertThat(this.repository.findById(originalFindById).block()).isNull();

		ReactiveRedisOperationsSessionRepository.RedisSession findByChangeSessionId = this.repository
				.findById(changeSessionId).block();

		assertThat(findByChangeSessionId.<String>getAttribute(attrName))
				.isEqualTo(attrValue);
	}

	@Test
	public void changeSessionIdWhenChangeTwice() throws Exception {
		ReactiveRedisOperationsSessionRepository.RedisSession toSave = this.repository
				.createSession().block();

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
	public void changeSessionIdWhenSetAttributeOnChangedSession() throws Exception {
		String attrName = "changeSessionId";
		String attrValue = "changeSessionId-value";

		ReactiveRedisOperationsSessionRepository.RedisSession toSave = this.repository
				.createSession().block();

		this.repository.save(toSave).block();

		ReactiveRedisOperationsSessionRepository.RedisSession findById = this.repository
				.findById(toSave.getId()).block();

		findById.setAttribute(attrName, attrValue);

		String originalFindById = findById.getId();
		String changeSessionId = findById.changeSessionId();

		this.repository.save(findById).block();

		assertThat(this.repository.findById(originalFindById).block()).isNull();

		ReactiveRedisOperationsSessionRepository.RedisSession findByChangeSessionId = this.repository
				.findById(changeSessionId).block();

		assertThat(findByChangeSessionId.<String>getAttribute(attrName))
				.isEqualTo(attrValue);
	}

	@Test
	public void changeSessionIdWhenHasNotSaved() throws Exception {
		ReactiveRedisOperationsSessionRepository.RedisSession toSave = this.repository
				.createSession().block();
		String originalId = toSave.getId();
		toSave.changeSessionId();

		this.repository.save(toSave).block();

		assertThat(this.repository.findById(toSave.getId()).block()).isNotNull();
		assertThat(this.repository.findById(originalId).block()).isNull();
	}

	// gh-954
	@Test
	public void changeSessionIdSaveTwice() {
		ReactiveRedisOperationsSessionRepository.RedisSession toSave = this.repository
				.createSession().block();
		String originalId = toSave.getId();
		toSave.changeSessionId();

		this.repository.save(toSave).block();
		this.repository.save(toSave).block();

		assertThat(this.repository.findById(toSave.getId()).block()).isNotNull();
		assertThat(this.repository.findById(originalId).block()).isNull();
	}

	// gh-1111
	@Test
	public void changeSessionSaveOldSessionInstance() {
		ReactiveRedisOperationsSessionRepository.RedisSession toSave = this.repository
				.createSession().block();
		String sessionId = toSave.getId();

		this.repository.save(toSave).block();

		ReactiveRedisOperationsSessionRepository.RedisSession session = this.repository
				.findById(sessionId).block();
		session.changeSessionId();
		session.setLastAccessedTime(Instant.now());
		this.repository.save(session).block();

		toSave.setLastAccessedTime(Instant.now());

		assertThatExceptionOfType(IllegalStateException.class)
				.isThrownBy(() -> this.repository.save(toSave).block())
				.withMessage("Session was invalidated");

		assertThat(this.repository.findById(sessionId).block()).isNull();
		assertThat(this.repository.findById(session.getId()).block()).isNotNull();
	}

	@Configuration
	@EnableRedisWebSession
	static class Config extends BaseConfig {

	}

}
