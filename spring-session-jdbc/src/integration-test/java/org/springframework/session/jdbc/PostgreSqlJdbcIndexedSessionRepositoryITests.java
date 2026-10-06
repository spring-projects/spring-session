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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testcontainers.containers.JdbcDatabaseContainer;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.jdbc.JdbcIndexedSessionRepository.JdbcSession;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link JdbcIndexedSessionRepository} using PostgreSQL database.
 *
 * @author Vedran Pavic
 */
@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration
class PostgreSqlJdbcIndexedSessionRepositoryITests extends AbstractContainerJdbcIndexedSessionRepositoryITests {

	@Autowired
	private JdbcIndexedSessionRepository repository;

	@Autowired
	private DataSource dataSource;

	@Test // gh-3452
	void findByPrincipalNameWhenAttributesAddedLaterThenReturnsCompleteSessions() {
		String principalName = "principal-" + UUID.randomUUID();
		String largeValue = "x".repeat(1200);
		List<String> ids = new ArrayList<>();
		for (int i = 0; i < 120; i++) {
			JdbcSession session = this.repository.createSession();
			session.setAttribute(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME, principalName);
			session.setAttribute("first", largeValue + i);
			session.setAttribute("second", largeValue + i);
			session.setAttribute("third", largeValue + i);
			this.repository.save(session);
			ids.add(session.getId());
		}
		for (int i = 0; i < ids.size(); i += 3) {
			JdbcSession session = this.repository.findById(ids.get(i));
			session.setAttribute("later", "value");
			this.repository.save(session);
		}
		new JdbcTemplate(this.dataSource).execute("ANALYZE SPRING_SESSION, SPRING_SESSION_ATTRIBUTES");

		Map<String, JdbcSession> sessions = this.repository.findByPrincipalName(principalName);

		assertThat(sessions).containsOnlyKeys(ids);
		for (String id : ids) {
			JdbcSession expected = this.repository.findById(id);
			assertThat(expected.getAttributeNames()).contains("first", "second", "third");
			JdbcSession actual = sessions.get(id);
			assertThat(actual.getAttributeNames()).isEqualTo(expected.getAttributeNames());
			for (String name : expected.getAttributeNames()) {
				assertThat(actual.<String>getAttribute(name)).isEqualTo(expected.getAttribute(name));
			}
		}
	}

	@Configuration
	static class Config extends BaseContainerConfig {

		@Bean
		JdbcDatabaseContainer<?> databaseContainer() {
			JdbcDatabaseContainer<?> databaseContainer = DatabaseContainers.postgreSql();
			databaseContainer.start();
			return databaseContainer;
		}

		@Bean
		ResourceDatabasePopulator databasePopulator() {
			return DatabasePopulators.postgreSql();
		}

	}

}
