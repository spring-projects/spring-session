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

package org.springframework.session;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.Test;

import org.springframework.beans.DirectFieldAccessor;

import static org.assertj.core.api.Assertions.assertThat;

public class MapSessionRepositoryTests {
	MockMapSessionRepository repository;

	MapSession session;

	@Before
	public void setup() {
		this.repository = new MockMapSessionRepository();
		this.session = new MapSession();
	}

	@Test
	public void getSessionExpired() {
		this.session.setMaxInactiveIntervalInSeconds(1);
		this.session.setLastAccessedTime(
				System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(5));
		this.repository.save(this.session);

		assertThat(this.repository.getSession(this.session.getId())).isNull();
	}

	@Test
	public void createSessionDefaultExpiration() {
		ExpiringSession session = this.repository.createSession();

		assertThat(session).isInstanceOf(MapSession.class);
		assertThat(session.getMaxInactiveIntervalInSeconds())
				.isEqualTo(new MapSession().getMaxInactiveIntervalInSeconds());
	}

	@Test
	public void createSessionCustomDefaultExpiration() {
		final int expectedMaxInterval = new MapSession().getMaxInactiveIntervalInSeconds()
				+ 10;
		this.repository.setDefaultMaxInactiveInterval(expectedMaxInterval);

		ExpiringSession session = this.repository.createSession();

		assertThat(session.getMaxInactiveIntervalInSeconds())
				.isEqualTo(expectedMaxInterval);
	}

	@Test
	public void createAndNotPersistOnSessionChange() {
		MapSession session = this.repository.createSession();

		this.repository.save(session);

		assertThat(this.repository.getSessions().size()).isEqualTo(1);
	}

	@Test
	public void createAndPersistOnSessionChange() {
		this.repository.setPersistOnSessionChange(true);
		MapSession session = this.repository.createSession();

		assertThat(session.isChanged()).isTrue();
		this.repository.save(session);
		assertThat(session.isChanged()).isFalse();
	}

	@Test
	public void modifyAndPersistOnSessionChange() {
		this.repository.setPersistOnSessionChange(true);
		MapSession session = this.repository.createSession();

		assertThat(session.isChanged()).isTrue();
		this.repository.save(session);
		assertThat(session.isChanged()).isFalse();

		session.setAttribute("foo", "bar");
		assertThat(session.isChanged()).isTrue();
		this.repository.save(session);
		assertThat(session.isChanged()).isFalse();
	}

	static class MockMapSessionRepository extends MapSessionRepository {

		public Map<String, MapSession> getSessions() {
			return (Map<String, MapSession>) new DirectFieldAccessor(this)
					.getPropertyValue("sessions");
		}

	}

}
