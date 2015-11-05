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
package org.springframework.session.hazelcast;

import static org.fest.assertions.Assertions.assertThat;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.ExpiringSession;
import org.springframework.session.SessionRepository;
import org.springframework.session.hazelcast.config.annotation.web.http.EnableHazelcastHttpSession;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.util.SocketUtils;

import com.hazelcast.config.Config;
import com.hazelcast.config.NetworkConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IMap;

/**
 * Integration tests that check the underlying data source - in this case
 * Hazelcast.
 * 
 * @author Tommy Ludwig
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration
@WebAppConfiguration
public class HazelcastRepositoryITests<S extends ExpiringSession> {

	@Autowired
	private HazelcastInstance hazelcast;
	
	@Autowired
	private SessionRepository<S> repository;
	
	@Test
	public void createAndDestorySession() {
		S sessionToSave = repository.createSession();
		String sessionId = sessionToSave.getId();
		
		IMap<String, S> hazelcastMap = hazelcast.getMap("spring:session:sessions");
		
		assertThat(hazelcastMap.size()).isEqualTo(0);
		
		repository.save(sessionToSave);
		
		assertThat(hazelcastMap.size()).isEqualTo(1);
		assertThat(hazelcastMap.get(sessionId)).isEqualTo(sessionToSave);
		
		repository.delete(sessionId);
		
		assertThat(hazelcastMap.size()).isEqualTo(0);
	}

	@EnableHazelcastHttpSession
	@Configuration
	static class HazelcastSessionConfig {

		@Bean
		public HazelcastInstance embeddedHazelcast() {
			Config hazelcastConfig = new Config();
			NetworkConfig netConfig = new NetworkConfig();
			netConfig.setPort(SocketUtils.findAvailableTcpPort());
			hazelcastConfig.setNetworkConfig(netConfig);
			return Hazelcast.newHazelcastInstance(hazelcastConfig);
		}
	}

}
