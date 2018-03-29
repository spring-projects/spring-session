/*
 * Copyright 2014-2018 the original author or authors.
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

import java.util.concurrent.ConcurrentHashMap;

import javax.servlet.ServletContext;

import org.junit.After;
import org.junit.Test;

import org.springframework.beans.factory.UnsatisfiedDependencyException;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockServletContext;
import org.springframework.session.MapSessionRepository;
import org.springframework.session.SessionRepository;
import org.springframework.session.security.web.authentication.SpringSessionRememberMeServices;
import org.springframework.session.web.http.CookieHttpSessionIdResolver;
import org.springframework.session.web.http.DefaultCookieSerializer;
import org.springframework.session.web.http.SessionEventHttpSessionListenerAdapter;
import org.springframework.session.web.http.SessionRepositoryFilter;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link SpringHttpSessionConfiguration}.
 *
 * @author Vedran Pavic
 */
public class SpringHttpSessionConfigurationTests {

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
		assertThatThrownBy(() -> registerAndRefresh(EmptyConfiguration.class))
				.isInstanceOf(UnsatisfiedDependencyException.class)
				.hasMessageContaining("org.springframework.session.SessionRepository");
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
	public void sessionCookieConfigConfiguration() {
		registerAndRefresh(SessionCookieConfigConfiguration.class);

		SessionRepositoryFilter sessionRepositoryFilter = this.context
				.getBean(SessionRepositoryFilter.class);
		assertThat(sessionRepositoryFilter).isNotNull();
		CookieHttpSessionIdResolver httpSessionIdResolver = (CookieHttpSessionIdResolver) ReflectionTestUtils
				.getField(sessionRepositoryFilter, "httpSessionIdResolver");
		assertThat(httpSessionIdResolver).isNotNull();
		DefaultCookieSerializer cookieSerializer = (DefaultCookieSerializer) ReflectionTestUtils
				.getField(httpSessionIdResolver, "cookieSerializer");
		assertThat(cookieSerializer).isNotNull();
		assertThat(ReflectionTestUtils.getField(cookieSerializer, "cookieName"))
				.isEqualTo("test-name");
		assertThat(ReflectionTestUtils.getField(cookieSerializer, "cookiePath"))
				.isEqualTo("test-path");
		assertThat(ReflectionTestUtils.getField(cookieSerializer, "cookieMaxAge"))
				.isEqualTo(600);
		assertThat(ReflectionTestUtils.getField(cookieSerializer, "domainName"))
				.isEqualTo("test-domain");
	}

	@Test
	public void rememberMeServicesConfiguration() {
		registerAndRefresh(RememberMeServicesConfiguration.class);

		SessionRepositoryFilter sessionRepositoryFilter = this.context
				.getBean(SessionRepositoryFilter.class);
		assertThat(sessionRepositoryFilter).isNotNull();
		CookieHttpSessionIdResolver httpSessionIdResolver = (CookieHttpSessionIdResolver) ReflectionTestUtils
				.getField(sessionRepositoryFilter, "httpSessionIdResolver");
		assertThat(httpSessionIdResolver).isNotNull();
		DefaultCookieSerializer cookieSerializer = (DefaultCookieSerializer) ReflectionTestUtils
				.getField(httpSessionIdResolver, "cookieSerializer");
		assertThat(cookieSerializer).isNotNull();
		assertThat(ReflectionTestUtils.getField(cookieSerializer,
				"rememberMeRequestAttribute")).isEqualTo(
						SpringSessionRememberMeServices.REMEMBER_ME_LOGIN_ATTR);
	}

	@Configuration
	@EnableSpringHttpSession
	static class EmptyConfiguration {
	}

	static class BaseConfiguration {

		@Bean
		public MapSessionRepository sessionRepository() {
			return new MapSessionRepository(new ConcurrentHashMap<>());
		}

	}

	@Configuration
	@EnableSpringHttpSession
	static class DefaultConfiguration extends BaseConfiguration {
	}

	@Configuration
	@EnableSpringHttpSession
	static class SessionCookieConfigConfiguration extends BaseConfiguration {

		@Bean
		public ServletContext servletContext() {
			MockServletContext servletContext = new MockServletContext();
			servletContext.getSessionCookieConfig().setName("test-name");
			servletContext.getSessionCookieConfig().setDomain("test-domain");
			servletContext.getSessionCookieConfig().setPath("test-path");
			servletContext.getSessionCookieConfig().setMaxAge(600);
			return servletContext;
		}

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
