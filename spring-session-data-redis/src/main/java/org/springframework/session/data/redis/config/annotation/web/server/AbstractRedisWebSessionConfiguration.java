/*
 * Copyright 2014-2023 the original author or authors.
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
import java.util.stream.Collectors;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.serializer.JdkSerializationRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.session.MapSession;
import org.springframework.session.ReactiveSessionRepository;
import org.springframework.session.SaveMode;
import org.springframework.session.Session;
import org.springframework.session.SessionIdGenerator;
import org.springframework.session.UuidSessionIdGenerator;
import org.springframework.session.config.ReactiveSessionRepositoryCustomizer;
import org.springframework.session.config.annotation.web.server.SpringWebSessionConfiguration;
import org.springframework.session.data.redis.ReactiveRedisSessionRepository;
import org.springframework.session.data.redis.config.annotation.SpringSessionRedisConnectionFactory;
import org.springframework.util.Assert;

@Configuration(proxyBeanMethods = false)
@Import(SpringWebSessionConfiguration.class)
public abstract class AbstractRedisWebSessionConfiguration<T extends ReactiveSessionRepository<? extends Session>> {

	private Duration maxInactiveInterval = MapSession.DEFAULT_MAX_INACTIVE_INTERVAL;

	private String redisNamespace = ReactiveRedisSessionRepository.DEFAULT_NAMESPACE;

	private SaveMode saveMode = SaveMode.ON_SET_ATTRIBUTE;

	private ReactiveRedisConnectionFactory redisConnectionFactory;

	private RedisSerializer<Object> defaultRedisSerializer = new JdkSerializationRedisSerializer();

	private List<ReactiveSessionRepositoryCustomizer<T>> sessionRepositoryCustomizers;

	private SessionIdGenerator sessionIdGenerator = UuidSessionIdGenerator.getInstance();

	public abstract T sessionRepository();

	public void setMaxInactiveInterval(Duration maxInactiveInterval) {
		this.maxInactiveInterval = maxInactiveInterval;
	}

	public void setRedisNamespace(String namespace) {
		Assert.hasText(namespace, "namespace cannot be empty or null");
		this.redisNamespace = namespace;
	}

	public void setSaveMode(SaveMode saveMode) {
		Assert.notNull(saveMode, "saveMode cannot be null");
		this.saveMode = saveMode;
	}

	public Duration getMaxInactiveInterval() {
		return this.maxInactiveInterval;
	}

	public String getRedisNamespace() {
		return this.redisNamespace;
	}

	public SaveMode getSaveMode() {
		return this.saveMode;
	}

	public SessionIdGenerator getSessionIdGenerator() {
		return this.sessionIdGenerator;
	}

	public RedisSerializer<Object> getDefaultRedisSerializer() {
		return this.defaultRedisSerializer;
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
			ObjectProvider<ReactiveSessionRepositoryCustomizer<T>> sessionRepositoryCustomizers) {
		this.sessionRepositoryCustomizers = sessionRepositoryCustomizers.orderedStream().collect(Collectors.toList());
	}

	protected List<ReactiveSessionRepositoryCustomizer<T>> getSessionRepositoryCustomizers() {
		return this.sessionRepositoryCustomizers;
	}

	protected ReactiveRedisTemplate<String, Object> createReactiveRedisTemplate() {
		RedisSerializer<String> keySerializer = RedisSerializer.string();
		RedisSerializer<Object> defaultSerializer = (this.defaultRedisSerializer != null) ? this.defaultRedisSerializer
				: new JdkSerializationRedisSerializer();
		RedisSerializationContext<String, Object> serializationContext = RedisSerializationContext
			.<String, Object>newSerializationContext(defaultSerializer)
			.key(keySerializer)
			.hashKey(keySerializer)
			.build();
		return new ReactiveRedisTemplate<>(this.redisConnectionFactory, serializationContext);
	}

	public ReactiveRedisConnectionFactory getRedisConnectionFactory() {
		return this.redisConnectionFactory;
	}

	@Autowired(required = false)
	public void setSessionIdGenerator(SessionIdGenerator sessionIdGenerator) {
		this.sessionIdGenerator = sessionIdGenerator;
	}

}
