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

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.BeanCreationException;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.annotation.Order;
import org.springframework.session.FlushMode;
import org.springframework.session.IndexResolver;
import org.springframework.session.SaveMode;
import org.springframework.session.Session;
import org.springframework.session.config.SessionRepositoryCustomizer;
import org.springframework.session.hazelcast.HazelcastFlushMode;
import org.springframework.session.hazelcast.HazelcastIndexedSessionRepository;
import org.springframework.session.hazelcast.config.annotation.SpringSessionHazelcastInstance;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * Tests for {@link HazelcastHttpSessionConfiguration}.
 *
 * @author Vedran Pavic
 * @author Aleksandar Stojsavljevic
 */
class HazelcastHttpSessionConfigurationTests {

	private static final String MAP_NAME = "spring:test:sessions";

	private static final int MAX_INACTIVE_INTERVAL_IN_SECONDS = 600;

	private AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();

	@AfterEach
	void closeContext() {
		if (this.context != null) {
			this.context.close();
		}
	}

	@Test
	void noHazelcastInstanceConfiguration() {
		assertThatExceptionOfType(BeanCreationException.class)
				.isThrownBy(() -> registerAndRefresh(NoHazelcastInstanceConfiguration.class))
				.withMessageContaining("HazelcastInstance");
	}

	@Test
	void defaultConfiguration() {
		registerAndRefresh(DefaultConfiguration.class);

		assertThat(this.context.getBean(HazelcastIndexedSessionRepository.class)).isNotNull();
	}

	@Test
	void customTableName() {
		registerAndRefresh(CustomSessionMapNameConfiguration.class);

		HazelcastIndexedSessionRepository repository = this.context.getBean(HazelcastIndexedSessionRepository.class);
		HazelcastHttpSessionConfiguration configuration = this.context.getBean(HazelcastHttpSessionConfiguration.class);
		assertThat(repository).isNotNull();
		assertThat(ReflectionTestUtils.getField(configuration, "sessionMapName")).isEqualTo(MAP_NAME);
	}

	@Test
	void setCustomSessionMapName() {
		registerAndRefresh(BaseConfiguration.class, CustomSessionMapNameSetConfiguration.class);

		HazelcastIndexedSessionRepository repository = this.context.getBean(HazelcastIndexedSessionRepository.class);
		HazelcastHttpSessionConfiguration configuration = this.context.getBean(HazelcastHttpSessionConfiguration.class);
		assertThat(repository).isNotNull();
		assertThat(ReflectionTestUtils.getField(configuration, "sessionMapName")).isEqualTo(MAP_NAME);
	}

	@Test
	void setCustomMaxInactiveIntervalInSeconds() {
		registerAndRefresh(BaseConfiguration.class, CustomMaxInactiveIntervalInSecondsSetConfiguration.class);

		HazelcastIndexedSessionRepository repository = this.context.getBean(HazelcastIndexedSessionRepository.class);
		assertThat(repository).isNotNull();
		assertThat(ReflectionTestUtils.getField(repository, "defaultMaxInactiveInterval"))
				.isEqualTo(MAX_INACTIVE_INTERVAL_IN_SECONDS);
	}

	@Test
	void customMaxInactiveIntervalInSeconds() {
		registerAndRefresh(CustomMaxInactiveIntervalInSecondsConfiguration.class);

		HazelcastIndexedSessionRepository repository = this.context.getBean(HazelcastIndexedSessionRepository.class);
		assertThat(repository).isNotNull();
		assertThat(ReflectionTestUtils.getField(repository, "defaultMaxInactiveInterval"))
				.isEqualTo(MAX_INACTIVE_INTERVAL_IN_SECONDS);
	}

	@Test
	void customFlushImmediately() {
		registerAndRefresh(CustomFlushImmediatelyConfiguration.class);

		HazelcastIndexedSessionRepository repository = this.context.getBean(HazelcastIndexedSessionRepository.class);
		assertThat(repository).isNotNull();
		assertThat(ReflectionTestUtils.getField(repository, "flushMode")).isEqualTo(FlushMode.IMMEDIATE);
	}

	@Test
	void customFlushImmediatelyLegacy() {
		registerAndRefresh(CustomFlushImmediatelyLegacyConfiguration.class);

		HazelcastIndexedSessionRepository repository = this.context.getBean(HazelcastIndexedSessionRepository.class);
		assertThat(repository).isNotNull();
		assertThat(ReflectionTestUtils.getField(repository, "flushMode")).isEqualTo(FlushMode.IMMEDIATE);
	}

