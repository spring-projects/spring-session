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

package org.springframework.session.endpoint;

import javax.sql.DataSource;

import org.junit.After;
import org.junit.Test;

import org.springframework.boot.actuate.autoconfigure.EndpointWebMvcAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.ManagementServerProperties;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.EmbeddedDataSourceConfiguration;
import org.springframework.boot.test.EnvironmentTestUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.jdbc.JdbcOperationsSessionRepository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link SessionEndpoint}.
 *
 * @author Eddú Meléndez
 */
public class SessionEndpointTests {

	private AnnotationConfigWebApplicationContext context = new AnnotationConfigWebApplicationContext();

	@After
	public void close() {
		if (this.context != null) {
			this.context.close();
		}
	}

	@Test
	public void testCustomProperties() {
		this.context.register(EmbeddedDataSourceConfiguration.class,
				DataSourceTransactionManagerAutoConfiguration.class,
				ManagementServerProperties.class, SessionAutoConfiguration.class,
				EndpointConfiguration.class, EndpointWebMvcAutoConfiguration.class);
		EnvironmentTestUtils.addEnvironment(this.context, "endpoints.session.path:/mysession",
				"endpoints.session.sensitive:true");
		this.context.refresh();
		SessionEndpoint sessionEndpoint = this.context.getBean(SessionEndpoint.class);
		assertThat(sessionEndpoint.getPath()).isEqualTo("/mysession");
		assertThat(sessionEndpoint.isSensitive()).isTrue();
		assertThat(sessionEndpoint.isEnabled()).isTrue();
	}

	@Test
	public void testEndpointDisabled() {
		this.context.register(EmbeddedDataSourceConfiguration.class,
				DataSourceTransactionManagerAutoConfiguration.class,
				ManagementServerProperties.class, SessionAutoConfiguration.class,
				EndpointConfiguration.class, EndpointWebMvcAutoConfiguration.class);
		EnvironmentTestUtils.addEnvironment(this.context, "endpoints.session.enabled:false");
		this.context.refresh();
		SessionEndpoint sessionEndpoint = this.context.getBean(SessionEndpoint.class);
		assertThat(sessionEndpoint.isEnabled()).isFalse();
	}

	@Test
	public void testSessionRepositoryIsPresent() {
		this.context.register(EmbeddedDataSourceConfiguration.class,
				DataSourceTransactionManagerAutoConfiguration.class,
				ManagementServerProperties.class, SessionAutoConfiguration.class,
				EndpointConfiguration.class, EndpointWebMvcAutoConfiguration.class);
		this.context.refresh();
		FindByIndexNameSessionRepository sessionRepository = this.context
				.getBean(FindByIndexNameSessionRepository.class);
		assertThat(sessionRepository).isNotNull();
	}

	@Test
	public void testSessionRepositoryIsNotPresent() {
		this.context.register(EmbeddedDataSourceConfiguration.class,
				DataSourceTransactionManagerAutoConfiguration.class,
				ManagementServerProperties.class, EndpointConfiguration.class,
				EndpointWebMvcAutoConfiguration.class);
		this.context.refresh();
		String[] sessionRepository = this.context
				.getBeanNamesForType(FindByIndexNameSessionRepository.class);
		assertThat(sessionRepository.length).isEqualTo(0);
	}

	@Configuration
	static class SessionAutoConfiguration {

		@Bean
		public FindByIndexNameSessionRepository sessionRepository(DataSource dataSource,
				PlatformTransactionManager transactionManager) {
			return new JdbcOperationsSessionRepository(dataSource, transactionManager);
		}
	}

}
