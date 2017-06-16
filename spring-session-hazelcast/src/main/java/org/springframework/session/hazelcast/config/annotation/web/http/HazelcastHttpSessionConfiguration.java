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

package org.springframework.session.hazelcast.config.annotation.web.http;

import java.util.Map;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IMap;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportAware;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.session.MapSession;
import org.springframework.session.config.annotation.web.http.SpringHttpSessionConfiguration;
import org.springframework.session.hazelcast.HazelcastFlushMode;
import org.springframework.session.hazelcast.HazelcastSessionRepository;
import org.springframework.session.web.http.SessionRepositoryFilter;

/**
 * Exposes the {@link SessionRepositoryFilter} as a bean named
 * "springSessionRepositoryFilter". In order to use this a single
 * {@link HazelcastInstance} must be exposed as a Bean.
 *
 * @author Tommy Ludwig
 * @author Vedran Pavic
 * @since 1.1
 * @see EnableHazelcastHttpSession
 */
@Configuration
public class HazelcastHttpSessionConfiguration extends SpringHttpSessionConfiguration
		implements ImportAware {

	static final String DEFAULT_SESSION_MAP_NAME = "spring:session:sessions";

	private Integer maxInactiveIntervalInSeconds;

	private String sessionMapName = DEFAULT_SESSION_MAP_NAME;

	private HazelcastFlushMode hazelcastFlushMode = HazelcastFlushMode.ON_SAVE;

	@Bean
	public HazelcastSessionRepository sessionRepository(
			HazelcastInstance hazelcastInstance,
			ApplicationEventPublisher eventPublisher) {
		IMap<String, MapSession> sessions = hazelcastInstance.getMap(
				this.sessionMapName);
		HazelcastSessionRepository sessionRepository = new HazelcastSessionRepository(
				sessions);
		sessionRepository.setApplicationEventPublisher(eventPublisher);
		sessionRepository.setDefaultMaxInactiveInterval(
				this.maxInactiveIntervalInSeconds);
		sessionRepository.setHazelcastFlushMode(this.hazelcastFlushMode);
		return sessionRepository;
	}

	public void setImportMetadata(AnnotationMetadata importMetadata) {
		Map<String, Object> enableAttrMap = importMetadata
				.getAnnotationAttributes(EnableHazelcastHttpSession.class.getName());
		AnnotationAttributes enableAttrs = AnnotationAttributes.fromMap(enableAttrMap);
		setMaxInactiveIntervalInSeconds(
				(Integer) enableAttrs.getNumber("maxInactiveIntervalInSeconds"));
		setSessionMapName(enableAttrs.getString("sessionMapName"));
		setHazelcastFlushMode(
				(HazelcastFlushMode) enableAttrs.getEnum("hazelcastFlushMode"));
	}

	public void setMaxInactiveIntervalInSeconds(int maxInactiveIntervalInSeconds) {
		this.maxInactiveIntervalInSeconds = maxInactiveIntervalInSeconds;
	}

	public void setSessionMapName(String sessionMapName) {
		this.sessionMapName = sessionMapName;
	}

	public void setHazelcastFlushMode(HazelcastFlushMode hazelcastFlushMode) {
		this.hazelcastFlushMode = hazelcastFlushMode;
	}

}
