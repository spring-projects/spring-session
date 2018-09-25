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

import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.DatabasePopulator;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

/**
 * Factories for various {@link DatabasePopulator}s.
 *
 * @author Vedran Pavic
 */
final class DatabasePopulators {

	private DatabasePopulators() {
	}

	static ResourceDatabasePopulator mySql() {
		return new ResourceDatabasePopulator(new ClassPathResource(
				"org/springframework/session/jdbc/schema-mysql.sql"));
	}

	static ResourceDatabasePopulator oracle() {
		return new ResourceDatabasePopulator(new ClassPathResource(
				"org/springframework/session/jdbc/schema-oracle.sql"));
	}

	static ResourceDatabasePopulator postgreSql() {
		return new ResourceDatabasePopulator(new ClassPathResource(
				"org/springframework/session/jdbc/schema-postgresql.sql"));
	}

	static ResourceDatabasePopulator sqlServer() {
		return new ResourceDatabasePopulator(new ClassPathResource(
				"org/springframework/session/jdbc/schema-sqlserver.sql"));
	}

}
