/*
 * Copyright 2014-2022 the original author or authors.
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

package org.springframework.session.hazelcast.config.annotation.web.server;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
import org.springframework.session.ReactiveSessionRepository;
import org.springframework.session.SaveMode;
import org.springframework.session.config.ReactiveSessionRepositoryCustomizer;
import org.springframework.session.config.annotation.web.server.SpringWebSessionConfiguration;
import org.springframework.session.hazelcast.ReactiveHazelcastSessionRepository;
import org.springframework.session.hazelcast.config.annotation.SpringSessionHazelcastInstance;
import org.springframework.util.StringUtils;
import org.springframework.web.server.session.WebSessionManager;

/**
 * Exposes the {@link WebSessionManager} as a bean named {@code webSessionManager}. In
 * order to use this a single {@link HazelcastInstance} must be exposed as a Bean.
 *
 * @author Tommy Ludwig
 * @author Vedran Pavic
 * @author Didier Loiseau
 * @since 2.6.4
 * @see EnableHazelcastWebSession
 */
@Configuration(proxyBeanMethods = false)
public class HazelcastWebSessionConfiguration extends SpringWebSessionConfiguration implements ImportAware {

	private Integer maxInactiveIntervalInSeconds = MapSession.DEFAULT_MAX_INACTIVE_INTERVAL_SECONDS;

	private String sessionMapName = ReactiveHazelcastSessionRepository.DEFAULT_SESSION_MAP_NAME;

	private SaveMode saveMode = SaveMode.ON_SET_ATTRIBUTE;

	private HazelcastInstance hazelcastInstance;

	private ApplicationEventPublisher applicationEventPublisher;

	private List<ReactiveSessionRepositoryCustomizer<ReactiveHazelcastSessionRepository>> sessionRepositoryCustomizers;

	@Bean
	public ReactiveSessionRepository<?> sessionRepository() {
		ReactiveHazelcastSessionRepository sessionRepository = new ReactiveHazelcastSessionRepository(
				this.hazelcastInstance);
		sessionRepository.setApplicationEventPublisher(this.applicationEventPublisher);
		if (StringUtils.hasText(this.sessionMapName)) {
			sessionRepository.setSessionMapName(this.sessionMapName);
		}
		sessionRepository.setDefaultMaxInactiveInterval(this.maxInactiveIntervalInSeconds);
		sessionRepository.setSaveMode(this.saveMode);
		this.sessionRepositoryCustomizers
				.forEach((sessionRepositoryCustomizer) -> sessionRepositoryCustomizer.customize(sessionRepository));
		return sessionRepository;
	}

	public void setMaxInactiveIntervalInSeconds(int maxInactiveIntervalInSeconds) {
		this.maxInactiveIntervalInSeconds = maxInactiveIntervalInSeconds;
	}

	public void setSessionMapName(String sessionMapName) {
		this.sessionMapName = sessionMapName;
	}

	public void setSaveMode(SaveMode saveMode) {
		this.saveMode = saveMode;
	}

	@Autowired
	public void setHazelcastInstance(
			@SpringSessionHazelcastInstance ObjectProvider<HazelcastInstance> springSessionHazelcastInstance,
			ObjectProvider<HazelcastInstance> hazelcastInstance) {
		HazelcastInstance hazelcastInstanceToUse = springSessionHazelcastInstance.getIfAvailable();
		if (hazelcastInstanceToUse == null) {
			hazelcastInstanceToUse = hazelcastInstance.getObject();
		}
		this.hazelcastInstance = hazelcastInstanceToUse;
	}

	@Autowired
	public void setApplicationEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
		this.applicationEventPublisher = applicationEventPublisher;
	}

	@Autowired(required = false)
	public void setSessionRepositoryCustomizer(
			ObjectProvider<ReactiveSessionRepositoryCustomizer<ReactiveHazelcastSessionRepository>> sessionRepositoryCustomizers) {
		this.sessionRepositoryCustomizers = sessionRepositoryCustomizers.orderedStream().collect(Collectors.toList());
	}

	@Override
	public void setImportMetadata(AnnotationMetadata importMetadata) {
		Map<String, Object> attributeMap = importMetadata
				.getAnnotationAttributes(EnableHazelcastWebSession.class.getName());
		AnnotationAttributes attributes = AnnotationAttributes.fromMap(attributeMap);
		this.maxInactiveIntervalInSeconds = attributes.getNumber("maxInactiveIntervalInSeconds");
		String sessionMapNameValue = attributes.getString("sessionMapName");
		if (StringUtils.hasText(sessionMapNameValue)) {
			this.sessionMapName = sessionMapNameValue;
		}
		this.saveMode = attributes.getEnum("saveMode");
	}

}
