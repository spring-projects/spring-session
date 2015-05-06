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
package org.springframework.session;

import static org.fest.assertions.Assertions.assertThat;

import java.util.Set;

import org.junit.Before;
import org.junit.Test;

public class MapSessionTests {
	
	private static final String SESSION_ID="826653e3-8220-48d5-8f2c-e4e2f3c78e99";

	private MapSession session;

	@Before
	public void setup() {
		session = new MapSession(SESSION_ID);
		session.setLastAccessedTime(1413258262962L);
	}

	@Test(expected = IllegalArgumentException.class)
	public void constructorNullSession() {
		new MapSession((ExpiringSession) null);
	}

	/**
	 * Ensure conforms to the javadoc of {@link Session}
	 */
	@Test
	public void setAttributeNullObjectRemoves() {
		String attr = "attr";
		session.setAttribute(attr, new Object());
		session.setAttribute(attr, null);
		assertThat(session.getAttributeNames()).isEmpty();
	}

	@Test
	public void equalsNonSessionFalse() {
		assertThat(session.equals(new Object())).isFalse();
	}

	@Test
	public void equalsCustomSession() {
		CustomSession other = new CustomSession();
		session.setId(other.getId());
		assertThat(session.equals(other)).isTrue();
	}

	@Test
	public void hashCodeEqualsIdHashCode() {
		session.setId("constantId");
		assertThat(session.hashCode()).isEqualTo(session.getId().hashCode());
	}

	@Test
	public void isExpiredExact() {
		long now = 1413260062962L;
		assertThat(session.isExpired(now)).isTrue();
	}

	@Test
	public void isExpiredOneMsTooSoon() {
		long now = 1413260062961L;
		assertThat(session.isExpired(now)).isFalse();
	}

	@Test
	public void isExpiredOneMsAfter() {
		long now = 1413260062963L;
		assertThat(session.isExpired(now)).isTrue();
	}

	static class CustomSession implements ExpiringSession {

		public long getCreationTime() {
			return 0;
		}

		public String getId() {
			return "id";
		}

		public long getLastAccessedTime() {
			return 0;
		}

		public void setMaxInactiveIntervalInSeconds(int interval) {

		}

		public int getMaxInactiveIntervalInSeconds() {
			return 0;
		}

		public Object getAttribute(String attributeName) {
			return null;
		}

		public Set<String> getAttributeNames() {
			return null;
		}

		public void setAttribute(String attributeName, Object attributeValue) {

		}

		public void removeAttribute(String attributeName) {

		}

		public boolean isExpired() {
			return false;
		}
	}

}