	@Test
	void setCustomFlushImmediately() {
		registerAndRefresh(BaseConfiguration.class, CustomFlushImmediatelySetConfiguration.class);

		HazelcastIndexedSessionRepository repository = this.context.getBean(HazelcastIndexedSessionRepository.class);
		assertThat(repository).isNotNull();
		assertThat(ReflectionTestUtils.getField(repository, "flushMode")).isEqualTo(FlushMode.IMMEDIATE);
	}

	@Test
	void setCustomFlushImmediatelyLegacy() {
		registerAndRefresh(BaseConfiguration.class, CustomFlushImmediatelySetLegacyConfiguration.class);

		HazelcastIndexedSessionRepository repository = this.context.getBean(HazelcastIndexedSessionRepository.class);
		assertThat(repository).isNotNull();
		assertThat(ReflectionTestUtils.getField(repository, "flushMode")).isEqualTo(FlushMode.IMMEDIATE);
	}

	@Test
	void customSaveModeAnnotation() {
		registerAndRefresh(BaseConfiguration.class, CustomSaveModeExpressionAnnotationConfiguration.class);
		assertThat(this.context.getBean(HazelcastIndexedSessionRepository.class))
				.hasFieldOrPropertyWithValue("saveMode", SaveMode.ALWAYS);
	}

	@Test
	void customSaveModeSetter() {
		registerAndRefresh(BaseConfiguration.class, CustomSaveModeExpressionSetterConfiguration.class);
		assertThat(this.context.getBean(HazelcastIndexedSessionRepository.class))
				.hasFieldOrPropertyWithValue("saveMode", SaveMode.ALWAYS);
	}

	@Test
	void qualifiedHazelcastInstanceConfiguration() {
		registerAndRefresh(QualifiedHazelcastInstanceConfiguration.class);

		HazelcastIndexedSessionRepository repository = this.context.getBean(HazelcastIndexedSessionRepository.class);
		HazelcastInstance hazelcastInstance = this.context.getBean("qualifiedHazelcastInstance",
				HazelcastInstance.class);
		assertThat(repository).isNotNull();
		assertThat(hazelcastInstance).isNotNull();
		assertThat(ReflectionTestUtils.getField(repository, "sessions"))
				.isEqualTo(QualifiedHazelcastInstanceConfiguration.qualifiedHazelcastInstanceSessions);
	}

	@Test
	void primaryHazelcastInstanceConfiguration() {
		registerAndRefresh(PrimaryHazelcastInstanceConfiguration.class);

		HazelcastIndexedSessionRepository repository = this.context.getBean(HazelcastIndexedSessionRepository.class);
		HazelcastInstance hazelcastInstance = this.context.getBean("primaryHazelcastInstance", HazelcastInstance.class);
		assertThat(repository).isNotNull();
		assertThat(hazelcastInstance).isNotNull();
		assertThat(ReflectionTestUtils.getField(repository, "sessions"))
				.isEqualTo(PrimaryHazelcastInstanceConfiguration.primaryHazelcastInstanceSessions);
	}

	@Test
	void qualifiedAndPrimaryHazelcastInstanceConfiguration() {
		registerAndRefresh(QualifiedAndPrimaryHazelcastInstanceConfiguration.class);

		HazelcastIndexedSessionRepository repository = this.context.getBean(HazelcastIndexedSessionRepository.class);
		HazelcastInstance hazelcastInstance = this.context.getBean("qualifiedHazelcastInstance",
				HazelcastInstance.class);
		assertThat(repository).isNotNull();
		assertThat(hazelcastInstance).isNotNull();
		assertThat(ReflectionTestUtils.getField(repository, "sessions"))
				.isEqualTo(QualifiedAndPrimaryHazelcastInstanceConfiguration.qualifiedHazelcastInstanceSessions);
	}

	@Test
	void namedHazelcastInstanceConfiguration() {
		registerAndRefresh(NamedHazelcastInstanceConfiguration.class);

		HazelcastIndexedSessionRepository repository = this.context.getBean(HazelcastIndexedSessionRepository.class);
		HazelcastInstance hazelcastInstance = this.context.getBean("hazelcastInstance", HazelcastInstance.class);
		assertThat(repository).isNotNull();
		assertThat(hazelcastInstance).isNotNull();
		assertThat(ReflectionTestUtils.getField(repository, "sessions"))
				.isEqualTo(NamedHazelcastInstanceConfiguration.hazelcastInstanceSessions);
	}

