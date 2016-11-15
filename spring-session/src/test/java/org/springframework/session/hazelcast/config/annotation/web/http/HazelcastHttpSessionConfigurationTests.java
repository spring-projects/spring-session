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

package org.springframework.session.hazelcast.config.annotation.web.http;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IMap;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import org.springframework.beans.factory.UnsatisfiedDependencyException;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.hazelcast.HazelcastFlushMode;
import org.springframework.session.hazelcast.HazelcastSessionRepository;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Matchers.isA;

/**
 * Tests for {@link HazelcastHttpSessionConfiguration}.
 *
 * @author Vedran Pavic
 * @author Aleksandar Stojsavljevic
 */
@RunWith(MockitoJUnitRunner.class)
public class HazelcastHttpSessionConfigurationTests {

	private static final String MAP_NAME = "spring:test:sessions";

	private static final int MAX_INACTIVE_INTERVAL_IN_SECONDS = 600;

	private static final HazelcastFlushMode HAZELCAST_FLUSH_MODE = HazelcastFlushMode.IMMEDIATE;

	@Rule
	public final ExpectedException thrown = ExpectedException.none();

	@Mock
	private static HazelcastInstance hazelcastInstance;

	@Mock
	private IMap<Object, Object> sessions;

	private AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();

	@Before
	public void setUp() {
		given(hazelcastInstance.getMap(isA(String.class))).willReturn(this.sessions);
	}

	@After
	public void closeContext() {
		if (this.context != null) {
			this.context.close();
		}
	}

	@Test
	public void noHazelcastInstanceConfiguration() {
		this.thrown.expect(UnsatisfiedDependencyException.class);
		this.thrown.expectMessage("HazelcastInstance");

		registerAndRefresh(EmptyConfiguration.class);
	}

	@Test
	public void defaultConfiguration() {
		registerAndRefresh(DefaultConfiguration.class);

		assertThat(this.context.getBean(HazelcastSessionRepository.class))
				.isNotNull();
	}

	@Test
	public void customTableName() {
		registerAndRefresh(CustomSessionMapNameConfiguration.class);

		HazelcastSessionRepository repository = this.context
				.getBean(HazelcastSessionRepository.class);
		HazelcastHttpSessionConfiguration configuration = this.context
				.getBean(HazelcastHttpSessionConfiguration.class);
		assertThat(repository).isNotNull();
		assertThat(ReflectionTestUtils.getField(configuration, "sessionMapName"))
				.isEqualTo(MAP_NAME);
	}

	@Test
	public void setCustomSessionMapName() {
		registerAndRefresh(BaseConfiguration.class,
				CustomSessionMapNameSetConfiguration.class);

		HazelcastSessionRepository repository = this.context
				.getBean(HazelcastSessionRepository.class);
		HazelcastHttpSessionConfiguration configuration = this.context
				.getBean(HazelcastHttpSessionConfiguration.class);
		assertThat(repository).isNotNull();
		assertThat(ReflectionTestUtils.getField(configuration, "sessionMapName"))
				.isEqualTo(MAP_NAME);
	}

	@Test
	public void setCustomMaxInactiveIntervalInSeconds() {
		registerAndRefresh(BaseConfiguration.class,
				CustomMaxInactiveIntervalInSecondsSetConfiguration.class);

		HazelcastSessionRepository repository = this.context
				.getBean(HazelcastSessionRepository.class);
		assertThat(repository).isNotNull();
		assertThat(ReflectionTestUtils.getField(repository, "defaultMaxInactiveInterval")).isEqualTo(
				MAX_INACTIVE_INTERVAL_IN_SECONDS);
	}

	@Test
	public void customMaxInactiveIntervalInSeconds() {
		registerAndRefresh(CustomMaxInactiveIntervalInSecondsConfiguration.class);

		HazelcastSessionRepository repository = this.context
				.getBean(HazelcastSessionRepository.class);
		assertThat(repository).isNotNull();
		assertThat(ReflectionTestUtils.getField(repository, "defaultMaxInactiveInterval"))
				.isEqualTo(MAX_INACTIVE_INTERVAL_IN_SECONDS);
	}

	@Test
	public void customFlushImmediately() {
		registerAndRefresh(CustomFlushImmediatelyConfiguration.class);

		HazelcastSessionRepository repository = this.context
				.getBean(HazelcastSessionRepository.class);
		assertThat(repository).isNotNull();
		assertThat(ReflectionTestUtils.getField(repository, "hazelcastFlushMode"))
				.isEqualTo(HazelcastFlushMode.IMMEDIATE);
	}

	@Test
	public void setCustomFlushImmediately() {
		registerAndRefresh(BaseConfiguration.class,
				CustomFlushImmediatelySetConfiguration.class);

		HazelcastSessionRepository repository = this.context
				.getBean(HazelcastSessionRepository.class);
		assertThat(repository).isNotNull();
		assertThat(ReflectionTestUtils.getField(repository, "hazelcastFlushMode"))
				.isEqualTo(HazelcastFlushMode.IMMEDIATE);
	}

	private void registerAndRefresh(Class<?>... annotatedClasses) {
		this.context.register(annotatedClasses);
		this.context.refresh();
	}

	@Configuration
	@EnableHazelcastHttpSession
	static class EmptyConfiguration {
	}

	static class BaseConfiguration {

		@Bean
		public HazelcastInstance hazelcastInstance() {
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
	static class CustomSessionMapNameSetConfiguration
			extends HazelcastHttpSessionConfiguration {

		CustomSessionMapNameSetConfiguration() {
			setSessionMapName(MAP_NAME);
		}

	}

	@Configuration
	static class CustomMaxInactiveIntervalInSecondsSetConfiguration
			extends HazelcastHttpSessionConfiguration {

		CustomMaxInactiveIntervalInSecondsSetConfiguration() {
			setMaxInactiveIntervalInSeconds(MAX_INACTIVE_INTERVAL_IN_SECONDS);
		}

	}

	@Configuration
	@EnableHazelcastHttpSession(maxInactiveIntervalInSeconds = MAX_INACTIVE_INTERVAL_IN_SECONDS)
	static class CustomMaxInactiveIntervalInSecondsConfiguration
			extends BaseConfiguration {
	}

	@Configuration
	static class CustomFlushImmediatelySetConfiguration
			extends HazelcastHttpSessionConfiguration {

		CustomFlushImmediatelySetConfiguration() {
			setHazelcastFlushMode(HAZELCAST_FLUSH_MODE);
		}

	}

	@Configuration
	@EnableHazelcastHttpSession(hazelcastFlushMode = HazelcastFlushMode.IMMEDIATE)
	static class CustomFlushImmediatelyConfiguration
			extends BaseConfiguration {
	}

}
