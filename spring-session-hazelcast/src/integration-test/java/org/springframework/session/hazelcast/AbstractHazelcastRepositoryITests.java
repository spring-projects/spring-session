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

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IMap;
import org.junit.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.session.MapSession;
import org.springframework.session.hazelcast.HazelcastSessionRepository.HazelcastSession;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Abstract base class for Hazelcast integration tests.
 *
 * @author Tommy Ludwig
 * @author Vedran Pavic
 */
public abstract class AbstractHazelcastRepositoryITests {

	@Autowired
	private HazelcastInstance hazelcast;

	@Autowired
	private HazelcastSessionRepository repository;

	@Test
	public void createAndDestroySession() {
		HazelcastSession sessionToSave = this.repository.createSession();
		String sessionId = sessionToSave.getId();

		IMap<String, MapSession> hazelcastMap = this.hazelcast.getMap(
				"spring:session:sessions");

		assertThat(hazelcastMap.size()).isEqualTo(0);

		this.repository.save(sessionToSave);

		assertThat(hazelcastMap.size()).isEqualTo(1);
		assertThat(hazelcastMap.get(sessionId)).isEqualTo(sessionToSave);

		this.repository.delete(sessionId);

		assertThat(hazelcastMap.size()).isEqualTo(0);
	}

}
