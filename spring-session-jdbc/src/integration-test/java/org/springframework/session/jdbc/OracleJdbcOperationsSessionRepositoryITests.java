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

import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;
import org.testcontainers.containers.OracleContainer;
import org.testcontainers.utility.TestcontainersConfiguration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.util.ClassUtils;

/**
 * Integration tests for {@link JdbcOperationsSessionRepository} using Oracle database.
 * <p>
 * This test is conditional on presence of Oracle JDBC driver on the classpath and
 * Testcontainers property {@code oracle.container.image} being set.
 *
 * @author Vedran Pavic
 */
@RunWith(SpringRunner.class)
@WebAppConfiguration
@ContextConfiguration
public class OracleJdbcOperationsSessionRepositoryITests
		extends AbstractContainerJdbcOperationsSessionRepositoryITests {

	@BeforeClass
	public static void setUpClass() {
		Assume.assumeTrue("Oracle JDBC driver is present on the classpath",
				ClassUtils.isPresent("oracle.jdbc.OracleDriver", null));
		Assume.assumeTrue("Testcontainers property `oracle.container.image` is set",
				TestcontainersConfiguration.getInstance().getProperties()
						.getProperty("oracle.container.image") != null);
	}

	@Configuration
	static class Config extends BaseContainerConfig {

		@Bean
		public OracleContainer databaseContainer() {
			OracleContainer databaseContainer = DatabaseContainers.oracle();
			databaseContainer.start();
			return databaseContainer;
		}

		@Bean
		public ResourceDatabasePopulator databasePopulator() {
			return DatabasePopulators.oracle();
		}

	}

}
