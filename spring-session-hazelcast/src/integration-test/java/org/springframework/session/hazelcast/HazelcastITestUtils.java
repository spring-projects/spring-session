/*
 * Copyright 2014-2019 the original author or authors.
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

import com.hazelcast.config.AttributeConfig;
import com.hazelcast.config.Config;
import com.hazelcast.config.IndexConfig;
import com.hazelcast.config.IndexType;
import com.hazelcast.config.NetworkConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;

/**
 * Utility class for Hazelcast integration tests.
 *
 * @author Vedran Pavic
 */
final class HazelcastITestUtils {

	private HazelcastITestUtils() {
	}

	/**
	 * Creates {@link HazelcastInstance} for use in integration tests.
	 * @return the Hazelcast instance
	 */
	static HazelcastInstance embeddedHazelcastServer() {
		Config config = new Config();
		NetworkConfig networkConfig = config.getNetworkConfig();
		networkConfig.setPort(0);
		networkConfig.getJoin().getMulticastConfig().setEnabled(false);
		AttributeConfig attributeConfig = new AttributeConfig()
				.setName(HazelcastIndexedSessionRepository.PRINCIPAL_NAME_ATTRIBUTE)
				.setExtractorClassName(PrincipalNameExtractor.class.getName());
		config.getMapConfig(HazelcastIndexedSessionRepository.DEFAULT_SESSION_MAP_NAME)
				.addAttributeConfig(attributeConfig).addIndexConfig(
				new IndexConfig(IndexType.HASH, HazelcastIndexedSessionRepository.PRINCIPAL_NAME_ATTRIBUTE));
		return Hazelcast.newHazelcastInstance(config);
	}

}
