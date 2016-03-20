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
package org.springframework.session.data.mongo;

import java.util.Map;

import org.junit.Test;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.data.mongo.config.annotation.web.http.EnableMongoHttpSession;
import org.springframework.test.context.ContextConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link MongoOperationsSessionRepository} that use
 * {@link JacksonMongoSessionConverter} based session serialization.
 *
 * @author Jakub Kubrynski
 * @author Vedran Pavic
 */
@ContextConfiguration
public class MongoRepositoryJdkSerializationITests extends AbstractMongoRepositoryITests {

	@Test
	public void findByDeletedSecurityPrincipalNameReload() throws Exception {
		MongoExpiringSession toSave = this.repository.createSession();
		toSave.setAttribute(SPRING_SECURITY_CONTEXT, this.context);

		this.repository.save(toSave);

		MongoExpiringSession getSession = this.repository.getSession(toSave.getId());
		getSession.setAttribute(INDEX_NAME, null);
		this.repository.save(getSession);

		Map<String, MongoExpiringSession> findByPrincipalName = this.repository
				.findByIndexNameAndIndexValue(INDEX_NAME, getChangedSecurityName());

		assertThat(findByPrincipalName).isEmpty();
	}

	@Test
	public void findByPrincipalNameNoSecurityPrincipalNameChangeReload()
			throws Exception {
		MongoExpiringSession toSave = this.repository.createSession();
		toSave.setAttribute(SPRING_SECURITY_CONTEXT, this.context);

		this.repository.save(toSave);

		toSave = this.repository.getSession(toSave.getId());

		toSave.setAttribute("other", "value");
		this.repository.save(toSave);

		Map<String, MongoExpiringSession> findByPrincipalName = this.repository
				.findByIndexNameAndIndexValue(INDEX_NAME, getSecurityName());

		assertThat(findByPrincipalName).hasSize(1);
		assertThat(findByPrincipalName.keySet()).containsOnly(toSave.getId());
	}

	@Configuration
	@EnableMongoHttpSession
	static class Config extends BaseConfig {

		@Bean
		public AbstractMongoSessionConverter mongoSessionConverter() {
			return new JdkMongoSessionConverter();
		}

	}
}
