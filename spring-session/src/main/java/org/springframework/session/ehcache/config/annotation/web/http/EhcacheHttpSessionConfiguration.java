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

package org.springframework.session.ehcache.config.annotation.web.http;

import java.util.Map;

import net.sf.ehcache.CacheManager;
import net.sf.ehcache.config.CacheConfiguration;
import net.sf.ehcache.config.Searchable;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportAware;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.session.config.annotation.web.http.SpringHttpSessionConfiguration;
import org.springframework.session.ehcache.EhcacheSessionRepository;


/**
 * Spring @Configuration class used to configure and initialize a {@link net.sf.ehcache.Ehcache} based HttpSession
 * provider implementation in Spring Session.
 * <p>
 * Exposes the {@link org.springframework.session.web.http.SessionRepositoryFilter} as a
 * bean named "springSessionRepositoryFilter".
 *
 * @author Ján Pichanič
 * @since 1.2.0
 * @see EnableEhcacheHttpSession
 */
@Configuration
public class EhcacheHttpSessionConfiguration extends SpringHttpSessionConfiguration implements ImportAware {

	private String cacheName;
	private int maxInactiveIntervalInSeconds;
	private int maxHeapEntries;

	@Bean
	public EhcacheSessionRepository sessionRepository(ApplicationEventPublisher applicationEventPublisher) {
		EhcacheSessionRepository repository = new EhcacheSessionRepository(cacheManager().getCache(this.cacheName));
		repository.setMaxInactiveIntervalInSeconds(this.maxInactiveIntervalInSeconds);
		repository.setApplicationEventPublisher(applicationEventPublisher);
		return repository;
	}

	@Bean(destroyMethod = "shutdown")
	public CacheManager cacheManager() {
		CacheConfiguration cacheConfiguration = new CacheConfiguration(this.cacheName, this.maxHeapEntries);
		Searchable searchable = new Searchable();
		searchable.allowDynamicIndexing(true);
		cacheConfiguration.addSearchable(searchable);
		net.sf.ehcache.config.Configuration configuration = new net.sf.ehcache.config.Configuration();
		configuration.addCache(cacheConfiguration);
		return CacheManager.newInstance(configuration);
	}

	public void setImportMetadata(AnnotationMetadata importMetadata) {
		Map<String, Object> enableAttrMap = importMetadata
				.getAnnotationAttributes(EnableEhcacheHttpSession.class.getName());
		AnnotationAttributes enableAttrs = AnnotationAttributes.fromMap(enableAttrMap);
		this.cacheName = enableAttrs.getString("cacheName");
		this.maxInactiveIntervalInSeconds = enableAttrs.getNumber("maxInactiveIntervalInSeconds");
		this.maxHeapEntries = enableAttrs.getNumber("maxHeapEntries");
	}
}
