/*
 * Copyright 2014-2020 the original author or authors.
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

import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;

import com.hazelcast.config.SerializationConfig;
import com.hazelcast.config.SerializerConfig;
import com.hazelcast.internal.serialization.impl.DefaultSerializationServiceBuilder;
import com.hazelcast.nio.serialization.Data;
import com.hazelcast.spi.serialization.SerializationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.session.MapSession;

import static org.assertj.core.api.Assertions.assertThat;

class HazelcastSessionSerializerTests {

	private SerializationService serializationService;

	@BeforeEach
	void setUp() {
		SerializationConfig serializationConfig = new SerializationConfig();
		SerializerConfig serializerConfig = new SerializerConfig().setImplementation(new HazelcastSessionSerializer())
				.setTypeClass(MapSession.class);
		serializationConfig.addSerializerConfig(serializerConfig);
		this.serializationService = new DefaultSerializationServiceBuilder().setConfig(serializationConfig).build();
	}

	@Test
	void serializeSessionWithStreamSerializer() {
		MapSession originalSession = new MapSession();
		originalSession.setAttribute("attr1", "value1");
		originalSession.setAttribute("attr2", "value2");
		originalSession.setAttribute("attr3", new SerializableTestAttribute(3));
		originalSession.setMaxInactiveInterval(Duration.ofDays(5));
		originalSession.setLastAccessedTime(Instant.now());
		originalSession.setId("custom-id");

		Data serialized = this.serializationService.toData(originalSession);
		MapSession cached = this.serializationService.toObject(serialized);

		assertThat(originalSession.getCreationTime()).isEqualTo(cached.getCreationTime());
		assertThat(originalSession.getMaxInactiveInterval()).isEqualTo(cached.getMaxInactiveInterval());
		assertThat(originalSession.getId()).isEqualTo(cached.getId());
		assertThat(originalSession.getOriginalId()).isEqualTo(cached.getOriginalId());
		assertThat(originalSession.getAttributeNames().size()).isEqualTo(cached.getAttributeNames().size());
		assertThat(originalSession.<String>getAttribute("attr1")).isEqualTo(cached.getAttribute("attr1"));
		assertThat(originalSession.<String>getAttribute("attr2")).isEqualTo(cached.getAttribute("attr2"));
		assertThat(originalSession.<SerializableTestAttribute>getAttribute("attr3"))
				.isEqualTo(cached.getAttribute("attr3"));
	}

	static class SerializableTestAttribute implements Serializable {

		private int id;

		SerializableTestAttribute(int id) {
			this.id = id;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) {
				return true;
			}
			if (!(o instanceof SerializableTestAttribute)) {
				return false;
			}
			SerializableTestAttribute that = (SerializableTestAttribute) o;
			return this.id == that.id;
		}

		@Override
		public int hashCode() {
			return this.id;
		}

	}

}
