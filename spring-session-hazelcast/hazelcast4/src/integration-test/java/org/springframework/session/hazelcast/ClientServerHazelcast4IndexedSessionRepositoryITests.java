/*
 * Copyright 2014-2022 the original author or authors.
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

import com.hazelcast.client.HazelcastClient;
import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.core.HazelcastInstance;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.MountableFile;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.MapSession;
import org.springframework.session.Session;
import org.springframework.session.hazelcast.config.annotation.web.http.EnableHazelcastHttpSession;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;

/**
 * Integration tests for {@link Hazelcast4IndexedSessionRepository} using client-server
 * topology.
 *
 * @author Eleftheria Stein
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration
@WebAppConfiguration
class ClientServerHazelcast4IndexedSessionRepositoryITests extends AbstractHazelcast4IndexedSessionRepositoryITests {

	private static GenericContainer container = new GenericContainer<>("hazelcast/hazelcast:4.2.4")
			.withExposedPorts(5701).withCopyFileToContainer(MountableFile.forClasspathResource("/hazelcast-server.xml"),
					"/opt/hazelcast/hazelcast.xml");

	@BeforeAll
	static void setUpClass() {
		container.start();
	}

	@AfterAll
	static void tearDownClass() {
		container.stop();
	}

	@Configuration
	@EnableHazelcastHttpSession
	static class HazelcastSessionConfig {

		@Bean
		HazelcastInstance hazelcastInstance() {
			ClientConfig clientConfig = new ClientConfig();
			clientConfig.getNetworkConfig()
					.addAddress(container.getContainerIpAddress() + ":" + container.getFirstMappedPort());
			clientConfig.getUserCodeDeploymentConfig().setEnabled(true).addClass(Session.class)
					.addClass(MapSession.class).addClass(Hazelcast4SessionUpdateEntryProcessor.class);
			return HazelcastClient.newHazelcastClient(clientConfig);
		}

	}

}
