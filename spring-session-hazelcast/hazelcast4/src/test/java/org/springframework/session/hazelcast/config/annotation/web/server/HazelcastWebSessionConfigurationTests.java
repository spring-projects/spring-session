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

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.BDDMockito;
import org.mockito.Mockito;

import org.springframework.beans.factory.BeanCreationException;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.annotation.Order;
import org.springframework.session.SaveMode;
import org.springframework.session.config.ReactiveSessionRepositoryCustomizer;
import org.springframework.session.hazelcast.ReactiveHazelcastSessionRepository;
import org.springframework.session.hazelcast.config.annotation.SpringSessionHazelcastInstance;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Tests for {@link HazelcastWebSessionConfiguration}.
 *
 * @author Vedran Pavic
 * @author Aleksandar Stojsavljevic
 * @author Didier Loiseau
 */
class HazelcastWebSessionConfigurationTests {

	private static final String MAP_NAME = "spring:test:sessions";

	private static final int MAX_INACTIVE_INTERVAL_IN_SECONDS = 600;

	private final AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();

	@AfterEach
	void closeContext() {
		this.context.close();
	}

	@Test
	void noHazelcastInstanceConfiguration() {
		Assertions.assertThatExceptionOfType(BeanCreationException.class)
				.isThrownBy(() -> registerAndRefresh(NoHazelcastInstanceConfiguration.class))
				.withMessageContaining("HazelcastInstance");
	}

	@Test
	void defaultConfiguration() {
		registerAndRefresh(DefaultConfiguration.class);

		Assertions.assertThat(this.context.getBean(ReactiveHazelcastSessionRepository.class)).isNotNull();
	}

	@Test
	void customTableName() {
		registerAndRefresh(CustomSessionMapNameConfiguration.class);

		ReactiveHazelcastSessionRepository repository = this.context.getBean(ReactiveHazelcastSessionRepository.class);
		HazelcastWebSessionConfiguration configuration = this.context.getBean(HazelcastWebSessionConfiguration.class);
		Assertions.assertThat(repository).isNotNull();
		Assertions.assertThat(ReflectionTestUtils.getField(configuration, "sessionMapName")).isEqualTo(MAP_NAME);
	}

	@Test
	void setCustomSessionMapName() {
		registerAndRefresh(BaseConfiguration.class, CustomSessionMapNameSetConfiguration.class);

		ReactiveHazelcastSessionRepository repository = this.context.getBean(ReactiveHazelcastSessionRepository.class);
		HazelcastWebSessionConfiguration configuration = this.context.getBean(HazelcastWebSessionConfiguration.class);
		Assertions.assertThat(repository).isNotNull();
		Assertions.assertThat(ReflectionTestUtils.getField(configuration, "sessionMapName")).isEqualTo(MAP_NAME);
	}

	@Test
	void setCustomMaxInactiveIntervalInSeconds() {
		registerAndRefresh(BaseConfiguration.class, CustomMaxInactiveIntervalInSecondsSetConfiguration.class);

		ReactiveHazelcastSessionRepository repository = this.context.getBean(ReactiveHazelcastSessionRepository.class);
		Assertions.assertThat(repository).isNotNull();
		Assertions.assertThat(ReflectionTestUtils.getField(repository, "defaultMaxInactiveInterval"))
				.isEqualTo(MAX_INACTIVE_INTERVAL_IN_SECONDS);
	}

	@Test
	void customMaxInactiveIntervalInSeconds() {
		registerAndRefresh(CustomMaxInactiveIntervalInSecondsConfiguration.class);

		ReactiveHazelcastSessionRepository repository = this.context.getBean(ReactiveHazelcastSessionRepository.class);
		Assertions.assertThat(repository).isNotNull();
		Assertions.assertThat(ReflectionTestUtils.getField(repository, "defaultMaxInactiveInterval"))
				.isEqualTo(MAX_INACTIVE_INTERVAL_IN_SECONDS);
	}

