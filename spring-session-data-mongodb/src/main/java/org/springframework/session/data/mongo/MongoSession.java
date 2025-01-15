/*
 * Copyright 2014-2022 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.session.data.mongo;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.lang.Nullable;
import org.springframework.session.MapSession;
import org.springframework.session.Session;
import org.springframework.session.SessionIdGenerator;
import org.springframework.session.UuidSessionIdGenerator;
import org.springframework.util.Assert;

/**
 * Session object providing additional information about the datetime of expiration.
 *
 * @author Jakub Kubrynski
 * @author Greg Turnquist
 * @since 1.2
 */
public final class MongoSession implements Session {

	/**
	 * Mongo doesn't support {@literal dot} in field names. We replace it with a unicode
	 * character from the Private Use Area.
	 * <p>
	 * NOTE: This was originally stored in unicode format. Delomboking the code caused it
	 * to get converted to another encoding, which isn't supported on all systems, so we
	 * migrated back to unicode. The same character is being represented ensuring binary
	 * compatibility. See <a href=
	 * "https://www.compart.com/en/unicode/U+F607">https://www.compart.com/en/unicode/U+F607</a>
	 */
	private static final char DOT_COVER_CHAR = '\uF607';

	private String id;

	private final String originalSessionId;

	private long createdMillis = System.currentTimeMillis();

	private long accessedMillis;

	private long intervalSeconds;

	private Date expireAt;

	private final Map<String, Object> attrs = new HashMap<>();

	private transient SessionIdGenerator sessionIdGenerator = UuidSessionIdGenerator.getInstance();

	/**
	 * Constructs a new instance using the provided session id.
	 * @param sessionId the session id to use
	 * @since 3.2
	 */
	public MongoSession(String sessionId) {
		this(sessionId, MapSession.DEFAULT_MAX_INACTIVE_INTERVAL_SECONDS);
	}

	public MongoSession() {
		this(MapSession.DEFAULT_MAX_INACTIVE_INTERVAL_SECONDS);
	}

	public MongoSession(long maxInactiveIntervalInSeconds) {
		this(UuidSessionIdGenerator.getInstance().generate(), maxInactiveIntervalInSeconds);
	}

	public MongoSession(String id, long maxInactiveIntervalInSeconds) {
		this.id = id;
		this.originalSessionId = id;
		this.intervalSeconds = maxInactiveIntervalInSeconds;
		setLastAccessedTime(Instant.ofEpochMilli(this.createdMillis));
	}

	/**
	 * Constructs a new instance using the provided {@link SessionIdGenerator}.
	 * @param sessionIdGenerator the {@link SessionIdGenerator} to use
	 * @since 3.2
	 */
	public MongoSession(SessionIdGenerator sessionIdGenerator) {
		this(sessionIdGenerator.generate(), MapSession.DEFAULT_MAX_INACTIVE_INTERVAL_SECONDS);
		this.sessionIdGenerator = sessionIdGenerator;
	}

	/**
	 * Constructs a new instance using the provided {@link SessionIdGenerator} and max
	 * inactive interval.
	 * @param sessionIdGenerator the {@link SessionIdGenerator} to use
	 * @param maxInactiveIntervalInSeconds the max inactive interval in seconds
	 * @since 3.2
	 */
	MongoSession(SessionIdGenerator sessionIdGenerator, long maxInactiveIntervalInSeconds) {
		this(sessionIdGenerator.generate(), maxInactiveIntervalInSeconds);
		this.sessionIdGenerator = sessionIdGenerator;
	}

	static String coverDot(String attributeName) {
		return attributeName.replace('.', DOT_COVER_CHAR);
	}

	static String uncoverDot(String attributeName) {
		return attributeName.replace(DOT_COVER_CHAR, '.');
	}

	@Override
	public String changeSessionId() {

		String changedId = this.sessionIdGenerator.generate();
		this.id = changedId;
		return changedId;
	}

	@Override
	@Nullable
	@SuppressWarnings("unchecked")
	public <T> T getAttribute(String attributeName) {
		return (T) this.attrs.get(coverDot(attributeName));
	}

	@Override
	public Set<String> getAttributeNames() {
		return this.attrs.keySet().stream().map(MongoSession::uncoverDot).collect(Collectors.toSet());
	}

	@Override
	public void setAttribute(String attributeName, Object attributeValue) {

		if (attributeValue == null) {
			removeAttribute(coverDot(attributeName));
		}
		else {
			this.attrs.put(coverDot(attributeName), attributeValue);
		}
	}

	@Override
	public void removeAttribute(String attributeName) {
		this.attrs.remove(coverDot(attributeName));
	}

	@Override
	public Instant getCreationTime() {
		return Instant.ofEpochMilli(this.createdMillis);
	}

	void setCreationTime(long created) {
		this.createdMillis = created;
	}

	@Override
	public Instant getLastAccessedTime() {
		return Instant.ofEpochMilli(this.accessedMillis);
	}

	@Override
	public void setLastAccessedTime(Instant lastAccessedTime) {
		this.accessedMillis = lastAccessedTime.toEpochMilli();
		this.expireAt = Date.from(lastAccessedTime.plus(Duration.ofSeconds(this.intervalSeconds)));
	}

	@Override
	public Duration getMaxInactiveInterval() {
		return Duration.ofSeconds(this.intervalSeconds);
	}

	@Override
	public void setMaxInactiveInterval(Duration interval) {
		this.intervalSeconds = interval.getSeconds();
	}

	@Override
	public boolean isExpired() {
		return this.intervalSeconds >= 0 && new Date().after(this.expireAt);
	}

	@Override
	public boolean equals(Object o) {

		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		MongoSession that = (MongoSession) o;
		return Objects.equals(this.id, that.id);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.id);
	}

	@Override
	public String getId() {
		return this.id;
	}

	Date getExpireAt() {
		return this.expireAt;
	}

	void setExpireAt(final Date expireAt) {
		this.expireAt = expireAt;
	}

	boolean hasChangedSessionId() {
		return !getId().equals(this.originalSessionId);
	}

	String getOriginalSessionId() {
		return this.originalSessionId;
	}

	/**
	 * Sets the session id.
	 * @param id the id to set
	 * @since 3.2
	 */
	void setId(String id) {
		this.id = id;
	}

	/**
	 * Sets the {@link SessionIdGenerator} to use.
	 * @param sessionIdGenerator the {@link SessionIdGenerator} to use
	 * @since 3.2
	 */
	void setSessionIdGenerator(SessionIdGenerator sessionIdGenerator) {
		Assert.notNull(sessionIdGenerator, "sessionIdGenerator cannot be null");
		this.sessionIdGenerator = sessionIdGenerator;
	}

}