	@Test
	void multipleHazelcastInstanceConfiguration() {
		assertThatExceptionOfType(BeanCreationException.class)
				.isThrownBy(() -> registerAndRefresh(MultipleHazelcastInstanceConfiguration.class))
				.withMessageContaining("expected single matching bean but found 2");
	}

	@Test
	void customIndexResolverConfiguration() {
		registerAndRefresh(CustomIndexResolverConfiguration.class);
		HazelcastIndexedSessionRepository repository = this.context.getBean(HazelcastIndexedSessionRepository.class);
		@SuppressWarnings("unchecked")
		IndexResolver<Session> indexResolver = this.context.getBean(IndexResolver.class);
		assertThat(repository).isNotNull();
		assertThat(indexResolver).isNotNull();
		assertThat(repository).hasFieldOrPropertyWithValue("indexResolver", indexResolver);
	}

	@Test
	void sessionRepositoryCustomizer() {
		registerAndRefresh(SessionRepositoryCustomizerConfiguration.class);
		HazelcastIndexedSessionRepository sessionRepository = this.context
				.getBean(HazelcastIndexedSessionRepository.class);
		assertThat(sessionRepository).hasFieldOrPropertyWithValue("defaultMaxInactiveInterval",
				MAX_INACTIVE_INTERVAL_IN_SECONDS);
	}

	private void registerAndRefresh(Class<?>... annotatedClasses) {
		this.context.register(annotatedClasses);
		this.context.refresh();
	}

	@Configuration
	@EnableHazelcastHttpSession
	static class NoHazelcastInstanceConfiguration {

	}

	static class BaseConfiguration {

		@SuppressWarnings("unchecked")
		static IMap<Object, Object> defaultHazelcastInstanceSessions = mock(IMap.class);

		@Bean
		HazelcastInstance defaultHazelcastInstance() {
			HazelcastInstance hazelcastInstance = mock(HazelcastInstance.class);
			given(hazelcastInstance.getMap(anyString())).willReturn(defaultHazelcastInstanceSessions);
			return hazelcastInstance;
		}

	}

	@Configuration
	@EnableHazelcastHttpSession
	static class DefaultConfiguration extends BaseConfiguration {

	}

	@Configuration
	@EnableHazelcastHttpSession(sessionMapName = MAP_NAME)
	static class CustomSessionMapNameConfiguration extends BaseConfiguration {

	}

	@Configuration
	static class CustomSessionMapNameSetConfiguration extends HazelcastHttpSessionConfiguration {

		CustomSessionMapNameSetConfiguration() {
			setSessionMapName(MAP_NAME);
		}

	}

	@Configuration
	static class CustomMaxInactiveIntervalInSecondsSetConfiguration extends HazelcastHttpSessionConfiguration {

		CustomMaxInactiveIntervalInSecondsSetConfiguration() {
			setMaxInactiveIntervalInSeconds(MAX_INACTIVE_INTERVAL_IN_SECONDS);
		}

	}

	@Configuration
	@EnableHazelcastHttpSession(maxInactiveIntervalInSeconds = MAX_INACTIVE_INTERVAL_IN_SECONDS)
	static class CustomMaxInactiveIntervalInSecondsConfiguration extends BaseConfiguration {

	}

	@Configuration
	static class CustomFlushImmediatelySetConfiguration extends HazelcastHttpSessionConfiguration {

		CustomFlushImmediatelySetConfiguration() {
			setFlushMode(FlushMode.IMMEDIATE);
		}

	}

	@Configuration
	@SuppressWarnings("deprecation")
	static class CustomFlushImmediatelySetLegacyConfiguration extends HazelcastHttpSessionConfiguration {

		CustomFlushImmediatelySetLegacyConfiguration() {
			setHazelcastFlushMode(HazelcastFlushMode.IMMEDIATE);
		}

	}

	@EnableHazelcastHttpSession(saveMode = SaveMode.ALWAYS)
	static class CustomSaveModeExpressionAnnotationConfiguration {

	}

	@Configuration
	static class CustomSaveModeExpressionSetterConfiguration extends HazelcastHttpSessionConfiguration {

		CustomSaveModeExpressionSetterConfiguration() {
			setSaveMode(SaveMode.ALWAYS);
		}

	}

	@Configuration
	@EnableHazelcastHttpSession(flushMode = FlushMode.IMMEDIATE)
	static class CustomFlushImmediatelyConfiguration extends BaseConfiguration {

	}

	@Configuration
	@EnableHazelcastHttpSession(hazelcastFlushMode = HazelcastFlushMode.IMMEDIATE)
	@SuppressWarnings("deprecation")
	static class CustomFlushImmediatelyLegacyConfiguration extends BaseConfiguration {

	}

