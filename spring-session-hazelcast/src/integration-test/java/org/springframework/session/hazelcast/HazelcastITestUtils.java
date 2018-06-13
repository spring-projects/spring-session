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

import com.hazelcast.config.Config;
import com.hazelcast.config.MapAttributeConfig;
import com.hazelcast.config.MapIndexConfig;
import com.hazelcast.config.NetworkConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;

import org.springframework.util.SocketUtils;

/**
 * Utility class for Hazelcast integration tests.
 *
 * @author Vedran Pavic
 */
public final class HazelcastITestUtils {

	private HazelcastITestUtils() {
	}

	/**
	 * Creates {@link HazelcastInstance} for use in integration tests.
	 * @param port the port for Hazelcast to bind to
	 * @return the Hazelcast instance
	 */
	public static HazelcastInstance embeddedHazelcastServer(int port) {
		MapAttributeConfig attributeConfig = new MapAttributeConfig()
				.setName(HazelcastSessionRepository.PRINCIPAL_NAME_ATTRIBUTE)
				.setExtractor(PrincipalNameExtractor.class.getName());

		Config config = new Config();

		NetworkConfig networkConfig = config.getNetworkConfig();

		networkConfig.setPort(port);

		networkConfig.getJoin()
				.getMulticastConfig().setEnabled(false);

		config.getMapConfig(HazelcastSessionRepository.DEFAULT_SESSION_MAP_NAME)
				.addMapAttributeConfig(attributeConfig)
				.addMapIndexConfig(new MapIndexConfig(
						HazelcastSessionRepository.PRINCIPAL_NAME_ATTRIBUTE, false));

		return Hazelcast.newHazelcastInstance(config);
	}

	/**
	 * Creates {@link HazelcastInstance} for use in integration tests.
	 * @return the Hazelcast instance
	 */
	public static HazelcastInstance embeddedHazelcastServer() {
		return embeddedHazelcastServer(SocketUtils.findAvailableTcpPort());
	}

}
