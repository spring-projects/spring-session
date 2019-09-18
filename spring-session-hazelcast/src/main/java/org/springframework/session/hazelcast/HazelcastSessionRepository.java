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

import com.hazelcast.core.HazelcastInstance;

import org.springframework.session.FlushMode;
import org.springframework.session.SessionRepository;
import org.springframework.util.Assert;

/**
 * This {@link SessionRepository} implementation is kept in order to support migration to
 * {@link HazelcastIndexedSessionRepository} in a backwards compatible manner.
 *
 * @author Vedran Pavic
 * @author Tommy Ludwig
 * @author Mark Anderson
 * @author Aleksandar Stojsavljevic
 * @since 1.3.0
 * @deprecated since 2.2.0 in favor of {@link HazelcastIndexedSessionRepository}
 */
@Deprecated
public class HazelcastSessionRepository extends HazelcastIndexedSessionRepository {

	/**
	 * Create a new {@link HazelcastSessionRepository} instance.
	 * @param hazelcastInstance the {@link HazelcastInstance} to use for managing sessions
	 * @see HazelcastIndexedSessionRepository#HazelcastIndexedSessionRepository(HazelcastInstance)
	 */
	public HazelcastSessionRepository(HazelcastInstance hazelcastInstance) {
		super(hazelcastInstance);
	}

	/**
	 * Sets the Hazelcast flush mode. Default flush mode is
	 * {@link HazelcastFlushMode#ON_SAVE}.
	 * @param hazelcastFlushMode the new Hazelcast flush mode
	 * @deprecated since 2.2.0 in favor of {@link #setFlushMode(FlushMode)}
	 */
	@Deprecated
	public void setHazelcastFlushMode(HazelcastFlushMode hazelcastFlushMode) {
		Assert.notNull(hazelcastFlushMode, "HazelcastFlushMode cannot be null");
		setFlushMode(hazelcastFlushMode.getFlushMode());
	}

}
