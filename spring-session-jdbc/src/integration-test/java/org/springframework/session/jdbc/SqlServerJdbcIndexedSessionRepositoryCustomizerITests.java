/*
 * Copyright 2014-2025 the original author or authors.
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

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;

/**
 * Integration tests for {@link JdbcIndexedSessionRepository} using SQL Server database
 * with {@link SqlServerJdbcIndexedSessionRepositoryCustomizer}.
 *
 * @author Vedran Pavic
 */
@SpringJUnitWebConfig
class SqlServerJdbcIndexedSessionRepositoryCustomizerITests extends SqlServerJdbcIndexedSessionRepositoryITests {

	@Configuration
	static class CustomizerConfig extends Config {

		@Bean
		SqlServerJdbcIndexedSessionRepositoryCustomizer sqlServerJdbcIndexedSessionRepositoryCustomizer() {
			return new SqlServerJdbcIndexedSessionRepositoryCustomizer();
		}

	}

}
