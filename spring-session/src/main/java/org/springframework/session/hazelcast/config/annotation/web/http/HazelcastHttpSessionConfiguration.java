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
package org.springframework.session.hazelcast.config.annotation.web.http;

import java.util.Map;

import javax.annotation.PreDestroy;

import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportAware;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.session.ExpiringSession;
import org.springframework.session.MapSessionRepository;
import org.springframework.session.SessionRepository;
import org.springframework.session.config.annotation.web.http.SpringHttpSessionConfiguration;
import org.springframework.session.hazelcast.SessionEntryListener;
import org.springframework.session.web.http.SessionRepositoryFilter;
import org.springframework.util.ClassUtils;

import com.hazelcast.config.MapConfig;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IMap;

/**
 * Exposes the {@link SessionRepositoryFilter} as a bean named
 * "springSessionRepositoryFilter". In order to use this a single
 * {@link HazelcastInstance} must be exposed as a Bean.
 *
 * @author Tommy Ludwig
 * @since 1.1
 * @see EnableHazelcastHttpSession
 */
@Configuration
public class HazelcastHttpSessionConfiguration extends SpringHttpSessionConfiguration implements ImportAware, BeanClassLoaderAware {

	/** This is the magic value to use if you do not want this configuration
	 * overriding the maxIdleSeconds value for the Map backing the session data. */
	private static final String DO_NOT_CONFIGURE_INACTIVE_INTERVAL_STRING = "";

	private ClassLoader beanClassLoader;

	private Integer maxInactiveIntervalInSeconds = 1800;

	private String sessionMapName = "spring:session:sessions";

	private String sessionListenerUid;

	private IMap<String, ExpiringSession> sessionsMap;

	@Bean
	public SessionRepository<ExpiringSession> sessionRepository(HazelcastInstance hazelcastInstance, SessionEntryListener sessionListener) {
		configureSessionMap(hazelcastInstance);
		this.sessionsMap = hazelcastInstance.getMap(sessionMapName);
		this.sessionListenerUid = this.sessionsMap.addEntryListener(sessionListener, true);

		MapSessionRepository sessionRepository = new MapSessionRepository(this.sessionsMap);
		sessionRepository.setDefaultMaxInactiveInterval(maxInactiveIntervalInSeconds);

		return sessionRepository;
	}

	@PreDestroy
	private void removeSessionListener() {
		this.sessionsMap.removeEntryListener(this.sessionListenerUid);
	}

	@Bean
	public SessionEntryListener sessionListener(ApplicationEventPublisher eventPublisher) {
		return new SessionEntryListener(eventPublisher);
	}

	/**
	 * Make a {@link MapConfig} for the given sessionMapName if one does not exist.
	 * Set Hazelcast's maxIdleSeconds to maxInactiveIntervalInSeconds if set (not "").
	 * Otherwise get the externally configured maxIdleSeconds for the distributed sessions map.
	 *
	 * @param hazelcastInstance the {@link HazelcastInstance} to configure
	 */
	private void configureSessionMap(HazelcastInstance hazelcastInstance) {
		MapConfig sessionMapConfig = hazelcastInstance.getConfig().getMapConfig(sessionMapName);
		if (this.maxInactiveIntervalInSeconds != null) {
			sessionMapConfig.setMaxIdleSeconds(this.maxInactiveIntervalInSeconds);
		} else {
			this.maxInactiveIntervalInSeconds = sessionMapConfig.getMaxIdleSeconds();
		}
	}

	public void setImportMetadata(AnnotationMetadata importMetadata) {
		Map<String, Object> enableAttrMap = importMetadata.getAnnotationAttributes(EnableHazelcastHttpSession.class.getName());
		AnnotationAttributes enableAttrs = AnnotationAttributes.fromMap(enableAttrMap);
		if (enableAttrs == null) {
			// search parent classes
			Class<?> currentClass = ClassUtils.resolveClassName(importMetadata.getClassName(), beanClassLoader);
			for (Class<?> classToInspect = currentClass; classToInspect != null; classToInspect = classToInspect.getSuperclass()) {
				EnableHazelcastHttpSession enableHazelcastHttpSessionAnnotation = AnnotationUtils.findAnnotation(classToInspect, EnableHazelcastHttpSession.class);
				if (enableHazelcastHttpSessionAnnotation == null) {
					continue;
				}
				enableAttrMap = AnnotationUtils
						.getAnnotationAttributes(enableHazelcastHttpSessionAnnotation);
				enableAttrs = AnnotationAttributes.fromMap(enableAttrMap);
			}
		}

		transferAnnotationAttributes(enableAttrs);
	}

	private void transferAnnotationAttributes(AnnotationAttributes enableAttrs) {
		String maxInactiveIntervalString = enableAttrs.getString("maxInactiveIntervalInSeconds");

		if (DO_NOT_CONFIGURE_INACTIVE_INTERVAL_STRING.equals(maxInactiveIntervalString)) {
			this.maxInactiveIntervalInSeconds = null;
		} else {
			try {
				this.maxInactiveIntervalInSeconds = Integer.parseInt(maxInactiveIntervalString);
			} catch (NumberFormatException nfe) {
				throw new IllegalArgumentException(
						"@EnableHazelcastHttpSession's maxInactiveIntervalInSeconds expects an int format String but was '"
								+ maxInactiveIntervalString + "' instead.", nfe);
			}
		}
		setSessionMapName(enableAttrs.getString("sessionMapName"));
	}

	public void setMaxInactiveIntervalInSeconds(int maxInactiveIntervalInSeconds) {
		this.maxInactiveIntervalInSeconds = maxInactiveIntervalInSeconds;
	}

	public void setSessionMapName(String sessionMapName) {
		this.sessionMapName = sessionMapName;
	}

	public void setBeanClassLoader(ClassLoader classLoader) {
		this.beanClassLoader = classLoader;
	}

}
