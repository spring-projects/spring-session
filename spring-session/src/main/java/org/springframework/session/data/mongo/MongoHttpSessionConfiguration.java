/*
 * Copyright 2002-2016 the original author or authors.
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

import com.mongodb.DBObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportAware;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.session.config.annotation.web.http.SpringHttpSessionConfiguration;

/**
 * Configuration class registering {@code MongoSessionRepository} bean
 * To import this configuration use {@link @EnableMongoHttpSession} annotation
 *
 * @author Jakub Kubrynski
 */
@Configuration
@EnableScheduling
class MongoHttpSessionConfiguration extends SpringHttpSessionConfiguration implements ImportAware {

	@Autowired(required = false)
	private Converter<MongoExpiringSession, DBObject> mongoSessionSerializer = new StandardMongoSessionToDBObjectConverter();

	@Autowired(required = false)
	private Converter<DBObject, MongoExpiringSession> mongoSessionDeserializer = new StandardDBObjectToMongoSessionConverter();

	private Integer maxInactiveIntervalInSeconds;
	private String collectionName;

	@Bean
	MongoOperationsSessionRepository mongoSessionRepository(MongoOperations mongoOperations,
	                                                        ApplicationEventPublisher eventPublisher) {
		return new MongoOperationsSessionRepository(mongoOperations, eventPublisher,
				mongoSessionSerializer, mongoSessionDeserializer,
				maxInactiveIntervalInSeconds, collectionName);
	}

	public void setImportMetadata(AnnotationMetadata importMetadata) {
		AnnotationAttributes attributes = AnnotationAttributes.fromMap(
				importMetadata.getAnnotationAttributes(EnableMongoHttpSession.class.getName()));
		maxInactiveIntervalInSeconds = attributes.getNumber("maxInactiveIntervalInSeconds");
		collectionName = attributes.getString("collectionName");
	}
}
