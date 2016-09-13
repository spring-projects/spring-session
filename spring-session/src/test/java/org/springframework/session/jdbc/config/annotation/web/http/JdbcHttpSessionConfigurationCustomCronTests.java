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

package org.springframework.session.jdbc.config.annotation.web.http;

import javax.sql.DataSource;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.transaction.PlatformTransactionManager;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * @author Rob Winch
 *
 */
public class JdbcHttpSessionConfigurationCustomCronTests {

	AnnotationConfigApplicationContext context;

	@Before
	public void setup() {
		this.context = new AnnotationConfigApplicationContext();
	}

	@After
	public void closeContext() {
		if (this.context != null) {
			this.context.close();
		}
	}

	@Test
	public void overrideCron() {
		this.context.register(Config.class);

		assertThatThrownBy(new ThrowingCallable() {
			public void call() throws Throwable {
				JdbcHttpSessionConfigurationCustomCronTests.this.context.refresh();
			}
		}).hasStackTraceContaining(
				"Encountered invalid @Scheduled method 'cleanUpExpiredSessions': Cron expression must consist of 6 fields (found 1 in \"oops\")");
	}

	@EnableJdbcHttpSession
	@Configuration
	@PropertySource("classpath:spring-session-cleanup-cron-expression-oops.properties")
	static class Config {
		@Bean
		public DataSource dataSource() {
			return mock(DataSource.class);
		}

		@Bean
		public PlatformTransactionManager transactionManager() {
			return mock(PlatformTransactionManager.class);
		}
	}
}
