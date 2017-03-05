/*
 * Copyright 2014-2017 the original author or authors.
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

package org.springframework.session.config.annotation.web.http;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import org.springframework.beans.factory.UnsatisfiedDependencyException;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.MapSessionRepository;
import org.springframework.session.SessionRepository;
import org.springframework.session.security.web.authentication.SpringSessionRememberMeServices;
import org.springframework.session.web.http.CookieHttpSessionStrategy;
import org.springframework.session.web.http.DefaultCookieSerializer;
import org.springframework.session.web.http.SessionEventHttpSessionListenerAdapter;
import org.springframework.session.web.http.SessionRepositoryFilter;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link SpringHttpSessionConfiguration}.
 *
 * @author Vedran Pavic
 */
@RunWith(MockitoJUnitRunner.class)
public class SpringHttpSessionConfigurationTests {

	@Rule
	public final ExpectedException thrown = ExpectedException.none();

	private AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();

	@After
	public void closeContext() {
		if (this.context != null) {
			this.context.close();
		}
	}

	private void registerAndRefresh(Class<?>... annotatedClasses) {
		this.context.register(annotatedClasses);
		this.context.refresh();
	}

	@Test
	public void noSessionRepositoryConfiguration() {
		this.thrown.expect(UnsatisfiedDependencyException.class);
		this.thrown.expectMessage("org.springframework.session.SessionRepository");

		registerAndRefresh(EmptyConfiguration.class);
	}

	@Test
	public void defaultConfiguration() {
		registerAndRefresh(DefaultConfiguration.class);

		assertThat(this.context.getBean(SessionEventHttpSessionListenerAdapter.class))
				.isNotNull();
		assertThat(this.context.getBean(SessionRepositoryFilter.class)).isNotNull();
		assertThat(this.context.getBean(SessionRepository.class)).isNotNull();
	}

	@Test
	public void rememberMeServicesConfiguration() {
		registerAndRefresh(RememberMeServicesConfiguration.class);

		SessionRepositoryFilter sessionRepositoryFilter = this.context.getBean(
				SessionRepositoryFilter.class);
		assertThat(sessionRepositoryFilter).isNotNull();
		CookieHttpSessionStrategy httpSessionStrategy =
				(CookieHttpSessionStrategy) ReflectionTestUtils.getField(
						sessionRepositoryFilter, "httpSessionStrategy");
		assertThat(httpSessionStrategy).isNotNull();
		DefaultCookieSerializer cookieSerializer =
				(DefaultCookieSerializer) ReflectionTestUtils.getField(
						httpSessionStrategy, "cookieSerializer");
		assertThat(cookieSerializer).isNotNull();
		assertThat(ReflectionTestUtils.getField(
				cookieSerializer, "rememberMeRequestAttribute"))
				.isEqualTo(SpringSessionRememberMeServices.REMEMBER_ME_LOGIN_ATTR);
	}

	@Configuration
	@EnableSpringHttpSession
	static class EmptyConfiguration {
	}

	static class BaseConfiguration {

		@Bean
		public MapSessionRepository sessionRepository() {
			return new MapSessionRepository();
		}

	}

	@Configuration
	@EnableSpringHttpSession
	static class DefaultConfiguration extends BaseConfiguration {
	}


	@Configuration
	@EnableSpringHttpSession
	static class RememberMeServicesConfiguration extends BaseConfiguration {

		@Bean
		public SpringSessionRememberMeServices rememberMeServices() {
			return new SpringSessionRememberMeServices();
		}

	}

}
