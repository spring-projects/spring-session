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

import com.hazelcast.core.HazelcastInstance;
import org.junit.runner.RunWith;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.hazelcast.config.annotation.web.http.EnableHazelcastHttpSession;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.web.WebAppConfiguration;

/**
 * Integration tests that check the underlying data source - in this case Hazelcast
 * Server.
 *
 * @author Tommy Ludwig
 * @author Vedran Pavic
 */
@RunWith(SpringRunner.class)
@ContextConfiguration
@WebAppConfiguration
public class HazelcastServerRepositoryITests extends AbstractHazelcastRepositoryITests {

	@EnableHazelcastHttpSession
	@Configuration
	static class HazelcastSessionConfig {

		@Bean
		public HazelcastInstance hazelcastInstance() {
			return HazelcastITestUtils.embeddedHazelcastServer();
		}

	}

}
