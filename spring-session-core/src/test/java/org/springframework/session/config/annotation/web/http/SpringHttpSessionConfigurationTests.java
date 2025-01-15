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

package org.springframework.session.config.annotation.web.http;

import java.util.concurrent.ConcurrentHashMap;

import jakarta.servlet.ServletContext;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

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
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;

/**
 * Tests for {@link SpringHttpSessionConfiguration}.
 *
 * @author Vedran Pavic
 */
class SpringHttpSessionConfigurationTests {

	private AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();

	@AfterEach
	void closeContext() {
		if (this.context != null) {
			this.context.close();
		}
	}

	private void registerAndRefresh(Class<?>... annotatedClasses) {
		this.context.register(annotatedClasses);
		this.context.refresh();
	}

	@Test
	void noSessionRepositoryConfiguration() {
		assertThatExceptionOfType(UnsatisfiedDependencyException.class)
			.isThrownBy(() -> registerAndRefresh(EmptyConfiguration.class))
			.withMessageContaining("org.springframework.session.SessionRepository");
	}

	@Test
	void defaultConfiguration() {
		registerAndRefresh(DefaultConfiguration.class);

		assertThat(this.context.getBean(SessionEventHttpSessionListenerAdapter.class)).isNotNull();
		assertThat(this.context.getBean(SessionRepositoryFilter.class)).isNotNull();
		assertThat(this.context.getBean(SessionRepository.class)).isNotNull();
	}

	@Test
	void sessionCookieConfigConfiguration() {
		registerAndRefresh(SessionCookieConfigConfiguration.class);

		SessionRepositoryFilter sessionRepositoryFilter = this.context.getBean(SessionRepositoryFilter.class);
		assertThat(sessionRepositoryFilter).isNotNull();
		CookieHttpSessionIdResolver httpSessionIdResolver = (CookieHttpSessionIdResolver) ReflectionTestUtils
			.getField(sessionRepositoryFilter, "httpSessionIdResolver");
		assertThat(httpSessionIdResolver).isNotNull();
		DefaultCookieSerializer cookieSerializer = (DefaultCookieSerializer) ReflectionTestUtils
			.getField(httpSessionIdResolver, "cookieSerializer");
		assertThat(cookieSerializer).isNotNull();
		assertThat(ReflectionTestUtils.getField(cookieSerializer, "cookieName")).isEqualTo("test-name");
		assertThat(ReflectionTestUtils.getField(cookieSerializer, "cookiePath")).isEqualTo("test-path");
		assertThat(ReflectionTestUtils.getField(cookieSerializer, "cookieMaxAge")).isEqualTo(600);
		assertThat(ReflectionTestUtils.getField(cookieSerializer, "domainName")).isEqualTo("test-domain");
	}

	@Test
	void rememberMeServicesConfiguration() {
		registerAndRefresh(RememberMeServicesConfiguration.class);

		SessionRepositoryFilter sessionRepositoryFilter = this.context.getBean(SessionRepositoryFilter.class);
		assertThat(sessionRepositoryFilter).isNotNull();
		CookieHttpSessionIdResolver httpSessionIdResolver = (CookieHttpSessionIdResolver) ReflectionTestUtils
			.getField(sessionRepositoryFilter, "httpSessionIdResolver");
		assertThat(httpSessionIdResolver).isNotNull();
		DefaultCookieSerializer cookieSerializer = (DefaultCookieSerializer) ReflectionTestUtils
			.getField(httpSessionIdResolver, "cookieSerializer");
		assertThat(cookieSerializer).isNotNull();
		assertThat(ReflectionTestUtils.getField(cookieSerializer, "rememberMeRequestAttribute"))
			.isEqualTo(SpringSessionRememberMeServices.REMEMBER_ME_LOGIN_ATTR);
	}

	@Test
	void rememberMeServicesAndCustomDefaultCookieSerializerThenWarnIfRememberMeRequestAttributeNotSet() {
		try (MockedStatic<LogFactory> logFactoryMockedStatic = mockStatic(LogFactory.class)) {
			Log logMock = mock();
			logFactoryMockedStatic.when(() -> LogFactory.getLog(any(Class.class))).thenReturn(logMock);
			registerAndRefresh(RememberMeServicesConfiguration.class, CustomDefaultCookieSerializerConfiguration.class);
			verify(logMock).warn("Spring Session Remember Me support is enabled "
					+ "and the DefaultCookieSerializer is provided explicitly. "
					+ "The DefaultCookieSerializer must be configured with "
					+ "setRememberMeRequestAttribute(String) in order to support Remember Me.");
		}
	}

	@Configuration
	@EnableSpringHttpSession
	static class EmptyConfiguration {

	}

	static class BaseConfiguration {

		@Bean
		MapSessionRepository sessionRepository() {
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
		ServletContext servletContext() {
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
		SpringSessionRememberMeServices rememberMeServices() {
			return new SpringSessionRememberMeServices();
		}

	}

	@Configuration
	@EnableSpringHttpSession
	static class CustomDefaultCookieSerializerConfiguration {

		@Bean
		DefaultCookieSerializer defaultCookieSerializer() {
			return new DefaultCookieSerializer();
		}

	}

}
