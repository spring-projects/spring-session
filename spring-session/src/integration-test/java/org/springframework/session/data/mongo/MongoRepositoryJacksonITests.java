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

import java.util.Collections;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.Module;
import org.junit.Test;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.geo.GeoModule;
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
public class MongoRepositoryJacksonITests extends AbstractMongoRepositoryITests {

	@Test
	public void findByCustomIndex() throws Exception {
		MongoExpiringSession toSave = this.repository.createSession();
		String cartId = "cart-" + UUID.randomUUID();
		toSave.setAttribute("cartId", cartId);

		this.repository.save(toSave);

		Map<String, MongoExpiringSession> findByCartId = this.repository
				.findByIndexNameAndIndexValue("cartId", cartId);

		assertThat(findByCartId).hasSize(1);
		assertThat(findByCartId.keySet()).containsOnly(toSave.getId());
	}

	@Configuration
	@EnableMongoHttpSession
	static class Config extends BaseConfig {

		@Bean
		public AbstractMongoSessionConverter mongoSessionConverter() {
			return new JacksonMongoSessionConverter(Collections.<Module>singletonList(new GeoModule()));
		}

	}
}