	@Test
	void customSaveModeAnnotation() {
		registerAndRefresh(BaseConfiguration.class, CustomSaveModeExpressionAnnotationConfiguration.class);
		Assertions.assertThat(this.context.getBean(ReactiveHazelcastSessionRepository.class))
				.hasFieldOrPropertyWithValue("saveMode", SaveMode.ALWAYS);
	}

	@Test
	void customSaveModeSetter() {
		registerAndRefresh(BaseConfiguration.class, CustomSaveModeExpressionSetterConfiguration.class);
		Assertions.assertThat(this.context.getBean(ReactiveHazelcastSessionRepository.class))
				.hasFieldOrPropertyWithValue("saveMode", SaveMode.ALWAYS);
	}

	@Test
	void qualifiedHazelcastInstanceConfiguration() {
		registerAndRefresh(QualifiedHazelcastInstanceConfiguration.class);

		ReactiveHazelcastSessionRepository repository = this.context.getBean(ReactiveHazelcastSessionRepository.class);
		HazelcastInstance hazelcastInstance = this.context.getBean("qualifiedHazelcastInstance",
				HazelcastInstance.class);
		Assertions.assertThat(repository).isNotNull();
		Assertions.assertThat(hazelcastInstance).isNotNull();
		Assertions.assertThat(ReflectionTestUtils.getField(repository, "sessions"))
				.isEqualTo(QualifiedHazelcastInstanceConfiguration.qualifiedHazelcastInstanceSessions);
	}

	@Test
	void primaryHazelcastInstanceConfiguration() {
		registerAndRefresh(PrimaryHazelcastInstanceConfiguration.class);

		ReactiveHazelcastSessionRepository repository = this.context.getBean(ReactiveHazelcastSessionRepository.class);
		HazelcastInstance hazelcastInstance = this.context.getBean("primaryHazelcastInstance", HazelcastInstance.class);
		Assertions.assertThat(repository).isNotNull();
		Assertions.assertThat(hazelcastInstance).isNotNull();
		Assertions.assertThat(ReflectionTestUtils.getField(repository, "sessions"))
				.isEqualTo(PrimaryHazelcastInstanceConfiguration.primaryHazelcastInstanceSessions);
	}

	@Test
	void qualifiedAndPrimaryHazelcastInstanceConfiguration() {
		registerAndRefresh(QualifiedAndPrimaryHazelcastInstanceConfiguration.class);

		ReactiveHazelcastSessionRepository repository = this.context.getBean(ReactiveHazelcastSessionRepository.class);
		HazelcastInstance hazelcastInstance = this.context.getBean("qualifiedHazelcastInstance",
				HazelcastInstance.class);
		Assertions.assertThat(repository).isNotNull();
		Assertions.assertThat(hazelcastInstance).isNotNull();
		Assertions.assertThat(ReflectionTestUtils.getField(repository, "sessions"))
				.isEqualTo(QualifiedAndPrimaryHazelcastInstanceConfiguration.qualifiedHazelcastInstanceSessions);
	}

	@Test
	void namedHazelcastInstanceConfiguration() {
		registerAndRefresh(NamedHazelcastInstanceConfiguration.class);

		ReactiveHazelcastSessionRepository repository = this.context.getBean(ReactiveHazelcastSessionRepository.class);
		HazelcastInstance hazelcastInstance = this.context.getBean("hazelcastInstance", HazelcastInstance.class);
		Assertions.assertThat(repository).isNotNull();
		Assertions.assertThat(hazelcastInstance).isNotNull();
		Assertions.assertThat(ReflectionTestUtils.getField(repository, "sessions"))
				.isEqualTo(NamedHazelcastInstanceConfiguration.hazelcastInstanceSessions);
	}

