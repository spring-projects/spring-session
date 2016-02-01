/*
 * Copyright 2002-2015 the original author or authors.
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

import static org.assertj.core.api.Assertions.*;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.ExpiringSession;
import org.springframework.session.SessionRepository;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.util.SocketUtils;

import com.hazelcast.config.ClasspathXmlConfig;
import com.hazelcast.config.Config;
import com.hazelcast.config.NetworkConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;

/**
 * Test the different configuration options for the
 * {@link EnableHazelcastHttpSession} annotation.
 * 
 * @author Tommy Ludwig
 */
public class HazelcastHttpSessionConfigurationXmlTests<S extends ExpiringSession> {

	@RunWith(SpringJUnit4ClassRunner.class)
	@ContextConfiguration
	@WebAppConfiguration
	public static class CustomXmlMapNameTest<S extends ExpiringSession> {

		@Autowired
		private SessionRepository<S> repository;

		@Autowired
		private HazelcastInstance hazelcast;

		@Test
		public void saveSessionTest() throws InterruptedException {

			S sessionToSave = repository.createSession();

			repository.save(sessionToSave);

			S session = repository.getSession(sessionToSave.getId());

			assertThat(session.getId()).isEqualTo(sessionToSave.getId());
			assertThat(session.getMaxInactiveIntervalInSeconds()).isEqualTo(1800);
		}

		@Test
		public void checkUnderlyingMapSettingsTest() {
			assertThat(
					hazelcast.getConfig()
							.getMapConfig("my-sessions")
							.getMaxIdleSeconds())
					.isEqualTo(1800);
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

	@RunWith(SpringJUnit4ClassRunner.class)
	@ContextConfiguration
	@WebAppConfiguration
	public static class CustomXmlMapNameAndIdleTest<S extends ExpiringSession> {

		@Autowired
		private SessionRepository<S> repository;

		@Autowired
		private HazelcastInstance hazelcast;

		@Test
		public void saveSessionTest() throws InterruptedException {

			S sessionToSave = repository.createSession();

			repository.save(sessionToSave);

			S session = repository.getSession(sessionToSave.getId());

			assertThat(session.getId()).isEqualTo(sessionToSave.getId());
			assertThat(session.getMaxInactiveIntervalInSeconds()).isEqualTo(1200);
		}

		@Test
		public void checkUnderlyingMapSettingsTest() {
			assertThat(
					hazelcast.getConfig()
							.getMapConfig("test-sessions")
							.getMaxIdleSeconds())
					.isEqualTo(1200);
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
