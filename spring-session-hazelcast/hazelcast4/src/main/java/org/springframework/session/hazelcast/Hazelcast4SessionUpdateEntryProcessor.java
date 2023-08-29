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

package org.springframework.session.hazelcast;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import com.hazelcast.map.EntryProcessor;
import com.hazelcast.map.ExtendedMapEntry;

import org.springframework.session.MapSession;

/**
 * Hazelcast {@link EntryProcessor} responsible for handling updates to session when using
 * Hazelcast 4.
 *
 * @author Eleftheria Stein
 * @author Didier Loiseau
 * @since 2.4.0
 */
public class Hazelcast4SessionUpdateEntryProcessor implements EntryProcessor<String, MapSession, Object> {

	private Instant lastAccessedTime;

	private Duration maxInactiveInterval;

	private Map<String, Object> delta;

	@Override
	public Object process(Map.Entry<String, MapSession> entry) {
		MapSession value = entry.getValue();
		if (value == null) {
			return Boolean.FALSE;
		}
		if (this.lastAccessedTime != null) {
			value.setLastAccessedTime(this.lastAccessedTime);
		}
		if (this.maxInactiveInterval != null) {
			value.setMaxInactiveInterval(this.maxInactiveInterval);
		}
		if (this.delta != null) {
			for (final Map.Entry<String, Object> attribute : this.delta.entrySet()) {
				if (attribute.getValue() != null) {
					value.setAttribute(attribute.getKey(), attribute.getValue());
				}
				else {
					value.removeAttribute(attribute.getKey());
				}
			}
		}
		((ExtendedMapEntry<String, MapSession>) entry).setValue(value, value.getMaxInactiveInterval().getSeconds(),
				TimeUnit.SECONDS);
		return Boolean.TRUE;
	}

	void setLastAccessedTime(Instant lastAccessedTime) {
		this.lastAccessedTime = lastAccessedTime;
	}

	void setMaxInactiveInterval(Duration maxInactiveInterval) {
		this.maxInactiveInterval = maxInactiveInterval;
	}

	void setDelta(Map<String, Object> delta) {
		this.delta = delta;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		Hazelcast4SessionUpdateEntryProcessor that = (Hazelcast4SessionUpdateEntryProcessor) o;
		// @formatter:off
		return Objects.equals(this.lastAccessedTime, that.lastAccessedTime)
				&& Objects.equals(this.maxInactiveInterval, that.maxInactiveInterval)
				&& Objects.equals(this.delta, that.delta);
		// @formatter:on
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.lastAccessedTime, this.maxInactiveInterval, this.delta);
	}

}
