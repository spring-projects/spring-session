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
package org.springframework.session.data.mongo;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.springframework.session.ExpiringSession;

/**
 * Session object providing additional information about the datetime of expiration.
 *
 * @author Jakub Kubrynski
 * @since 1.2
 */
public class MongoExpiringSession implements ExpiringSession {

	private final String id;
	private long created = System.currentTimeMillis();
	private long accessed;
	private int interval;
	private Date expireAt;
	private Map<String, Object> attrs = new HashMap<String, Object>();

	public MongoExpiringSession() {
		this(MongoOperationsSessionRepository.DEFAULT_INACTIVE_INTERVAL);
	}

	public MongoExpiringSession(int maxInactiveIntervalInSeconds) {
		this(UUID.randomUUID().toString(), maxInactiveIntervalInSeconds);
	}

	public MongoExpiringSession(String id, int maxInactiveIntervalInSeconds) {
		this.id = id;
		this.interval = maxInactiveIntervalInSeconds;
		setLastAccessedTime(this.created);
	}

	public String getId() {
		return this.id;
	}

	@SuppressWarnings("unchecked")
	public <T> T getAttribute(String attributeName) {
		return (T) this.attrs.get(attributeName);
	}

	public Set<String> getAttributeNames() {
		return this.attrs.keySet();
	}

	public void setAttribute(String attributeName, Object attributeValue) {
		if (attributeValue == null) {
			removeAttribute(attributeName);
		}
		else {
			this.attrs.put(attributeName, attributeValue);
		}
	}

	public void removeAttribute(String attributeName) {
		this.attrs.remove(attributeName);
	}

	public long getCreationTime() {
		return this.created;
	}

	public void setCreationTime(long created) {
		this.created = created;
	}

	public void setLastAccessedTime(long lastAccessedTime) {
		this.accessed = lastAccessedTime;
		this.expireAt = new Date(
				lastAccessedTime + TimeUnit.SECONDS.toMillis(this.interval));
	}

	public long getLastAccessedTime() {
		return this.accessed;
	}

	public void setMaxInactiveIntervalInSeconds(int interval) {
		this.interval = interval;
	}

	public int getMaxInactiveIntervalInSeconds() {
		return this.interval;
	}

	public boolean isExpired() {
		return new Date().after(this.expireAt);
	}

	public Date getExpireAt() {
		return this.expireAt;
	}

	public void setExpireAt(Date expireAt) {
		this.expireAt = expireAt;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}

		MongoExpiringSession that = (MongoExpiringSession) o;

		return this.id.equals(that.id);

	}

	@Override
	public int hashCode() {
		return this.id.hashCode();
	}
}
