/*
 * Copyright 2014-2017 the original author or authors.
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

package org.springframework.session.hazelcast.config.annotation.web.http;

import java.time.Duration;

import com.hazelcast.config.ClasspathXmlConfig;
import com.hazelcast.config.Config;
import com.hazelcast.config.NetworkConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.util.SocketUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test the different configuration options for the {@link EnableHazelcastHttpSession}
 * annotation.
 *
 * @author Tommy Ludwig
 */
public class HazelcastHttpSessionConfigurationXmlTests<S extends Session> {

	@RunWith(SpringRunner.class)
	@ContextConfiguration
	@WebAppConfiguration
	public static class CustomXmlMapNameTest<S extends Session> {

		@Autowired
		private SessionRepository<S> repository;

		@Test
		public void saveSessionTest() throws InterruptedException {

			S sessionToSave = this.repository.createSession();

			this.repository.save(sessionToSave);

			S session = this.repository.findById(sessionToSave.getId());

			assertThat(session.getId()).isEqualTo(sessionToSave.getId());
			assertThat(session.getMaxInactiveInterval())
					.isEqualTo(Duration.ofMinutes(30));
		}

		@Configuration
		@EnableHazelcastHttpSession(sessionMapName = "my-sessions")
		static class HazelcastSessionXmlConfigCustomMapName {

			@Bean
			public HazelcastInstance embeddedHazelcast() {
				Config hazelcastConfig = new ClasspathXmlConfig(
						"org/springframework/session/hazelcast/config/annotation/web/http/hazelcast-custom-map-name.xml");
				NetworkConfig netConfig = new NetworkConfig();
				netConfig.setPort(SocketUtils.findAvailableTcpPort());
				hazelcastConfig.setNetworkConfig(netConfig);
				return Hazelcast.newHazelcastInstance(hazelcastConfig);
			}
		}
	}

	@RunWith(SpringRunner.class)
	@ContextConfiguration
	@WebAppConfiguration
	public static class CustomXmlMapNameAndIdleTest<S extends Session> {

		@Autowired
		private SessionRepository<S> repository;

		@Test
		public void saveSessionTest() throws InterruptedException {

			S sessionToSave = this.repository.createSession();

			this.repository.save(sessionToSave);

			S session = this.repository.findById(sessionToSave.getId());

			assertThat(session.getId()).isEqualTo(sessionToSave.getId());
			assertThat(session.getMaxInactiveInterval())
					.isEqualTo(Duration.ofMinutes(20));
		}

		@Configuration
		@EnableHazelcastHttpSession(sessionMapName = "test-sessions", maxInactiveIntervalInSeconds = 1200)
		static class HazelcastSessionXmlConfigCustomMapNameAndIdle {

			@Bean
			public HazelcastInstance embeddedHazelcast() {
				Config hazelcastConfig = new ClasspathXmlConfig(
						"org/springframework/session/hazelcast/config/annotation/web/http/hazelcast-custom-idle-time-map-name.xml");
				NetworkConfig netConfig = new NetworkConfig();
				netConfig.setPort(SocketUtils.findAvailableTcpPort());
				hazelcastConfig.setNetworkConfig(netConfig);
				return Hazelcast.newHazelcastInstance(hazelcastConfig);
			}
		}
	}

}
