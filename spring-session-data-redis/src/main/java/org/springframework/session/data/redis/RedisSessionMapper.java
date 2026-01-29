/*
 * Copyright 2014-present the original author or authors.
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

package org.springframework.session.data.redis;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.springframework.session.MapSession;
import org.springframework.session.Session;
import org.springframework.util.Assert;

/**
 * A {@link Function} that converts a {@link Map} representing Redis hash to a
 * {@link MapSession}.
 *
 * @author Vedran Pavic
 * @author Marcus da Coregio
 * @since 2.2.0
 */
public final class RedisSessionMapper implements BiFunction<String, Map<String, Object>, MapSession> {

	/**
	 * The key in the hash representing {@link Session#getCreationTime()}.
	 */
	static final String CREATION_TIME_KEY = "creationTime";

	/**
	 * The key in the hash representing {@link Session#getLastAccessedTime()}.
	 */
	static final String LAST_ACCESSED_TIME_KEY = "lastAccessedTime";

	/**
	 * The key in the hash representing {@link Session#getMaxInactiveInterval()}.
	 */
	static final String MAX_INACTIVE_INTERVAL_KEY = "maxInactiveInterval";

	/**
	 * The prefix of the key in the hash used for session attributes. For example, if the
	 * session contained an attribute named {@code attributeName}, then there would be an
	 * entry in the hash named {@code sessionAttr:attributeName} that mapped to its value.
	 */
	static final String ATTRIBUTE_PREFIX = "sessionAttr:";

	private static <T> T getRequired(Map<String, Object> map, String key) {
		T value = (T) map.get(key);
		if (value == null) {
			throw new IllegalStateException(key + " key must not be null");
		}
		return value;
	}

	@Override
	public MapSession apply(String sessionId, Map<String, Object> map) {
		Assert.hasText(sessionId, "sessionId must not be empty");
		Assert.notEmpty(map, "map must not be empty");
		MapSession session = new MapSession(sessionId);
		Long creationTime = getRequired(map, CREATION_TIME_KEY);
		session.setCreationTime(Instant.ofEpochMilli(creationTime));
		Long lastAccessedTime = getRequired(map, LAST_ACCESSED_TIME_KEY);
		session.setLastAccessedTime(Instant.ofEpochMilli(lastAccessedTime));
		Integer maxInactiveInterval = getRequired(map, MAX_INACTIVE_INTERVAL_KEY);
		session.setMaxInactiveInterval(Duration.ofSeconds(maxInactiveInterval));
		map.forEach((name, value) -> {
			if (name.startsWith(ATTRIBUTE_PREFIX)) {
				session.setAttribute(name.substring(ATTRIBUTE_PREFIX.length()), value);
			}
		});
		return session;
	}

}