	@Configuration
	@EnableHazelcastHttpSession
	static class QualifiedHazelcastInstanceConfiguration extends BaseConfiguration {

		@SuppressWarnings("unchecked")
		static IMap<Object, Object> qualifiedHazelcastInstanceSessions = mock(IMap.class);

		@Bean
		@SpringSessionHazelcastInstance
		HazelcastInstance qualifiedHazelcastInstance() {
			HazelcastInstance hazelcastInstance = mock(HazelcastInstance.class);
			given(hazelcastInstance.getMap(anyString())).willReturn(qualifiedHazelcastInstanceSessions);
			return hazelcastInstance;
		}

	}

	@Configuration
	@EnableHazelcastHttpSession
	static class PrimaryHazelcastInstanceConfiguration extends BaseConfiguration {

		@SuppressWarnings("unchecked")
		static IMap<Object, Object> primaryHazelcastInstanceSessions = mock(IMap.class);

		@Bean
		@Primary
		HazelcastInstance primaryHazelcastInstance() {
			HazelcastInstance hazelcastInstance = mock(HazelcastInstance.class);
			given(hazelcastInstance.getMap(anyString())).willReturn(primaryHazelcastInstanceSessions);
			return hazelcastInstance;
		}

	}

	@Configuration
	@EnableHazelcastHttpSession
	static class QualifiedAndPrimaryHazelcastInstanceConfiguration extends BaseConfiguration {

		@SuppressWarnings("unchecked")
		static IMap<Object, Object> qualifiedHazelcastInstanceSessions = mock(IMap.class);

		@SuppressWarnings("unchecked")
		static IMap<Object, Object> primaryHazelcastInstanceSessions = mock(IMap.class);

		@Bean
		@SpringSessionHazelcastInstance
		HazelcastInstance qualifiedHazelcastInstance() {
			HazelcastInstance hazelcastInstance = mock(HazelcastInstance.class);
			given(hazelcastInstance.getMap(anyString())).willReturn(qualifiedHazelcastInstanceSessions);
			return hazelcastInstance;
		}

		@Bean
		@Primary
		HazelcastInstance primaryHazelcastInstance() {
			HazelcastInstance hazelcastInstance = mock(HazelcastInstance.class);
			given(hazelcastInstance.getMap(anyString())).willReturn(primaryHazelcastInstanceSessions);
			return hazelcastInstance;
		}

	}

	@Configuration
	@EnableHazelcastHttpSession
	static class NamedHazelcastInstanceConfiguration extends BaseConfiguration {

		@SuppressWarnings("unchecked")
		static IMap<Object, Object> hazelcastInstanceSessions = mock(IMap.class);

		@Bean
		HazelcastInstance hazelcastInstance() {
			HazelcastInstance hazelcastInstance = mock(HazelcastInstance.class);
			given(hazelcastInstance.getMap(anyString())).willReturn(hazelcastInstanceSessions);
			return hazelcastInstance;
		}

	}

	@Configuration
	@EnableHazelcastHttpSession
	static class MultipleHazelcastInstanceConfiguration extends BaseConfiguration {

		@SuppressWarnings("unchecked")
		static IMap<Object, Object> secondaryHazelcastInstanceSessions = mock(IMap.class);

		@Bean
		HazelcastInstance secondaryHazelcastInstance() {
			HazelcastInstance hazelcastInstance = mock(HazelcastInstance.class);
			given(hazelcastInstance.getMap(anyString())).willReturn(secondaryHazelcastInstanceSessions);
			return hazelcastInstance;
		}

	}

	@EnableHazelcastHttpSession
	static class CustomIndexResolverConfiguration extends BaseConfiguration {

		@Bean
		@SuppressWarnings("unchecked")
		IndexResolver<Session> indexResolver() {
			return mock(IndexResolver.class);
		}

	}

	@EnableHazelcastHttpSession
	static class SessionRepositoryCustomizerConfiguration extends BaseConfiguration {

		@Bean
		@Order(0)
		SessionRepositoryCustomizer<HazelcastIndexedSessionRepository> sessionRepositoryCustomizerOne() {
			return (sessionRepository) -> sessionRepository.setDefaultMaxInactiveInterval(0);
		}

		@Bean
		@Order(1)
		SessionRepositoryCustomizer<HazelcastIndexedSessionRepository> sessionRepositoryCustomizerTwo() {
			return (sessionRepository) -> sessionRepository
					.setDefaultMaxInactiveInterval(MAX_INACTIVE_INTERVAL_IN_SECONDS);
		}

	}

}
