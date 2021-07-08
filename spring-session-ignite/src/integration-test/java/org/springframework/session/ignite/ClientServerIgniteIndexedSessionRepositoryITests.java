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

package org.springframework.session.ignite;

import java.util.Collections;

import org.apache.ignite.Ignite;
import org.apache.ignite.Ignition;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.spi.discovery.tcp.TcpDiscoverySpi;
import org.apache.ignite.spi.discovery.tcp.ipfinder.vm.TcpDiscoveryVmIpFinder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testcontainers.containers.GenericContainer;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.ignite.config.annotation.web.http.EnableIgniteHttpSession;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;

/**
 * Integration tests for {@link IgniteIndexedSessionRepository} using client-server
 * topology.
 *
 * @author Semyon Danilov
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration
@WebAppConfiguration
class ClientServerIgniteIndexedSessionRepositoryITests extends AbstractIgniteIndexedSessionRepositoryITests {

	private static GenericContainer container = new GenericContainer<>("apacheignite/ignite:2.9.0")
			.withExposedPorts(47100, 47500);

	@BeforeAll
	static void setUpClass() {
		Ignition.stopAll(true);
		container.start();
	}

	@AfterAll
	static void tearDownClass() {
		Ignition.stopAll(true);
		container.stop();
	}

	@Configuration
	@EnableIgniteHttpSession
	static class IgniteSessionConfig {

		@Bean
		Ignite ignite() {
			IgniteConfiguration cfg = new IgniteConfiguration();
			final String address = container.getContainerIpAddress() + ":" + container.getMappedPort(47500);
			final TcpDiscoverySpi spi = new TcpDiscoverySpi()
					.setIpFinder(new TcpDiscoveryVmIpFinder().setAddresses(Collections.singleton(address)));
			cfg.setDiscoverySpi(spi);
			return Ignition.start(cfg);
		}

	}

}
