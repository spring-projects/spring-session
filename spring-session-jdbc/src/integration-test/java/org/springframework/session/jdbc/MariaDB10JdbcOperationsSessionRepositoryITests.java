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

package org.springframework.session.jdbc;

import java.sql.SQLException;

import javax.sql.DataSource;

import org.junit.ClassRule;
import org.junit.runner.RunWith;
import org.mariadb.jdbc.MariaDbDataSource;
import org.testcontainers.containers.MariaDBContainer;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;
import org.springframework.jdbc.datasource.init.DataSourceInitializer;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.web.WebAppConfiguration;

/**
 * Integration tests for {@link JdbcOperationsSessionRepository} using MariaDB 10.x
 * database.
 *
 * @author Vedran Pavic
 */
@RunWith(SpringRunner.class)
@WebAppConfiguration
@ContextConfiguration
public class MariaDB10JdbcOperationsSessionRepositoryITests
		extends AbstractJdbcOperationsSessionRepositoryITests {

	private static final String DOCKER_IMAGE = "mariadb:10.2.10";

	@ClassRule
	public static MariaDBContainer mariaDBContainer = new MariaDBContainer(DOCKER_IMAGE);

	@Configuration
	static class Config extends BaseConfig {

		@Bean
		public DataSource dataSource() throws SQLException {
			MariaDbDataSource dataSource = new MariaDbDataSource(
					mariaDBContainer.getJdbcUrl());
			dataSource.setUserName(mariaDBContainer.getUsername());
			dataSource.setPassword(mariaDBContainer.getPassword());
			return dataSource;
		}

		@Bean
		public DataSourceInitializer initializer(DataSource dataSource,
				ResourceLoader resourceLoader) {
			DataSourceInitializer initializer = new DataSourceInitializer();
			initializer.setDataSource(dataSource);
			initializer.setDatabasePopulator(
					new ResourceDatabasePopulator(resourceLoader.getResource(
							"classpath:org/springframework/session/jdbc/schema-mysql.sql")));
			return initializer;
		}

	}

}
