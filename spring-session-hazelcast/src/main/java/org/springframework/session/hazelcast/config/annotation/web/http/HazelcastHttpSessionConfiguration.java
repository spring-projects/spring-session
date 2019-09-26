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

package org.springframework.session.hazelcast.config.annotation.web.http;

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
import org.springframework.session.FlushMode;
import org.springframework.session.IndexResolver;
import org.springframework.session.MapSession;
import org.springframework.session.SaveMode;
import org.springframework.session.Session;
import org.springframework.session.config.SessionRepositoryCustomizer;
import org.springframework.session.config.annotation.web.http.SpringHttpSessionConfiguration;
import org.springframework.session.hazelcast.HazelcastFlushMode;
import org.springframework.session.hazelcast.HazelcastIndexedSessionRepository;
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
@Configuration(proxyBeanMethods = false)
public class HazelcastHttpSessionConfiguration extends SpringHttpSessionConfiguration implements ImportAware {

	private Integer maxInactiveIntervalInSeconds = MapSession.DEFAULT_MAX_INACTIVE_INTERVAL_SECONDS;

	private String sessionMapName = HazelcastIndexedSessionRepository.DEFAULT_SESSION_MAP_NAME;

	private FlushMode flushMode = FlushMode.ON_SAVE;

	private SaveMode saveMode = SaveMode.ON_SET_ATTRIBUTE;

	private HazelcastInstance hazelcastInstance;

	private ApplicationEventPublisher applicationEventPublisher;

	private IndexResolver<Session> indexResolver;

	private List<SessionRepositoryCustomizer<HazelcastIndexedSessionRepository>> sessionRepositoryCustomizers;

	@Bean
	public HazelcastIndexedSessionRepository sessionRepository() {
		HazelcastIndexedSessionRepository sessionRepository = new HazelcastIndexedSessionRepository(
				this.hazelcastInstance);
		sessionRepository.setApplicationEventPublisher(this.applicationEventPublisher);
		if (this.indexResolver != null) {
			sessionRepository.setIndexResolver(this.indexResolver);
		}
		if (StringUtils.hasText(this.sessionMapName)) {
			sessionRepository.setSessionMapName(this.sessionMapName);
		}
		sessionRepository.setDefaultMaxInactiveInterval(this.maxInactiveIntervalInSeconds);
		sessionRepository.setFlushMode(this.flushMode);
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

	@Deprecated
	public void setHazelcastFlushMode(HazelcastFlushMode hazelcastFlushMode) {
		setFlushMode(hazelcastFlushMode.getFlushMode());
	}

	public void setFlushMode(FlushMode flushMode) {
		this.flushMode = flushMode;
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
	public void setIndexResolver(IndexResolver<Session> indexResolver) {
		this.indexResolver = indexResolver;
	}

	@Autowired(required = false)
	public void setSessionRepositoryCustomizer(
			ObjectProvider<SessionRepositoryCustomizer<HazelcastIndexedSessionRepository>> sessionRepositoryCustomizers) {
		this.sessionRepositoryCustomizers = sessionRepositoryCustomizers.orderedStream().collect(Collectors.toList());
	}

	@Override
	@SuppressWarnings("deprecation")
	public void setImportMetadata(AnnotationMetadata importMetadata) {
		Map<String, Object> attributeMap = importMetadata
				.getAnnotationAttributes(EnableHazelcastHttpSession.class.getName());
		AnnotationAttributes attributes = AnnotationAttributes.fromMap(attributeMap);
		this.maxInactiveIntervalInSeconds = attributes.getNumber("maxInactiveIntervalInSeconds");
		String sessionMapNameValue = attributes.getString("sessionMapName");
		if (StringUtils.hasText(sessionMapNameValue)) {
			this.sessionMapName = sessionMapNameValue;
		}
		FlushMode flushMode = attributes.getEnum("flushMode");
		HazelcastFlushMode hazelcastFlushMode = attributes.getEnum("hazelcastFlushMode");
		if (flushMode == FlushMode.ON_SAVE && hazelcastFlushMode != HazelcastFlushMode.ON_SAVE) {
			flushMode = hazelcastFlushMode.getFlushMode();
		}
		this.flushMode = flushMode;
		this.saveMode = attributes.getEnum("saveMode");
	}

}
