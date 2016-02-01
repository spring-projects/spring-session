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

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import javax.annotation.PreDestroy;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportAware;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.session.ExpiringSession;
import org.springframework.session.MapSessionRepository;
import org.springframework.session.SessionRepository;
import org.springframework.session.config.annotation.web.http.SpringHttpSessionConfiguration;
import org.springframework.session.hazelcast.SessionEntryListener;
import org.springframework.session.web.http.SessionRepositoryFilter;

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
public class HazelcastHttpSessionConfiguration extends SpringHttpSessionConfiguration implements ImportAware {

	private Integer maxInactiveIntervalInSeconds = 1800;

	private String sessionMapName = "spring:session:sessions";

	private String sessionListenerUid;

	private IMap<String, ExpiringSession> sessionsMap;

	@Bean
	public SessionRepository<ExpiringSession> sessionRepository(HazelcastInstance hazelcastInstance, SessionEntryListener sessionListener) {
		this.sessionsMap = hazelcastInstance.getMap(sessionMapName);
		this.sessionListenerUid = this.sessionsMap.addEntryListener(sessionListener, true);

		MapSessionRepository sessionRepository = new MapSessionRepository(new ExpiringSessionMap(this.sessionsMap));
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

	public void setImportMetadata(AnnotationMetadata importMetadata) {
		Map<String, Object> enableAttrMap = importMetadata.getAnnotationAttributes(EnableHazelcastHttpSession.class.getName());
		AnnotationAttributes enableAttrs = AnnotationAttributes.fromMap(enableAttrMap);

		transferAnnotationAttributes(enableAttrs);
	}

	private void transferAnnotationAttributes(AnnotationAttributes enableAttrs) {
		setMaxInactiveIntervalInSeconds((Integer) enableAttrs.getNumber("maxInactiveIntervalInSeconds"));
		setSessionMapName(enableAttrs.getString("sessionMapName"));
	}

	public void setMaxInactiveIntervalInSeconds(int maxInactiveIntervalInSeconds) {
		this.maxInactiveIntervalInSeconds = maxInactiveIntervalInSeconds;
	}

	public void setSessionMapName(String sessionMapName) {
		this.sessionMapName = sessionMapName;
	}
	
	static class ExpiringSessionMap implements Map<String, ExpiringSession> {
	    private IMap<String,ExpiringSession> delegate;

	    ExpiringSessionMap(IMap<String,ExpiringSession> delegate) {
	        this.delegate = delegate;
	    }
	    public ExpiringSession put(String key, ExpiringSession value) {
	        if(value == null) {
	            return delegate.put(key, value);
	        }
	        return delegate.put(key, value, value.getMaxInactiveIntervalInSeconds(), TimeUnit.SECONDS);
	    }

	    public int size() {
	        return delegate.size();
	    }

	    public boolean isEmpty() {
	        return delegate.isEmpty();
	    }

	    public boolean containsKey(Object key) {
	        return delegate.containsKey(key);
	    }

	    public boolean containsValue(Object value) {
	        return delegate.containsValue(value);
	    }

	    public ExpiringSession get(Object key) {
	        return delegate.get(key);
	    }

	    public ExpiringSession remove(Object key) {
	        return delegate.remove(key);
	    }

	    public void putAll(Map<? extends String, ? extends ExpiringSession> m) {
	        delegate.putAll(m);
	    }

	    public void clear() {
	        delegate.clear();
	    }

	    public Set<String> keySet() {
	        return delegate.keySet();
	    }

	    public Collection<ExpiringSession> values() {
	        return delegate.values();
	    }

	    public Set<java.util.Map.Entry<String, ExpiringSession>> entrySet() {
	        return delegate.entrySet();
	    }

	    public boolean equals(Object o) {
	        return delegate.equals(o);
	    }

	    public int hashCode() {
	        return delegate.hashCode();
	    }
	}
}
