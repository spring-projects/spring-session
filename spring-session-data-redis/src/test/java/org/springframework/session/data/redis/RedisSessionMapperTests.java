/*
 * Copyright 2014-2023 the original author or authors.
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
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.session.MapSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

/**
 * Tests for {@link RedisSessionMapper}.
 *
 * @author Vedran Pavic
 */
class RedisSessionMapperTests {

	private RedisSessionMapper mapper = new RedisSessionMapper();

	@Test
	void apply_NullId_ShouldThrowException() {
		assertThatIllegalArgumentException().isThrownBy(() -> this.mapper.apply(null, Collections.emptyMap()))
				.withMessage("sessionId must not be empty");
	}

	@Test
	void apply_EmptyId_ShouldThrowException() {
		assertThatIllegalArgumentException().isThrownBy(() -> this.mapper.apply(" ", Collections.emptyMap()))
				.withMessage("sessionId must not be empty");
	}

	@Test
	void apply_NullMap_ShouldThrowException() {
		assertThatIllegalArgumentException().isThrownBy(() -> this.mapper.apply("1234", null))
				.withMessage("map must not be empty");
	}

	@Test
	void apply_EmptyMap_ShouldThrowException() {
		assertThatIllegalArgumentException().isThrownBy(() -> this.mapper.apply("1234", Collections.emptyMap()))
				.withMessage("map must not be empty");
	}

	@Test
	void apply_MapWithoutCreationTime_ShouldThrowException() {
		Map<String, Object> sessionMap = new HashMap<>();
		sessionMap.put(RedisSessionMapper.LAST_ACCESSED_TIME_KEY, 0L);
		sessionMap.put(RedisSessionMapper.MAX_INACTIVE_INTERVAL_KEY, 1800);
		assertThatIllegalStateException().isThrownBy(() -> this.mapper.apply("id", sessionMap))
				.withMessage(RedisSessionMapper.CREATION_TIME_KEY + " key must not be null");
	}

	@Test
	void apply_MapWithoutLastAccessedTime_ShouldThrowException() {
		Map<String, Object> sessionMap = new HashMap<>();
		sessionMap.put(RedisSessionMapper.CREATION_TIME_KEY, 0L);
		sessionMap.put(RedisSessionMapper.MAX_INACTIVE_INTERVAL_KEY, 1800);
		assertThatIllegalStateException().isThrownBy(() -> this.mapper.apply("id", sessionMap))
				.withMessage(RedisSessionMapper.LAST_ACCESSED_TIME_KEY + " key must not be null");
	}

	@Test
	void apply_MapWithoutMaxInactiveInterval_ShouldThrowException() {
		Map<String, Object> sessionMap = new HashMap<>();
		sessionMap.put(RedisSessionMapper.CREATION_TIME_KEY, 0L);
		sessionMap.put(RedisSessionMapper.LAST_ACCESSED_TIME_KEY, 0L);
		assertThatIllegalStateException().isThrownBy(() -> this.mapper.apply("id", sessionMap))
				.withMessage(RedisSessionMapper.MAX_INACTIVE_INTERVAL_KEY + " key must not be null");
	}

	@Test
	void apply_ValidMap_ShouldReturnSession() {
		Map<String, Object> sessionMap = new HashMap<>();
		sessionMap.put(RedisSessionMapper.CREATION_TIME_KEY, 0L);
		sessionMap.put(RedisSessionMapper.LAST_ACCESSED_TIME_KEY, 0L);
		sessionMap.put(RedisSessionMapper.MAX_INACTIVE_INTERVAL_KEY, 1800);
		sessionMap.put(RedisSessionMapper.ATTRIBUTE_PREFIX + "existing", "value");
		sessionMap.put(RedisSessionMapper.ATTRIBUTE_PREFIX + "missing", null);
		MapSession session = this.mapper.apply("id", sessionMap);
		assertThat(session.getId()).isEqualTo("id");
		assertThat(session.getCreationTime()).isEqualTo(Instant.ofEpochMilli(0));
		assertThat(session.getLastAccessedTime()).isEqualTo(Instant.ofEpochMilli(0));
		assertThat(session.getMaxInactiveInterval()).isEqualTo(Duration.ofMinutes(30));
		assertThat(session.getAttributeNames()).hasSize(1);
		assertThat((String) session.getAttribute("existing")).isEqualTo("value");
	}

}
