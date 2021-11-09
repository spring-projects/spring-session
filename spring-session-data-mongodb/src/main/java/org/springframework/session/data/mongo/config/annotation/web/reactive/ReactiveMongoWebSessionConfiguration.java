/*
 * Copyright 2017 the original author or authors.
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

package org.springframework.session.data.mongo.config.annotation.web.reactive;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.EmbeddedValueResolverAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportAware;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.serializer.support.DeserializingConverter;
import org.springframework.core.serializer.support.SerializingConverter;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.ReactiveMongoOperations;
import org.springframework.session.IndexResolver;
import org.springframework.session.config.ReactiveSessionRepositoryCustomizer;
import org.springframework.session.config.annotation.web.server.SpringWebSessionConfiguration;
import org.springframework.session.data.mongo.AbstractMongoSessionConverter;
import org.springframework.session.data.mongo.JdkMongoSessionConverter;
import org.springframework.session.data.mongo.MongoSession;
import org.springframework.session.data.mongo.ReactiveMongoSessionRepository;
import org.springframework.util.StringUtils;
import org.springframework.util.StringValueResolver;

/**
 * Configure a {@link ReactiveMongoSessionRepository} using a provided
 * {@link ReactiveMongoOperations}.
 *
 * @author Greg Turnquist
 * @author Vedran Pavić
 */
@Configuration(proxyBeanMethods = false)
public class ReactiveMongoWebSessionConfiguration extends SpringWebSessionConfiguration
		implements BeanClassLoaderAware, EmbeddedValueResolverAware, ImportAware {

	private AbstractMongoSessionConverter mongoSessionConverter;

	private Integer maxInactiveIntervalInSeconds;

	private String collectionName;

	private StringValueResolver embeddedValueResolver;

	private List<ReactiveSessionRepositoryCustomizer<ReactiveMongoSessionRepository>> sessionRepositoryCustomizers;

	@Autowired(required = false)
	private MongoOperations mongoOperations;

	private ClassLoader classLoader;

	private IndexResolver<MongoSession> indexResolver;

	@Bean
	public ReactiveMongoSessionRepository reactiveMongoSessionRepository(ReactiveMongoOperations operations) {

		ReactiveMongoSessionRepository repository = new ReactiveMongoSessionRepository(operations);

		if (this.mongoSessionConverter != null) {
			repository.setMongoSessionConverter(this.mongoSessionConverter);

			if (this.indexResolver != null) {
				this.mongoSessionConverter.setIndexResolver(this.indexResolver);
			}

		}
		else {
			JdkMongoSessionConverter mongoSessionConverter = new JdkMongoSessionConverter(new SerializingConverter(),
					new DeserializingConverter(this.classLoader),
					Duration.ofSeconds(ReactiveMongoSessionRepository.DEFAULT_INACTIVE_INTERVAL));

			if (this.indexResolver != null) {
				mongoSessionConverter.setIndexResolver(this.indexResolver);
			}

			repository.setMongoSessionConverter(mongoSessionConverter);
		}

		if (this.maxInactiveIntervalInSeconds != null) {
			repository.setMaxInactiveIntervalInSeconds(this.maxInactiveIntervalInSeconds);
		}

		if (this.collectionName != null) {
			repository.setCollectionName(this.collectionName);
		}

		if (this.mongoOperations != null) {
			repository.setBlockingMongoOperations(this.mongoOperations);
		}

		this.sessionRepositoryCustomizers
				.forEach((sessionRepositoryCustomizer) -> sessionRepositoryCustomizer.customize(repository));

		return repository;
	}

	@Autowired(required = false)
	public void setMongoSessionConverter(AbstractMongoSessionConverter mongoSessionConverter) {
		this.mongoSessionConverter = mongoSessionConverter;
	}

	@Override
	public void setImportMetadata(AnnotationMetadata importMetadata) {

		AnnotationAttributes attributes = AnnotationAttributes
				.fromMap(importMetadata.getAnnotationAttributes(EnableMongoWebSession.class.getName()));

		if (attributes != null) {
			this.maxInactiveIntervalInSeconds = attributes.getNumber("maxInactiveIntervalInSeconds");
		}
		else {
			this.maxInactiveIntervalInSeconds = ReactiveMongoSessionRepository.DEFAULT_INACTIVE_INTERVAL;
		}

		String collectionNameValue = (attributes != null) ? attributes.getString("collectionName") : "";
		if (StringUtils.hasText(collectionNameValue)) {
			this.collectionName = this.embeddedValueResolver.resolveStringValue(collectionNameValue);
		}

	}

	@Override
	public void setBeanClassLoader(ClassLoader classLoader) {
		this.classLoader = classLoader;
	}

	@Override
	public void setEmbeddedValueResolver(StringValueResolver embeddedValueResolver) {
		this.embeddedValueResolver = embeddedValueResolver;
	}

	public Integer getMaxInactiveIntervalInSeconds() {
		return this.maxInactiveIntervalInSeconds;
	}

	public void setMaxInactiveIntervalInSeconds(Integer maxInactiveIntervalInSeconds) {
		this.maxInactiveIntervalInSeconds = maxInactiveIntervalInSeconds;
	}

	public String getCollectionName() {
		return this.collectionName;
	}

	public void setCollectionName(String collectionName) {
		this.collectionName = collectionName;
	}

	@Autowired(required = false)
	public void setSessionRepositoryCustomizers(
			ObjectProvider<ReactiveSessionRepositoryCustomizer<ReactiveMongoSessionRepository>> sessionRepositoryCustomizers) {
		this.sessionRepositoryCustomizers = sessionRepositoryCustomizers.orderedStream().collect(Collectors.toList());
	}

	@Autowired(required = false)
	public void setIndexResolver(IndexResolver<MongoSession> indexResolver) {
		this.indexResolver = indexResolver;
	}

}
