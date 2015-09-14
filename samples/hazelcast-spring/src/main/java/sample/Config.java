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

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.MapSessionRepository;
import org.springframework.session.config.annotation.web.http.EnableSpringHttpSession;
import org.springframework.session.ExpiringSession;
import org.springframework.util.SocketUtils;

import com.hazelcast.config.MapConfig;
import com.hazelcast.config.NetworkConfig;
import com.hazelcast.config.SerializerConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IMap;

// tag::class[]
@EnableSpringHttpSession
@Configuration
public class Config {

	private String sessionMapName = "spring:session:sessions";

	@Autowired
	private ApplicationEventPublisher eventPublisher;

	@Bean(destroyMethod = "shutdown")
	public HazelcastInstance hazelcastInstance() {
		com.hazelcast.config.Config cfg = new com.hazelcast.config.Config();
		NetworkConfig netConfig = new NetworkConfig();
		netConfig.setPort(SocketUtils.findAvailableTcpPort());
		cfg.setNetworkConfig(netConfig);
		SerializerConfig serializer = new SerializerConfig().setTypeClass(
				Object.class).setImplementation(new ObjectStreamSerializer());
		cfg.getSerializationConfig().addSerializerConfig(serializer);
		MapConfig mc = new MapConfig();
		mc.setName(sessionMapName);

		mc.setMaxIdleSeconds(60);
		cfg.addMapConfig(mc);

		return Hazelcast.newHazelcastInstance(cfg);
	}

	@Bean
	public SessionRemovedListener removeListener() {
		return new SessionRemovedListener(eventPublisher);
	}

	@Bean
	public SessionEvictedListener evictListener() {
		return new SessionEvictedListener(eventPublisher);
	}

	@Bean
	public SessionCreatedListener addListener() {
		return new SessionCreatedListener(eventPublisher);
	}

	@Bean
	public MapSessionRepository sessionRepository(HazelcastInstance instance,
			SessionRemovedListener removeListener, SessionEvictedListener evictListener,
			SessionCreatedListener addListener) {
		IMap<String, ExpiringSession> sessions = instance.getMap(sessionMapName);
		sessions.addEntryListener(removeListener, true);
		sessions.addEntryListener(evictListener, true);
		sessions.addEntryListener(addListener, true);
		return new MapSessionRepository(sessions);
	}
}
// end::class[]