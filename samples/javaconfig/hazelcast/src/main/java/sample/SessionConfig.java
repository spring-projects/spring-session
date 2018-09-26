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

package sample;

import com.hazelcast.config.Config;
import com.hazelcast.config.MapAttributeConfig;
import com.hazelcast.config.MapIndexConfig;
import com.hazelcast.config.SerializerConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.hazelcast.HazelcastSessionRepository;
import org.springframework.session.hazelcast.PrincipalNameExtractor;
import org.springframework.session.hazelcast.config.annotation.web.http.EnableHazelcastHttpSession;
import org.springframework.util.SocketUtils;

// tag::class[]
@EnableHazelcastHttpSession(maxInactiveIntervalInSeconds = 300)
@Configuration
public class SessionConfig {

	@Bean(destroyMethod = "shutdown")
	public HazelcastInstance hazelcastInstance() {
		Config config = new Config();

		int port = SocketUtils.findAvailableTcpPort();

		config.getNetworkConfig()
				.setPort(port)
				.getJoin().getMulticastConfig().setEnabled(false);

		System.out.println("Hazelcast port #: " + port);

		SerializerConfig serializer = new SerializerConfig()
				.setImplementation(new ObjectStreamSerializer())
				.setTypeClass(Object.class);

		config.getSerializationConfig()
				.addSerializerConfig(serializer);

		MapAttributeConfig attributeConfig = new MapAttributeConfig()
				.setName(HazelcastSessionRepository.PRINCIPAL_NAME_ATTRIBUTE)
				.setExtractor(PrincipalNameExtractor.class.getName());

		config.getMapConfig(HazelcastSessionRepository.DEFAULT_SESSION_MAP_NAME)
				.addMapAttributeConfig(attributeConfig)
				.addMapIndexConfig(new MapIndexConfig(
						HazelcastSessionRepository.PRINCIPAL_NAME_ATTRIBUTE, false));

		return Hazelcast.newHazelcastInstance(config);
	}

}
// end::class[]
