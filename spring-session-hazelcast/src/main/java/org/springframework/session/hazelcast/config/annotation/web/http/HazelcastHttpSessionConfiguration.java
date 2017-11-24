/*
 * Copyright 2014-2017 the original author or authors.
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

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
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
import org.springframework.session.hazelcast.config.annotation.SpringSessionHazelcastInstance;
import org.springframework.session.web.http.SessionRepositoryFilter;
import org.springframework.util.StringUtils;

/**
 * Exposes the {@link SessionRepositoryFilter} as a bean named
 * {@code springSessionRepositoryFilter}. In order to use this a single
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

	private Integer maxInactiveIntervalInSeconds = MapSession.DEFAULT_MAX_INACTIVE_INTERVAL_SECONDS;

	private String sessionMapName = HazelcastSessionRepository.DEFAULT_SESSION_MAP_NAME;

	private HazelcastFlushMode hazelcastFlushMode = HazelcastFlushMode.ON_SAVE;

	private HazelcastInstance hazelcastInstance;

	private ApplicationEventPublisher applicationEventPublisher;

	@Bean
	public HazelcastSessionRepository sessionRepository() {
		HazelcastSessionRepository sessionRepository = new HazelcastSessionRepository(
				this.hazelcastInstance);
		sessionRepository.setApplicationEventPublisher(this.applicationEventPublisher);
		if (StringUtils.hasText(this.sessionMapName)) {
			sessionRepository.setSessionMapName(this.sessionMapName);
		}
		sessionRepository
				.setDefaultMaxInactiveInterval(this.maxInactiveIntervalInSeconds);
		sessionRepository.setHazelcastFlushMode(this.hazelcastFlushMode);
		return sessionRepository;
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

	@Autowired
	public void setHazelcastInstance(
			@SpringSessionHazelcastInstance ObjectProvider<HazelcastInstance> springSessionHazelcastInstance,
			ObjectProvider<HazelcastInstance> hazelcastInstance) {
		HazelcastInstance hazelcastInstanceToUse = springSessionHazelcastInstance
				.getIfAvailable();
		if (hazelcastInstanceToUse == null) {
			hazelcastInstanceToUse = hazelcastInstance.getObject();
		}
		this.hazelcastInstance = hazelcastInstanceToUse;
	}

	@Autowired
	public void setApplicationEventPublisher(
			ApplicationEventPublisher applicationEventPublisher) {
		this.applicationEventPublisher = applicationEventPublisher;
	}

	@Override
	public void setImportMetadata(AnnotationMetadata importMetadata) {
		Map<String, Object> attributeMap = importMetadata
				.getAnnotationAttributes(EnableHazelcastHttpSession.class.getName());
		AnnotationAttributes attributes = AnnotationAttributes.fromMap(attributeMap);
		this.maxInactiveIntervalInSeconds =
				attributes.getNumber("maxInactiveIntervalInSeconds");
		String sessionMapNameValue = attributes.getString("sessionMapName");
		if (StringUtils.hasText(sessionMapNameValue)) {
			this.sessionMapName = sessionMapNameValue;
		}
		this.hazelcastFlushMode = attributes.getEnum("hazelcastFlushMode");
	}

}
