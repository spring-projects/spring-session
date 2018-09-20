/*
 * Copyright 2014-2018 the original author or authors.
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

package org.springframework.session.hazelcast;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import com.hazelcast.core.Offloadable;
import com.hazelcast.map.AbstractEntryProcessor;
import com.hazelcast.map.EntryProcessor;

import org.springframework.session.MapSession;

/**
 * Hazelcast {@link EntryProcessor} responsible for handling updates to session.
 *
 * @author Vedran Pavic
 * @since 2.0.5
 * @see HazelcastSessionRepository#save(HazelcastSessionRepository.HazelcastSession)
 */
class SessionUpdateEntryProcessor extends AbstractEntryProcessor<String, MapSession>
		implements Offloadable {

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
		entry.setValue(value);
		return Boolean.TRUE;
	}

	@Override
	public String getExecutorName() {
		return OFFLOADABLE_EXECUTOR;
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

}
