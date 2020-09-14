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

import com.hazelcast.core.HazelcastInstance;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.hazelcast.config.annotation.web.http.EnableHazelcastHttpSession;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;

/**
 * Integration tests for {@link Hazelcast4IndexedSessionRepository} using embedded
 * topology.
 *
 * @author Eleftheria Stein
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration
@WebAppConfiguration
class EmbeddedHazelcast4IndexedSessionRepositoryITests extends AbstractHazelcast4IndexedSessionRepositoryITests {

	@EnableHazelcastHttpSession
	@Configuration
	static class HazelcastSessionConfig {

		@Bean
		HazelcastInstance hazelcastInstance() {
			return Hazelcast4ITestUtils.embeddedHazelcastServer();
		}

	}

}
