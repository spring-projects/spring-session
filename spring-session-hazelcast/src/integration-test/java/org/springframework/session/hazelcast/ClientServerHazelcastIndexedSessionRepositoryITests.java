/*
 * Copyright 2014-2025 the original author or authors.
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
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.utility.MountableFile;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.MapSession;
import org.springframework.session.Session;
import org.springframework.session.SessionIdGenerator;
import org.springframework.session.hazelcast.config.annotation.web.http.EnableHazelcastHttpSession;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;

/**
 * Integration tests for {@link HazelcastIndexedSessionRepository} using client-server
 * topology.
 *
 * @author Vedran Pavic
 * @author Artem Bilan
 */
@SpringJUnitWebConfig
class ClientServerHazelcastIndexedSessionRepositoryITests extends AbstractHazelcastIndexedSessionRepositoryITests {

	// @formatter:off
	private static GenericContainer container = new GenericContainer<>(new ImageFromDockerfile()
			.withDockerfileFromBuilder((builder) -> builder
					.from("hazelcast/hazelcast:5.3.2-slim")
					.user("root")
					.run("apk del --no-cache openjdk11-jre-headless")
					.run("apk add --no-cache openjdk17-jre-headless")
					.user("hazelcast")))
			.withExposedPorts(5701).withCopyFileToContainer(MountableFile.forClasspathResource("/hazelcast-server.xml"),
					"/opt/hazelcast/hazelcast.xml")
			.withEnv("HAZELCAST_CONFIG", "hazelcast.xml");
	// @formatter:on

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
			clientConfig.getNetworkConfig().addAddress(container.getHost() + ":" + container.getFirstMappedPort());
			clientConfig.getUserCodeDeploymentConfig()
				.setEnabled(true)
				.addClass(Session.class)
				.addClass(MapSession.class)
				.addClass(SessionUpdateEntryProcessor.class)
				.addClass(SessionIdGenerator.class);
			return HazelcastClient.newHazelcastClient(clientConfig);
		}

	}

}
