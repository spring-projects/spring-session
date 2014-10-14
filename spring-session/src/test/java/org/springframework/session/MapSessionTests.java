package org.springframework.session;

import static org.fest.assertions.Assertions.assertThat;

import java.util.Set;

import org.junit.Before;
import org.junit.Test;

public class MapSessionTests {

    private MapSession session;

    @Before
    public void setup() {
        session = new MapSession();
        session.setLastAccessedTime(1413258262962L);
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

        @Override
        public boolean isExpired() {
            return false;
        }
    }

}