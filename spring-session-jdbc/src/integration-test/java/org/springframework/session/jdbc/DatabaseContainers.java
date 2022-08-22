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

package org.springframework.session.jdbc;

import org.testcontainers.containers.Db2ContainerProvider;
import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.containers.MSSQLServerContainerProvider;
import org.testcontainers.containers.MariaDBContainerProvider;
import org.testcontainers.containers.MySQLContainerProvider;
import org.testcontainers.containers.OracleContainerProvider;
import org.testcontainers.containers.PostgreSQLContainerProvider;

/**
 * Factories for various {@link JdbcDatabaseContainer}s.
 *
 * @author Vedran Pavic
 */
final class DatabaseContainers {

	private DatabaseContainers() {
	}

	static JdbcDatabaseContainer<?> db2() {
		return new Db2ContainerProvider().newInstance("11.5.7.0a");
	}

	static JdbcDatabaseContainer<?> mariaDb() {
		return new MariaDBContainerProvider().newInstance("10.8.3");
	}

	static JdbcDatabaseContainer<?> mySql() {
		return new MySQLContainerProvider().newInstance("8.0.30");
	}

	static JdbcDatabaseContainer<?> oracle() {
		return new OracleContainerProvider().newInstance("21.3.0-slim");
	}

	static JdbcDatabaseContainer<?> postgreSql() {
		return new PostgreSQLContainerProvider().newInstance("14.5-alpine");
	}

	static JdbcDatabaseContainer<?> sqlServer() {
		return new MSSQLServerContainerProvider().newInstance("2019-CU17-ubuntu-20.04");
	}

}