	@Test
	void multipleHazelcastInstanceConfiguration() {
		Assertions.assertThatExceptionOfType(BeanCreationException.class)
				.isThrownBy(() -> registerAndRefresh(MultipleHazelcastInstanceConfiguration.class))
				.withMessageContaining("expected single matching bean but found 2");
	}

	@Test
	void sessionRepositoryCustomizer() {
		registerAndRefresh(SessionRepositoryCustomizerConfiguration.class);
		ReactiveHazelcastSessionRepository sessionRepository = this.context
				.getBean(ReactiveHazelcastSessionRepository.class);
		Assertions.assertThat(sessionRepository).hasFieldOrPropertyWithValue("defaultMaxInactiveInterval",
				MAX_INACTIVE_INTERVAL_IN_SECONDS);
	}

	private void registerAndRefresh(Class<?>... annotatedClasses) {
		this.context.register(annotatedClasses);
		this.context.refresh();
	}

	@Configuration
	@EnableHazelcastWebSession
	static class NoHazelcastInstanceConfiguration {

	}

	static class BaseConfiguration {

		@SuppressWarnings("unchecked")
		static IMap<Object, Object> defaultHazelcastInstanceSessions = Mockito.mock(IMap.class);

		@Bean
		HazelcastInstance defaultHazelcastInstance() {
			HazelcastInstance hazelcastInstance = Mockito.mock(HazelcastInstance.class);
			BDDMockito.given(hazelcastInstance.getMap(ArgumentMatchers.anyString()))
					.willReturn(defaultHazelcastInstanceSessions);
			return hazelcastInstance;
		}

	}

	@Configuration
	@EnableHazelcastWebSession
	static class DefaultConfiguration extends BaseConfiguration {

	}

	@Configuration
	@EnableHazelcastWebSession(sessionMapName = MAP_NAME)
	static class CustomSessionMapNameConfiguration extends BaseConfiguration {

	}

	@Configuration
	static class CustomSessionMapNameSetConfiguration extends HazelcastWebSessionConfiguration {

		CustomSessionMapNameSetConfiguration() {
			setSessionMapName(MAP_NAME);
		}

	}

	@Configuration
	static class CustomMaxInactiveIntervalInSecondsSetConfiguration extends HazelcastWebSessionConfiguration {

		CustomMaxInactiveIntervalInSecondsSetConfiguration() {
			setMaxInactiveIntervalInSeconds(MAX_INACTIVE_INTERVAL_IN_SECONDS);
		}

	}

	@Configuration
	@EnableHazelcastWebSession(maxInactiveIntervalInSeconds = MAX_INACTIVE_INTERVAL_IN_SECONDS)
	static class CustomMaxInactiveIntervalInSecondsConfiguration extends BaseConfiguration {

	}

	@EnableHazelcastWebSession(saveMode = SaveMode.ALWAYS)
	static class CustomSaveModeExpressionAnnotationConfiguration {

	}

	@Configuration
	static class CustomSaveModeExpressionSetterConfiguration extends HazelcastWebSessionConfiguration {

		CustomSaveModeExpressionSetterConfiguration() {
			setSaveMode(SaveMode.ALWAYS);
		}

	}

	@Configuration
	@EnableHazelcastWebSession
	static class QualifiedHazelcastInstanceConfiguration extends BaseConfiguration {

		@SuppressWarnings("unchecked")
		static IMap<Object, Object> qualifiedHazelcastInstanceSessions = Mockito.mock(IMap.class);

		@Bean
		@SpringSessionHazelcastInstance
		HazelcastInstance qualifiedHazelcastInstance() {
			HazelcastInstance hazelcastInstance = Mockito.mock(HazelcastInstance.class);
			BDDMockito.given(hazelcastInstance.getMap(ArgumentMatchers.anyString()))
					.willReturn(qualifiedHazelcastInstanceSessions);
			return hazelcastInstance;
		}

	}

	@Configuration
	@EnableHazelcastWebSession
	static class PrimaryHazelcastInstanceConfiguration extends BaseConfiguration {

