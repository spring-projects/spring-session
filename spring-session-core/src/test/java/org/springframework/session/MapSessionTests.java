/*
 * Copyright 2014-2017 the original author or authors.
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

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class MapSessionTests {

	private MapSession session;

	@Before
	public void setup() {
		this.session = new MapSession();
		this.session.setLastAccessedTime(Instant.ofEpochMilli(1413258262962L));
	}

	@Test(expected = IllegalArgumentException.class)
	public void constructorNullSession() {
		new MapSession((Session) null);
	}

	@Test
	public void getAttributeWhenNullThenNull() {
		String result = this.session.getAttribute("attrName");
		assertThat(result).isNull();
	}

	@Test
	public void getAttributeOrDefaultWhenNullThenDefaultValue() {
		String defaultValue = "default";
		String result = this.session.getAttributeOrDefault("attrName", defaultValue);
		assertThat(result).isEqualTo(defaultValue);
	}

	@Test
	public void getAttributeOrDefaultWhenNotNullThenDefaultValue() {
		String defaultValue = "default";
		String attrValue = "value";
		String attrName = "attrName";
		this.session.setAttribute(attrName, attrValue);

		String result = this.session.getAttributeOrDefault(attrName, defaultValue);

		assertThat(result).isEqualTo(attrValue);
	}

	@Test(expected = IllegalArgumentException.class)
	public void getRequiredAttributeWhenNullThenException() {
		this.session.getRequiredAttribute("attrName");
	}

	@Test
	public void getRequiredAttributeWhenNotNullThenReturns() {
		String attrValue = "value";
		String attrName = "attrName";
		this.session.setAttribute(attrName, attrValue);

		String result = this.session.getRequiredAttribute("attrName");

		assertThat(result).isEqualTo(attrValue);
	}

	/**
	 * Ensure conforms to the javadoc of {@link Session}
	 */
	@Test
	public void setAttributeNullObjectRemoves() {
		String attr = "attr";
		this.session.setAttribute(attr, new Object());
		this.session.setAttribute(attr, null);
		assertThat(this.session.getAttributeNames()).isEmpty();
	}

	@Test
	public void equalsNonSessionFalse() {
		assertThat(this.session.equals(new Object())).isFalse();
	}

	@Test
	public void equalsCustomSession() {
		CustomSession other = new CustomSession();
		this.session.setId(other.getId());
		assertThat(this.session.equals(other)).isTrue();
	}

	@Test
	public void hashCodeEqualsIdHashCode() {
		this.session.setId("constantId");
		assertThat(this.session.hashCode()).isEqualTo(this.session.getId().hashCode());
	}

	@Test
	public void isExpiredExact() {
		Instant now = Instant.ofEpochMilli(1413260062962L);
		assertThat(this.session.isExpired(now)).isTrue();
	}

	@Test
	public void isExpiredOneMsTooSoon() {
		Instant now = Instant.ofEpochMilli(1413260062961L);
		assertThat(this.session.isExpired(now)).isFalse();
	}

	@Test
	public void isExpiredOneMsAfter() {
		Instant now = Instant.ofEpochMilli(1413260062963L);
		assertThat(this.session.isExpired(now)).isTrue();
	}

	static class CustomSession implements Session {

		public Instant getCreationTime() {
			return Instant.EPOCH;
		}

		public String changeSessionId() {
			throw new UnsupportedOperationException();
		}

		public String getId() {
			return "id";
		}

		public void setLastAccessedTime(Instant lastAccessedTime) {
			throw new UnsupportedOperationException();
		}

		public Instant getLastAccessedTime() {
			return Instant.EPOCH;
		}

		public void setMaxInactiveInterval(Duration interval) {

		}

		public Duration getMaxInactiveInterval() {
			return Duration.ZERO;
		}

		public <T> T getAttribute(String attributeName) {
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
