package org.springframework.session;

import org.junit.Before;
import org.junit.Test;

import java.util.Set;

import static org.fest.assertions.Assertions.assertThat;

public class MapSessionTests {

	private MapSession session;

	@Before
	public void setup() {
		session = new MapSession();
	}

	@Test(expected = IllegalArgumentException.class)
	public void constructorNullSession() {
		new MapSession(null);
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

	static class CustomSession implements Session {

		@Override
		public long getCreationTime() {
			return 0;
		}

		@Override
		public String getId() {
			return "id";
		}

		@Override
		public long getLastAccessedTime() {
			return 0;
		}

		@Override
		public void setMaxInactiveInterval(int interval) {

		}

		@Override
		public int getMaxInactiveInterval() {
			return 0;
		}

		@Override
		public Object getAttribute(String attributeName) {
			return null;
		}

		@Override
		public Set<String> getAttributeNames() {
			return null;
		}

		@Override
		public void setAttribute(String attributeName, Object attributeValue) {

		}

		@Override
		public void removeAttribute(String attributeName) {

		}
	}

}