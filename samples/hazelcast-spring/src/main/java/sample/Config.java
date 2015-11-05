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
package sample;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.hazelcast.config.annotation.web.http.EnableHazelcastHttpSession;
import org.springframework.util.SocketUtils;

import com.hazelcast.config.NetworkConfig;
import com.hazelcast.config.SerializerConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;

// tag::class[]
@EnableHazelcastHttpSession(maxInactiveIntervalInSeconds = "300")
@Configuration
public class Config {

	@Bean(destroyMethod = "shutdown")
	public HazelcastInstance hazelcastInstance() {
		com.hazelcast.config.Config cfg = new com.hazelcast.config.Config();
		NetworkConfig netConfig = new NetworkConfig();
		netConfig.setPort(SocketUtils.findAvailableTcpPort());
		System.out.println("Hazelcast port #: " + netConfig.getPort());
		cfg.setNetworkConfig(netConfig);
		SerializerConfig serializer = new SerializerConfig().setTypeClass(
				Object.class).setImplementation(new ObjectStreamSerializer());
		cfg.getSerializationConfig().addSerializerConfig(serializer);

		return Hazelcast.newHazelcastInstance(cfg);
	}

}
// end::class[]