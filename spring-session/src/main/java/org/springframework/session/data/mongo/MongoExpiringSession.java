package org.springframework.session.data.mongo;

import org.springframework.session.ExpiringSession;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Session object providing additional information about
 * the datetime of expiration
 *
 * @author Jakub Kubrynski
 */
public class MongoExpiringSession implements ExpiringSession {

	private static final int DEFAULT_INTERVAL_IN_SECONDS = 1800;

	public static final String ID = "_id";
	public static final String CREATION_TIME = "created";
	public static final String LAST_ACCESSED_TIME = "accessed";
	public static final String MAX_INTERVAL = "interval";
	public static final String ATTRIBUTES = "attr";

	private final String id;
	private long created = System.currentTimeMillis();
	private long accessed;
	private int interval;
	private Date expireAt;
	private Map<String, Object> attrs = new HashMap<String, Object>();

	public MongoExpiringSession() {
		this(DEFAULT_INTERVAL_IN_SECONDS);
	}

	public MongoExpiringSession(int maxInactiveIntervalInSeconds) {
		this(UUID.randomUUID().toString(), maxInactiveIntervalInSeconds);
	}

	public MongoExpiringSession(String id, int maxInactiveIntervalInSeconds) {
		this.id = id;
		this.interval = maxInactiveIntervalInSeconds;
		setLastAccessedTime(created);
	}

	public String getId() {
		return id;
	}

	@SuppressWarnings("unchecked")
	public <T> T getAttribute(String attributeName) {
		return (T) attrs.get(attributeName);
	}

	public Set<String> getAttributeNames() {
		return attrs.keySet();
	}

	public void setAttribute(String attributeName, Object attributeValue) {
		if (attributeValue == null) {
			removeAttribute(attributeName);
		} else {
			attrs.put(attributeName, attributeValue);
		}
	}

	public void removeAttribute(String attributeName) {
		attrs.remove(attributeName);
	}

	public long getCreationTime() {
		return created;
	}

	public void setCreationTime(long created) {
		this.created = created;
	}

	public void setLastAccessedTime(long lastAccessedTime) {
		this.accessed = lastAccessedTime;
		expireAt = new Date(lastAccessedTime + TimeUnit.SECONDS.toMillis(interval));
	}

	public long getLastAccessedTime() {
		return accessed;
	}

	public void setMaxInactiveIntervalInSeconds(int interval) {
		this.interval = interval;
	}

	public int getMaxInactiveIntervalInSeconds() {
		return interval;
	}

	public boolean isExpired() {
		return new Date().after(expireAt);
	}

	public Date getExpireAt() {
		return expireAt;
	}

	public void setExpireAt(Date expireAt) {
		this.expireAt = expireAt;
	}

	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		MongoExpiringSession that = (MongoExpiringSession) o;

		return id.equals(that.id);

	}

	public int hashCode() {
		return id.hashCode();
	}
}
