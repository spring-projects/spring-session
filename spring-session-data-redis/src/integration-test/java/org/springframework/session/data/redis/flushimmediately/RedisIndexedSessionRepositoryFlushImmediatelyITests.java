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

package org.springframework.session.data.redis.flushimmediately;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.FlushMode;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.session.data.redis.AbstractRedisITests;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;

import static org.assertj.core.api.Assertions.assertThat;

@SpringJUnitWebConfig
class RedisIndexedSessionRepositoryFlushImmediatelyITests<S extends Session> extends AbstractRedisITests {

	@Autowired
	private SessionRepository<S> sessionRepository;

	@Test
	void savesOnCreate() {
		S created = this.sessionRepository.createSession();

		S getSession = this.sessionRepository.findById(created.getId());

		assertThat(getSession).isNotNull();
	}

	@Configuration
	@EnableRedisHttpSession(flushMode = FlushMode.IMMEDIATE)
	static class RedisHttpSessionConfig extends BaseConfig {

	}

}
