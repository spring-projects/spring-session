/*
 * Copyright 2002-2015 the original author or authors.
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

import static org.fest.assertions.Assertions.assertThat;

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
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession;
import org.springframework.session.events.AbstractSessionEvent;
import org.springframework.session.events.SessionCreatedEvent;
import org.springframework.session.events.SessionDestroyedEvent;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration
@WebAppConfiguration
public class RedisOperationsSessionRepositoryITests<S extends Session> {
	@Autowired
	private SessionRepository<S> repository;

	@Autowired
	private SessionEventRegistry registry;

	@Test
	public void saves() throws InterruptedException {
		S toSave = repository.createSession();
		String expectedAttributeName = "a";
		String expectedAttributeValue = "b";
		toSave.setAttribute(expectedAttributeName, expectedAttributeValue);
		Authentication toSaveToken = new UsernamePasswordAuthenticationToken("user","password", AuthorityUtils.createAuthorityList("ROLE_USER"));
		SecurityContext toSaveContext = SecurityContextHolder.createEmptyContext();
		toSaveContext.setAuthentication(toSaveToken);
		toSave.setAttribute("SPRING_SECURITY_CONTEXT", toSaveContext);
		registry.clear();

		repository.save(toSave);

		assertThat(registry.receivedEvent()).isTrue();
		assertThat(registry.getEvent()).isInstanceOf(SessionCreatedEvent.class);

		Session session = repository.getSession(toSave.getId());

		assertThat(session.getId()).isEqualTo(toSave.getId());
		assertThat(session.getAttributeNames()).isEqualTo(session.getAttributeNames());
		assertThat(session.getAttribute(expectedAttributeName)).isEqualTo(toSave.getAttribute(expectedAttributeName));

		registry.clear();

		repository.delete(toSave.getId());

		assertThat(repository.getSession(toSave.getId())).isNull();
		assertThat(registry.getEvent()).isInstanceOf(SessionDestroyedEvent.class);


		assertThat(registry.getEvent().getSession().getAttribute(expectedAttributeName)).isEqualTo(expectedAttributeValue);
	}

	@Test
	public void putAllOnSingleAttrDoesNotRemoveOld() {
		S toSave = repository.createSession();
		toSave.setAttribute("a", "b");

		repository.save(toSave);
		toSave = repository.getSession(toSave.getId());

		toSave.setAttribute("1", "2");

		repository.save(toSave);
		toSave = repository.getSession(toSave.getId());

		Session session = repository.getSession(toSave.getId());
		assertThat(session.getAttributeNames().size()).isEqualTo(2);
		assertThat(session.getAttribute("a")).isEqualTo("b");
		assertThat(session.getAttribute("1")).isEqualTo("2");
	}

	static class SessionEventRegistry implements ApplicationListener<AbstractSessionEvent> {
		private AbstractSessionEvent event;
		private final Object lock = new Object();

		public void onApplicationEvent(AbstractSessionEvent event) {
			this.event = event;
			synchronized (lock) {
				lock.notifyAll();
			}
		}

		public void clear() {
			this.event = null;
		}

		public boolean receivedEvent() throws InterruptedException {
			return waitForEvent() != null;
		}

		@SuppressWarnings("unchecked")
		public <E extends AbstractSessionEvent> E getEvent() throws InterruptedException {
			return (E) waitForEvent();
		}

		@SuppressWarnings("unchecked")
		private <E extends AbstractSessionEvent> E waitForEvent() throws InterruptedException {
			synchronized(lock) {
				if(event == null) {
					lock.wait(3000);
				}
			}
			return (E) event;
		}
	}

	@Configuration
	@EnableRedisHttpSession
	static class Config {
		@Bean
		public JedisConnectionFactory connectionFactory() throws Exception {
			JedisConnectionFactory factory = new JedisConnectionFactory();
			factory.setUsePool(false);
			return factory;
		}

		@Bean
		public SessionEventRegistry sessionEventRegistry() {
			return new SessionEventRegistry();
		}
	}
}