/*
 * Copyright 2014-2016 the original author or authors.
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

import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.session.ExpiringSession;
import org.springframework.session.SessionRepository;
import org.springframework.session.events.SessionExpiredEvent;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration
@WebAppConfiguration
public class EnableRedisHttpSessionExpireSessionDestroyedTests<S extends ExpiringSession> {
	@Autowired
	private SessionRepository<S> repository;

	@Autowired
	private SessionExpiredEventRegistry registry;

	private final Object lock = new Object();

	@Before
	public void setup() {
		this.registry.setLock(this.lock);
	}

	@Test
	public void expireFiresSessionExpiredEvent() throws InterruptedException {
		S toSave = this.repository.createSession();
		toSave.setAttribute("a", "b");
		Authentication toSaveToken = new UsernamePasswordAuthenticationToken("user",
				"password", AuthorityUtils.createAuthorityList("ROLE_USER"));
		SecurityContext toSaveContext = SecurityContextHolder.createEmptyContext();
		toSaveContext.setAuthentication(toSaveToken);
		toSave.setAttribute("SPRING_SECURITY_CONTEXT", toSaveContext);

		this.repository.save(toSave);

		synchronized (this.lock) {
			this.lock.wait((toSave.getMaxInactiveIntervalInSeconds() * 1000) + 1);
		}
		if (!this.registry.receivedEvent()) {
			// Redis makes no guarantees on when an expired event will be fired
			// we can ensure it gets fired by trying to get the session
			this.repository.getSession(toSave.getId());
			synchronized (this.lock) {
				if (!this.registry.receivedEvent()) {
					// wait at most a minute
					this.lock.wait(TimeUnit.MINUTES.toMillis(1));
				}
			}
		}
		assertThat(this.registry.receivedEvent()).isTrue();
	}

	static class SessionExpiredEventRegistry
			implements ApplicationListener<SessionExpiredEvent> {
		private boolean receivedEvent;
		private Object lock;

		public void onApplicationEvent(SessionExpiredEvent event) {
			synchronized (this.lock) {
				this.receivedEvent = true;
				this.lock.notifyAll();
			}
		}

		public boolean receivedEvent() {
			return this.receivedEvent;
		}

		public void setLock(Object lock) {
			this.lock = lock;
		}
	}

	@Configuration
	@EnableRedisHttpSession(maxInactiveIntervalInSeconds = 1)
	static class Config {
		@Bean
		public JedisConnectionFactory connectionFactory() throws Exception {
			JedisConnectionFactory factory = new JedisConnectionFactory();
			factory.setUsePool(false);
			return factory;
		}

		@Bean
		public SessionExpiredEventRegistry sessionDestroyedEventRegistry() {
			return new SessionExpiredEventRegistry();
		}
	}
}
