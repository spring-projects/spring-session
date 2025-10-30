/*
 * Copyright 2014-present the original author or authors.
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

package org.springframework.session.jdbc;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testcontainers.containers.JdbcDatabaseContainer;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;

/**
 * Integration tests for {@link JdbcIndexedSessionRepository} using IBM DB2 database.
 *
 * @author Vedran Pavic
 */
@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration
@Disabled
class Db2JdbcIndexedSessionRepositoryITests extends AbstractContainerJdbcIndexedSessionRepositoryITests {

	@Configuration
	static class Config extends BaseContainerConfig {

		@Bean
		JdbcDatabaseContainer<?> databaseContainer() {
			JdbcDatabaseContainer<?> databaseContainer = DatabaseContainers.db2();
			databaseContainer.start();
			return databaseContainer;
		}

		@Bean
		ResourceDatabasePopulator databasePopulator() {
			return DatabasePopulators.db2();
		}

	}

}
