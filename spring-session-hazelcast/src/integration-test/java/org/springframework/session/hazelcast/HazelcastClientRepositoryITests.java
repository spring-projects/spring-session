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

import com.hazelcast.client.HazelcastClient;
import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.core.HazelcastInstance;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.MapSession;
import org.springframework.session.Session;
import org.springframework.session.hazelcast.config.annotation.web.http.EnableHazelcastHttpSession;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.web.WebAppConfiguration;

/**
 * Integration tests that check the underlying data source - in this case Hazelcast
 * Client.
 *
 * @author Vedran Pavic
 * @author Artem Bilan
 * @since 1.1
 */
@RunWith(SpringRunner.class)
@ContextConfiguration
@WebAppConfiguration
public class HazelcastClientRepositoryITests extends AbstractHazelcastRepositoryITests {

	private static GenericContainer container = new GenericContainer<>(
			"hazelcast/hazelcast:3.10.6")
					.withExposedPorts(5701)
					.withEnv("JAVA_OPTS",
							"-Dhazelcast.config=/opt/hazelcast/config_ext/hazelcast.xml")
					.withClasspathResourceMapping("/hazelcast-server.xml",
							"/opt/hazelcast/config_ext/hazelcast.xml",
							BindMode.READ_ONLY);

	@BeforeClass
	public static void setUpClass() {
		container.start();
	}

	@AfterClass
	public static void tearDownClass() {
		container.stop();
	}

	@Configuration
	@EnableHazelcastHttpSession
	static class HazelcastSessionConfig {

		@Bean
		public HazelcastInstance hazelcastInstance() {
			ClientConfig clientConfig = new ClientConfig();
			clientConfig.getNetworkConfig().addAddress(container.getContainerIpAddress()
					+ ":" + container.getFirstMappedPort());
			clientConfig.getUserCodeDeploymentConfig().setEnabled(true)
					.addClass(Session.class).addClass(MapSession.class)
					.addClass(SessionUpdateEntryProcessor.class);
			return HazelcastClient.newHazelcastClient(clientConfig);
		}

	}

}
