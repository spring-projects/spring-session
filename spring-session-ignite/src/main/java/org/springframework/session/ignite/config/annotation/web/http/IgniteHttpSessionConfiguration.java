/*
 * Copyright 2014-2020 the original author or authors.
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

package org.springframework.session.ignite.config.annotation.web.http;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.ignite.Ignite;

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
import org.springframework.session.SessionRepository;
import org.springframework.session.config.SessionRepositoryCustomizer;
import org.springframework.session.config.annotation.web.http.SpringHttpSessionConfiguration;
import org.springframework.session.ignite.IgniteIndexedSessionRepository;
import org.springframework.session.ignite.config.annotation.SpringSessionIgnite;
import org.springframework.session.web.http.SessionRepositoryFilter;
import org.springframework.util.StringUtils;

/**
 * Exposes the {@link SessionRepositoryFilter} as a bean named
 * {@code springSessionRepositoryFilter}. In order to use this a single {@link Ignite}
 * must be exposed as a Bean.
 *
 * @author Semyon Danilov
 * @see EnableIgniteHttpSession
 * @since 2.5.0
 */
@Configuration(proxyBeanMethods = false)
public class IgniteHttpSessionConfiguration extends SpringHttpSessionConfiguration implements ImportAware {

	private Integer maxInactiveIntervalInSeconds = MapSession.DEFAULT_MAX_INACTIVE_INTERVAL_SECONDS;

	private String sessionMapName = IgniteIndexedSessionRepository.DEFAULT_SESSION_MAP_NAME;

	private FlushMode flushMode = FlushMode.ON_SAVE;

	private SaveMode saveMode = SaveMode.ON_SET_ATTRIBUTE;

	private Ignite ignite;

	private ApplicationEventPublisher applicationEventPublisher;

	private IndexResolver<Session> indexResolver;

	private List<SessionRepositoryCustomizer<IgniteIndexedSessionRepository>> sessionRepositoryCustomizers;

	@Bean
	public SessionRepository<?> sessionRepository() {
		return createIgniteIndexedSessionRepository();
	}

	public void setMaxInactiveIntervalInSeconds(int maxInactiveIntervalInSeconds) {
		this.maxInactiveIntervalInSeconds = maxInactiveIntervalInSeconds;
	}

	public void setSessionMapName(String sessionMapName) {
		this.sessionMapName = sessionMapName;
	}

	public void setFlushMode(FlushMode flushMode) {
		this.flushMode = flushMode;
	}

	public void setSaveMode(SaveMode saveMode) {
		this.saveMode = saveMode;
	}

	@Autowired
	public void setIgnite(@SpringSessionIgnite ObjectProvider<Ignite> springSessionIgnite,
			ObjectProvider<Ignite> ignite) {
		Ignite igniteToUse = springSessionIgnite.getIfAvailable();
		if (igniteToUse == null) {
			igniteToUse = ignite.getObject();
		}
		this.ignite = igniteToUse;
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
			ObjectProvider<SessionRepositoryCustomizer<IgniteIndexedSessionRepository>> sessionRepositoryCustomizers) {
		this.sessionRepositoryCustomizers = sessionRepositoryCustomizers.orderedStream().collect(Collectors.toList());
	}

	@Override
	@SuppressWarnings("deprecation")
	public void setImportMetadata(AnnotationMetadata importMetadata) {
		Map<String, Object> attributeMap = importMetadata
				.getAnnotationAttributes(EnableIgniteHttpSession.class.getName());
		AnnotationAttributes attributes = AnnotationAttributes.fromMap(attributeMap);
		this.maxInactiveIntervalInSeconds = attributes.getNumber("maxInactiveIntervalInSeconds");
		String sessionMapNameValue = attributes.getString("sessionMapName");
		if (StringUtils.hasText(sessionMapNameValue)) {
			this.sessionMapName = sessionMapNameValue;
		}
		this.flushMode = attributes.getEnum("flushMode");
		this.saveMode = attributes.getEnum("saveMode");
	}

	private IgniteIndexedSessionRepository createIgniteIndexedSessionRepository() {
		IgniteIndexedSessionRepository sessionRepository = new IgniteIndexedSessionRepository(this.ignite);
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

}
