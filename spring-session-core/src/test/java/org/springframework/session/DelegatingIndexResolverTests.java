/*
 * Copyright 2014-2019 the original author or authors.
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

package org.springframework.session;

import java.util.Collections;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link DelegatingIndexResolver}.
 *
 * @author Vedran Pavic
 */
class DelegatingIndexResolverTests {

	private DelegatingIndexResolver<MapSession> indexResolver;

	@BeforeEach
	void setUp() {
		this.indexResolver = new DelegatingIndexResolver<>(new TestIndexResolver("one"), new TestIndexResolver("two"));
	}

	@Test
	void resolve() {
		MapSession session = new MapSession("1");
		session.setAttribute("one", "first");
		session.setAttribute("two", "second");
		Map<String, String> indexes = this.indexResolver.resolveIndexesFor(session);
		assertThat(indexes).hasSize(2);
		assertThat(indexes.get("one")).isEqualTo("first");
		assertThat(indexes.get("two")).isEqualTo("second");
	}

	private static class TestIndexResolver implements IndexResolver<MapSession> {

		private final String supportedIndex;

		TestIndexResolver(String supportedIndex) {
			this.supportedIndex = supportedIndex;
		}

		@Override
		public Map<String, String> resolveIndexesFor(MapSession session) {
			return Collections.singletonMap(this.supportedIndex, session.getAttribute(this.supportedIndex));
		}

	}

}
