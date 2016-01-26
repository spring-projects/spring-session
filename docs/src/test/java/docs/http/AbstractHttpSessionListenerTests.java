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
package docs.http;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationListener;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.security.core.session.SessionDestroyedEvent;
import org.springframework.session.MapSession;
import org.springframework.session.Session;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;

/**
 * @author Rob Winch
 * @since 1.2
 */
@RunWith(SpringJUnit4ClassRunner.class)
@WebAppConfiguration
public abstract class AbstractHttpSessionListenerTests {
	@Autowired
	ApplicationEventPublisher publisher;

	@Autowired
	SecuritySessionDestroyedListener listener;

	@Test
	public void springSessionDestroyedTranslatedToSpringSecurityDestroyed() {
		Session session = new MapSession();

		publisher.publishEvent(new org.springframework.session.events.SessionDestroyedEvent(this, session));

		assertThat(listener.getEvent().getId()).isEqualTo(session.getId());
	}

	static RedisConnectionFactory createMockRedisConnection() {
		RedisConnectionFactory factory = mock(RedisConnectionFactory.class);
		RedisConnection connection = mock(RedisConnection.class);

		when(factory.getConnection()).thenReturn(connection);
		return factory;
	}

	static class SecuritySessionDestroyedListener implements ApplicationListener<SessionDestroyedEvent> {

		private SessionDestroyedEvent event;

		/* (non-Javadoc)
		 * @see org.springframework.context.ApplicationListener#onApplicationEvent(org.springframework.context.ApplicationEvent)
		 */
		@Override
		public void onApplicationEvent(SessionDestroyedEvent event) {
			this.event = event;
		}

		public SessionDestroyedEvent getEvent() {
			return event;
		}
	}
}
