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

package org.springframework.session.data.redis.config.annotation.web.server;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.EmbeddedValueResolverAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.ImportAware;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.serializer.JdkSerializationRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.session.MapSession;
import org.springframework.session.SaveMode;
import org.springframework.session.SessionIdGenerator;
import org.springframework.session.UuidSessionIdGenerator;
import org.springframework.session.config.ReactiveSessionRepositoryCustomizer;
import org.springframework.session.config.annotation.web.server.SpringWebSessionConfiguration;
import org.springframework.session.data.redis.ReactiveRedisSessionRepository;
import org.springframework.session.data.redis.config.annotation.SpringSessionRedisConnectionFactory;
import org.springframework.util.StringUtils;
import org.springframework.util.StringValueResolver;
import org.springframework.web.server.session.WebSessionManager;

/**
 * Exposes the {@link WebSessionManager} as a bean named {@code webSessionManager}. In
 * order to use this a single {@link ReactiveRedisConnectionFactory} must be exposed as a
 * Bean.
 *
 * @author Vedran Pavic
 * @author Yanming Zhou
 * @since 2.0.0
 * @see EnableRedisWebSession
 */
@Configuration(proxyBeanMethods = false)
@Import(SpringWebSessionConfiguration.class)
public class RedisWebSessionConfiguration implements BeanClassLoaderAware, EmbeddedValueResolverAware, ImportAware {

	private Duration maxInactiveInterval = MapSession.DEFAULT_MAX_INACTIVE_INTERVAL;

	private String redisNamespace = ReactiveRedisSessionRepository.DEFAULT_NAMESPACE;

	private SaveMode saveMode = SaveMode.ON_SET_ATTRIBUTE;

	private ReactiveRedisConnectionFactory redisConnectionFactory;

	private RedisSerializer<Object> defaultRedisSerializer;

	private List<ReactiveSessionRepositoryCustomizer<ReactiveRedisSessionRepository>> sessionRepositoryCustomizers;

	private ClassLoader classLoader;

	private StringValueResolver embeddedValueResolver;

	private SessionIdGenerator sessionIdGenerator = UuidSessionIdGenerator.getInstance();

	@Bean
	public ReactiveRedisSessionRepository sessionRepository() {
		ReactiveRedisTemplate<String, Object> reactiveRedisTemplate = createReactiveRedisTemplate();
		ReactiveRedisSessionRepository sessionRepository = new ReactiveRedisSessionRepository(reactiveRedisTemplate);
		sessionRepository.setDefaultMaxInactiveInterval(this.maxInactiveInterval);
		if (StringUtils.hasText(this.redisNamespace)) {
			sessionRepository.setRedisKeyNamespace(this.redisNamespace);
		}
		sessionRepository.setSaveMode(this.saveMode);
		sessionRepository.setSessionIdGenerator(this.sessionIdGenerator);
		this.sessionRepositoryCustomizers
			.forEach((sessionRepositoryCustomizer) -> sessionRepositoryCustomizer.customize(sessionRepository));
		return sessionRepository;
	}

	public void setMaxInactiveInterval(Duration maxInactiveInterval) {
		this.maxInactiveInterval = maxInactiveInterval;
	}

	@Deprecated
	public void setMaxInactiveIntervalInSeconds(int maxInactiveIntervalInSeconds) {
		setMaxInactiveInterval(Duration.ofSeconds(maxInactiveIntervalInSeconds));
	}

	public void setRedisNamespace(String namespace) {
		this.redisNamespace = namespace;
	}

	public void setSaveMode(SaveMode saveMode) {
		this.saveMode = saveMode;
	}

	@Autowired
	public void setRedisConnectionFactory(
			@SpringSessionRedisConnectionFactory ObjectProvider<ReactiveRedisConnectionFactory> springSessionRedisConnectionFactory,
			ObjectProvider<ReactiveRedisConnectionFactory> redisConnectionFactory) {
		ReactiveRedisConnectionFactory redisConnectionFactoryToUse = springSessionRedisConnectionFactory
			.getIfAvailable();
		if (redisConnectionFactoryToUse == null) {
			redisConnectionFactoryToUse = redisConnectionFactory.getObject();
		}
		this.redisConnectionFactory = redisConnectionFactoryToUse;
	}

	@Autowired(required = false)
	@Qualifier("springSessionDefaultRedisSerializer")
	public void setDefaultRedisSerializer(RedisSerializer<Object> defaultRedisSerializer) {
		this.defaultRedisSerializer = defaultRedisSerializer;
	}

	@Autowired(required = false)
	public void setSessionRepositoryCustomizer(
			ObjectProvider<ReactiveSessionRepositoryCustomizer<ReactiveRedisSessionRepository>> sessionRepositoryCustomizers) {
		this.sessionRepositoryCustomizers = sessionRepositoryCustomizers.orderedStream().collect(Collectors.toList());
	}

	@Override
	public void setBeanClassLoader(ClassLoader classLoader) {
		this.classLoader = classLoader;
	}

	@Override
	public void setEmbeddedValueResolver(StringValueResolver resolver) {
		this.embeddedValueResolver = resolver;
	}

	@Override
	public void setImportMetadata(AnnotationMetadata importMetadata) {
		Map<String, Object> attributeMap = importMetadata
			.getAnnotationAttributes(EnableRedisWebSession.class.getName());
		AnnotationAttributes attributes = AnnotationAttributes.fromMap(attributeMap);
		if (attributes == null) {
			return;
		}
		this.maxInactiveInterval = Duration.ofSeconds(attributes.<Integer>getNumber("maxInactiveIntervalInSeconds"));
		String redisNamespaceValue = attributes.getString("redisNamespace");
		if (StringUtils.hasText(redisNamespaceValue)) {
			this.redisNamespace = this.embeddedValueResolver.resolveStringValue(redisNamespaceValue);
		}
		this.saveMode = attributes.getEnum("saveMode");
	}

	private ReactiveRedisTemplate<String, Object> createReactiveRedisTemplate() {
		RedisSerializer<String> keySerializer = RedisSerializer.string();
		RedisSerializer<Object> defaultSerializer = (this.defaultRedisSerializer != null) ? this.defaultRedisSerializer
				: new JdkSerializationRedisSerializer(this.classLoader);
		RedisSerializationContext<String, Object> serializationContext = RedisSerializationContext
			.<String, Object>newSerializationContext(defaultSerializer)
			.key(keySerializer)
			.hashKey(keySerializer)
			.build();
		return new ReactiveRedisTemplate<>(this.redisConnectionFactory, serializationContext);
	}

	@Autowired(required = false)
	public void setSessionIdGenerator(SessionIdGenerator sessionIdGenerator) {
		this.sessionIdGenerator = sessionIdGenerator;
	}

}
