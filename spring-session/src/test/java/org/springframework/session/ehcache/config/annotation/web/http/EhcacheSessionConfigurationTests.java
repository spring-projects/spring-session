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

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.ehcache.EhcacheSessionRepository;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link EhcacheHttpSessionConfiguration}.
 *
 * @author Ján Pichanič
 * @since 1.2.0
 */
public class EhcacheSessionConfigurationTests {

	private static final int CUSTOM_MAX_INACTIVE_INTERVAL_IN_SECONDS = 600;
	private static final String CUSTOM_CACHE_NAME = "customHttpCacheName";
	private static final int CUSTOM_MAX_HEAP_ENTRIES = 20000;

	@Rule
	public final ExpectedException thrown = ExpectedException.none();

	private AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();

	@After
	public void close() {
		if (this.context != null) {
			this.context.close();
		}
	}

	private void registerAndRefresh(Class<?>... annotatedClasses) {
		this.context.register(annotatedClasses);
		this.context.refresh();
	}

	@Test
	public void defaultConfiguration() {
		registerAndRefresh(DefaultConfiguration.class);

		assertThat(this.context.getBean(EhcacheSessionRepository.class))
				.isNotNull();
	}

	@Test
	public void customMaxInactiveIntervalInSeconds() {
		registerAndRefresh(CustomMaxInactiveIntervalInSecondsConfiguration.class);

		EhcacheSessionRepository repository = this.context
				.getBean(EhcacheSessionRepository.class);
		assertThat(repository).isNotNull();
		assertThat(ReflectionTestUtils.getField(repository, "maxInactiveIntervalInSeconds"))
				.isEqualTo(CUSTOM_MAX_INACTIVE_INTERVAL_IN_SECONDS);
	}

	@Test
	public void customCacheName() {
		registerAndRefresh(CustomCacheNameConfiguration.class);
		EhcacheHttpSessionConfiguration configuration = this.context
				.getBean(EhcacheHttpSessionConfiguration.class);
		assertThat(configuration).isNotNull();
		assertThat(ReflectionTestUtils.getField(configuration, "cacheName"))
				.isEqualTo(CUSTOM_CACHE_NAME);
	}

	@Test
	public void customMaxHeapEntries() {
		registerAndRefresh(CustomMaxHeapEntriesConfiguration.class);
		EhcacheHttpSessionConfiguration configuration = this.context
				.getBean(EhcacheHttpSessionConfiguration.class);
		assertThat(configuration).isNotNull();
		assertThat(ReflectionTestUtils.getField(configuration, "maxHeapEntries"))
				.isEqualTo(CUSTOM_MAX_HEAP_ENTRIES);
	}

	@Configuration
	@EnableEhcacheHttpSession
	static class DefaultConfiguration {

	}

	@Configuration
	@EnableEhcacheHttpSession(maxInactiveIntervalInSeconds = CUSTOM_MAX_INACTIVE_INTERVAL_IN_SECONDS)
	static class CustomMaxInactiveIntervalInSecondsConfiguration {

	}

	@Configuration
	@EnableEhcacheHttpSession(cacheName = CUSTOM_CACHE_NAME)
	static class CustomCacheNameConfiguration {

	}

	@Configuration
	@EnableEhcacheHttpSession(maxHeapEntries = CUSTOM_MAX_HEAP_ENTRIES)
	static class CustomMaxHeapEntriesConfiguration {

	}
}