		@SuppressWarnings("unchecked")
		static IMap<Object, Object> primaryHazelcastInstanceSessions = Mockito.mock(IMap.class);

		@Bean
		@Primary
		HazelcastInstance primaryHazelcastInstance() {
			HazelcastInstance hazelcastInstance = Mockito.mock(HazelcastInstance.class);
			BDDMockito.given(hazelcastInstance.getMap(ArgumentMatchers.anyString()))
					.willReturn(primaryHazelcastInstanceSessions);
			return hazelcastInstance;
		}

	}

	@Configuration
	@EnableHazelcastWebSession
	static class QualifiedAndPrimaryHazelcastInstanceConfiguration extends BaseConfiguration {

		@SuppressWarnings("unchecked")
		static IMap<Object, Object> qualifiedHazelcastInstanceSessions = Mockito.mock(IMap.class);

		@SuppressWarnings("unchecked")
		static IMap<Object, Object> primaryHazelcastInstanceSessions = Mockito.mock(IMap.class);

		@Bean
		@SpringSessionHazelcastInstance
		HazelcastInstance qualifiedHazelcastInstance() {
			HazelcastInstance hazelcastInstance = Mockito.mock(HazelcastInstance.class);
			BDDMockito.given(hazelcastInstance.getMap(ArgumentMatchers.anyString()))
					.willReturn(qualifiedHazelcastInstanceSessions);
			return hazelcastInstance;
		}

		@Bean
		@Primary
		HazelcastInstance primaryHazelcastInstance() {
			HazelcastInstance hazelcastInstance = Mockito.mock(HazelcastInstance.class);
			BDDMockito.given(hazelcastInstance.getMap(ArgumentMatchers.anyString()))
					.willReturn(primaryHazelcastInstanceSessions);
			return hazelcastInstance;
		}

	}

	@Configuration
	@EnableHazelcastWebSession
	static class NamedHazelcastInstanceConfiguration extends BaseConfiguration {

		@SuppressWarnings("unchecked")
		static IMap<Object, Object> hazelcastInstanceSessions = Mockito.mock(IMap.class);

		@Bean
		HazelcastInstance hazelcastInstance() {
			HazelcastInstance hazelcastInstance = Mockito.mock(HazelcastInstance.class);
			BDDMockito.given(hazelcastInstance.getMap(ArgumentMatchers.anyString()))
					.willReturn(hazelcastInstanceSessions);
			return hazelcastInstance;
		}

	}

	@Configuration
	@EnableHazelcastWebSession
	static class MultipleHazelcastInstanceConfiguration extends BaseConfiguration {

		@SuppressWarnings("unchecked")
		static IMap<Object, Object> secondaryHazelcastInstanceSessions = Mockito.mock(IMap.class);

		@Bean
		HazelcastInstance secondaryHazelcastInstance() {
			HazelcastInstance hazelcastInstance = Mockito.mock(HazelcastInstance.class);
			BDDMockito.given(hazelcastInstance.getMap(ArgumentMatchers.anyString()))
					.willReturn(secondaryHazelcastInstanceSessions);
			return hazelcastInstance;
		}

	}

	@EnableHazelcastWebSession
	static class SessionRepositoryCustomizerConfiguration extends BaseConfiguration {

		@Bean
		@Order(0)
		ReactiveSessionRepositoryCustomizer<ReactiveHazelcastSessionRepository> sessionRepositoryCustomizerOne() {
			return (sessionRepository) -> sessionRepository.setDefaultMaxInactiveInterval(0);
		}

		@Bean
		@Order(1)
		ReactiveSessionRepositoryCustomizer<ReactiveHazelcastSessionRepository> sessionRepositoryCustomizerTwo() {
			return (sessionRepository) -> sessionRepository
					.setDefaultMaxInactiveInterval(MAX_INACTIVE_INTERVAL_IN_SECONDS);
		}

	}

}
