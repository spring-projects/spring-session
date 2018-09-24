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

package org.springframework.session.jdbc;

import org.junit.runner.RunWith;
import org.testcontainers.containers.MySQLContainer;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.web.WebAppConfiguration;

/**
 * Integration tests for {@link JdbcOperationsSessionRepository} using MySQL 5.x database.
 *
 * @author Vedran Pavic
 */
@RunWith(SpringRunner.class)
@WebAppConfiguration
@ContextConfiguration
public class MySql5JdbcOperationsSessionRepositoryITests
		extends AbstractContainerJdbcOperationsSessionRepositoryITests {

	@Configuration
	static class Config extends BaseContainerConfig {

		@Bean
		public MySQLContainer databaseContainer() {
			MySQLContainer databaseContainer = DatabaseContainers.mySql5();
			databaseContainer.start();
			return databaseContainer;
		}

		@Bean
		public ResourceDatabasePopulator databasePopulator() {
			return DatabasePopulators.mySql();
		}

	}

}
