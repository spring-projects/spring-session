/*
 * Copyright 2014-2016 the original author or authors.
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

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.hazelcast.config.annotation.web.http.EnableHazelcastHttpSession;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.util.SocketUtils;

/**
 * Integration tests that check the underlying data source - in this case Hazelcast
 * Client.
 *
 * @author Vedran Pavic
 * @author Artem Bilan
 * @since 1.1
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration
@WebAppConfiguration
public class HazelcastClientRepositoryITests extends AbstractHazelcastRepositoryITests {

	private static final int PORT = SocketUtils.findAvailableTcpPort();

	private static HazelcastInstance hazelcastInstance;

	@BeforeClass
	public static void setup() {
		hazelcastInstance = HazelcastITestUtils.embeddedHazelcastServer(PORT);
	}

	@AfterClass
	public static void teardown() {
		if (hazelcastInstance != null) {
			hazelcastInstance.shutdown();
		}
	}

	@Configuration
	@EnableHazelcastHttpSession
	static class HazelcastSessionConfig {

		@Bean
		public HazelcastInstance embeddedHazelcastClient() {
			ClientConfig clientConfig = new ClientConfig();
			clientConfig.getNetworkConfig().addAddress("127.0.0.1:" + PORT);
			return HazelcastClient.newHazelcastClient(clientConfig);
		}

	}

}
