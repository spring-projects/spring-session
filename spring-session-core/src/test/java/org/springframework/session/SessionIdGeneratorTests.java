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

package org.springframework.session;

import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Yanming Zhou
 */
class SessionIdGeneratorTests {

	static final String prefix = "sessionid-";

	@Test
	void sessionIdShouldStartsWithCustomizedPrefix() {
		MapSession session = new MapSession();
		assertThat(session.getId()).startsWith(prefix);
		session.changeSessionId();
		assertThat(session.getId()).startsWith(prefix);
	}

	public static class MySessionIdGenerator implements SessionIdGenerator {

		private final AtomicLong counter = new AtomicLong();

		@Override
		public String generateId() {
			return prefix + this.counter.incrementAndGet();
		}

	}

}
