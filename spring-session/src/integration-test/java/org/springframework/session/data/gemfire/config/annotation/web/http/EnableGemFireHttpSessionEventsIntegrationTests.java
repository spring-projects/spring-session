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

package org.springframework.session.data.gemfire.config.annotation.web.http;

import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import com.gemstone.gemfire.cache.DataPolicy;
import com.gemstone.gemfire.cache.ExpirationAction;
import com.gemstone.gemfire.cache.Region;
import com.gemstone.gemfire.cache.RegionShortcut;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.data.gemfire.CacheFactoryBean;
import org.springframework.session.ExpiringSession;
import org.springframework.session.data.gemfire.AbstractGemFireIntegrationTests;
import org.springframework.session.data.gemfire.support.GemFireUtils;
import org.springframework.session.events.AbstractSessionEvent;
import org.springframework.session.events.SessionCreatedEvent;
import org.springframework.session.events.SessionDeletedEvent;
import org.springframework.session.events.SessionExpiredEvent;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The EnableGemFireHttpSessionEventsIntegrationTests class is a test suite of test cases
 * testing the Session Event functionality and behavior of the
 * GemFireOperationsSessionRepository and GemFire's configuration.
 *
 * @author John Blum
 * @since 1.1.0
 * @see org.junit.Test
 * @see org.junit.runner.RunWith
 * @see org.springframework.session.ExpiringSession
 * @see org.springframework.session.data.gemfire.AbstractGemFireIntegrationTests
 * @see org.springframework.session.data.gemfire.GemFireOperationsSessionRepository
 * @see org.springframework.session.events.SessionCreatedEvent
 * @see org.springframework.session.events.SessionDeletedEvent
 * @see org.springframework.session.events.SessionExpiredEvent
 * @see org.springframework.test.annotation.DirtiesContext
 * @see org.springframework.test.context.ContextConfiguration
 * @see org.springframework.test.context.junit4.SpringJUnit4ClassRunner
 * @see org.springframework.test.context.web.WebAppConfiguration
 * @see com.gemstone.gemfire.cache.Region
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration
@DirtiesContext
@WebAppConfiguration
public class EnableGemFireHttpSessionEventsIntegrationTests
		extends AbstractGemFireIntegrationTests {

	private static final int MAX_INACTIVE_INTERVAL_IN_SECONDS = 1;

	private static final String GEMFIRE_LOG_LEVEL = "warning";
	private static final String SPRING_SESSION_GEMFIRE_REGION_NAME = "TestReplicatedSessions";

	@Autowired
	private SessionEventListener sessionEventListener;

	@Before
	public void setup() {
		assertThat(GemFireUtils.isPeer(this.gemfireCache)).isTrue();
		assertThat(this.gemfireSessionRepository).isNotNull();
		assertThat(this.gemfireSessionRepository.getMaxInactiveIntervalInSeconds())
				.isEqualTo(MAX_INACTIVE_INTERVAL_IN_SECONDS);
		assertThat(this.sessionEventListener).isNotNull();

		Region<Object, ExpiringSession> sessionRegion = this.gemfireCache
				.getRegion(SPRING_SESSION_GEMFIRE_REGION_NAME);

		assertRegion(sessionRegion, SPRING_SESSION_GEMFIRE_REGION_NAME,
				DataPolicy.REPLICATE);
		assertEntryIdleTimeout(sessionRegion, ExpirationAction.INVALIDATE,
				MAX_INACTIVE_INTERVAL_IN_SECONDS);
	}

	@After
	public void tearDown() {
		this.sessionEventListener.getSessionEvent();
	}

	@Test
	public void sessionCreatedEvent() {
		final long beforeOrAtCreationTime = System.currentTimeMillis();

		ExpiringSession expectedSession = save(createSession());

		AbstractSessionEvent sessionEvent = this.sessionEventListener.getSessionEvent();

		assertThat(sessionEvent).isInstanceOf(SessionCreatedEvent.class);

		ExpiringSession createdSession = sessionEvent.getSession();

		assertThat(createdSession).isEqualTo(expectedSession);
		assertThat(createdSession.getId()).isNotNull();
		assertThat(createdSession.getCreationTime())
				.isGreaterThanOrEqualTo(beforeOrAtCreationTime);
		assertThat(createdSession.getLastAccessedTime())
				.isEqualTo(createdSession.getCreationTime());
		assertThat(createdSession.getMaxInactiveIntervalInSeconds())
				.isEqualTo(MAX_INACTIVE_INTERVAL_IN_SECONDS);
		assertThat(createdSession.isExpired()).isFalse();
	}

	@Test
	public void getExistingNonExpiredSession() {
		ExpiringSession expectedSession = save(touch(createSession()));

		assertThat(expectedSession.isExpired()).isFalse();

		// NOTE though unlikely, a possible race condition exists between save and get...
		ExpiringSession savedSession = this.gemfireSessionRepository
				.getSession(expectedSession.getId());

		assertThat(savedSession).isEqualTo(expectedSession);
	}

	@Test
	public void getExistingExpiredSession() {
		ExpiringSession expectedSession = save(expire(createSession()));

		AbstractSessionEvent sessionEvent = this.sessionEventListener.getSessionEvent();

		assertThat(sessionEvent).isInstanceOf(SessionCreatedEvent.class);

		ExpiringSession createdSession = sessionEvent.getSession();

		assertThat(createdSession).isEqualTo(expectedSession);
		assertThat(createdSession.isExpired()).isTrue();
		assertThat(this.gemfireSessionRepository.getSession(createdSession.getId()))
				.isNull();
	}

	@Test
	public void getNonExistingSession() {
		assertThat(this.gemfireSessionRepository.getSession(UUID.randomUUID().toString()))
				.isNull();
	}

	@Test
	public void deleteExistingNonExpiredSession() {
		ExpiringSession expectedSession = save(touch(createSession()));
		ExpiringSession savedSession = this.gemfireSessionRepository
				.getSession(expectedSession.getId());

		assertThat(savedSession).isEqualTo(expectedSession);
		assertThat(savedSession.isExpired()).isFalse();

		this.gemfireSessionRepository.delete(savedSession.getId());

		AbstractSessionEvent sessionEvent = this.sessionEventListener.getSessionEvent();

		assertThat(sessionEvent).isInstanceOf(SessionDeletedEvent.class);
		assertThat(sessionEvent.getSessionId()).isEqualTo(savedSession.getId());

		ExpiringSession deletedSession = sessionEvent.getSession();

		assertThat(deletedSession).isEqualTo(savedSession);
		assertThat(this.gemfireSessionRepository.getSession(deletedSession.getId()))
				.isNull();
	}

	@Test
	public void deleteExistingExpiredSession() {
		ExpiringSession expectedSession = save(createSession());

		AbstractSessionEvent sessionEvent = this.sessionEventListener.getSessionEvent();

		assertThat(sessionEvent).isInstanceOf(SessionCreatedEvent.class);

		ExpiringSession createdSession = sessionEvent.getSession();

		assertThat(createdSession).isEqualTo(expectedSession);

		sessionEvent = this.sessionEventListener.waitForSessionEvent(TimeUnit.SECONDS
				.toMillis(this.gemfireSessionRepository.getMaxInactiveIntervalInSeconds()
						+ 1));

		assertThat(sessionEvent).isInstanceOf(SessionExpiredEvent.class);

		ExpiringSession expiredSession = sessionEvent.getSession();

		assertThat(expiredSession).isEqualTo(createdSession);
		assertThat(expiredSession.isExpired()).isTrue();

		this.gemfireSessionRepository.delete(expectedSession.getId());

		sessionEvent = this.sessionEventListener.getSessionEvent();

		assertThat(sessionEvent).isInstanceOf(SessionDeletedEvent.class);
		assertThat(sessionEvent.getSession()).isNull();
		assertThat(sessionEvent.getSessionId()).isEqualTo(expiredSession.getId());
		assertThat(this.gemfireSessionRepository.getSession(sessionEvent.getSessionId()))
				.isNull();
	}

	@Test
	public void deleteNonExistingSession() {
		String expectedSessionId = UUID.randomUUID().toString();

		assertThat(this.gemfireSessionRepository.getSession(expectedSessionId)).isNull();

		this.gemfireSessionRepository.delete(expectedSessionId);

		AbstractSessionEvent sessionEvent = this.sessionEventListener.getSessionEvent();

		assertThat(sessionEvent).isInstanceOf(SessionDeletedEvent.class);
		assertThat(sessionEvent.getSession()).isNull();
		assertThat(sessionEvent.getSessionId()).isEqualTo(expectedSessionId);
	}

	@EnableGemFireHttpSession(regionName = SPRING_SESSION_GEMFIRE_REGION_NAME, maxInactiveIntervalInSeconds = MAX_INACTIVE_INTERVAL_IN_SECONDS, serverRegionShortcut = RegionShortcut.REPLICATE)
	static class SpringSessionGemFireConfiguration {

		@Bean
		Properties gemfireProperties() {
			Properties gemfireProperties = new Properties();

			gemfireProperties.setProperty("name",
					EnableGemFireHttpSessionEventsIntegrationTests.class.getName());
			gemfireProperties.setProperty("mcast-port", "0");
			gemfireProperties.setProperty("log-level", GEMFIRE_LOG_LEVEL);

			return gemfireProperties;
		}

		@Bean
		CacheFactoryBean gemfireCache() {
			CacheFactoryBean gemfireCache = new CacheFactoryBean();

			gemfireCache.setClose(true);
			gemfireCache.setProperties(gemfireProperties());

			return gemfireCache;
		}

		@Bean
		SessionEventListener sessionEventListener() {
			return new SessionEventListener();
		}
	}

}
