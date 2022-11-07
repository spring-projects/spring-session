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

package org.springframework.session.data.redis.config.annotation.web.http;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.session.FlushMode;
import org.springframework.session.MapSession;
import org.springframework.session.SaveMode;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.session.config.SessionRepositoryCustomizer;
import org.springframework.session.config.annotation.web.http.SpringHttpSessionConfiguration;
import org.springframework.session.data.redis.RedisSessionRepository;
import org.springframework.session.data.redis.config.annotation.SpringSessionRedisConnectionFactory;
import org.springframework.util.Assert;

/**
 * Base configuration class for Redis based {@link SessionRepository} implementations.
 *
 * @param <T> the {@link SessionRepository} type
 * @author Vedran Pavic
 * @author Yanming Zhou
 * @since 3.0.0
 * @see RedisHttpSessionConfiguration
 * @see RedisIndexedHttpSessionConfiguration
 * @see SpringSessionRedisConnectionFactory
 */
@Configuration(proxyBeanMethods = false)
@Import(SpringHttpSessionConfiguration.class)
public abstract class AbstractRedisHttpSessionConfiguration<T extends SessionRepository<? extends Session>>
		implements BeanClassLoaderAware {

	private Duration maxInactiveInterval = MapSession.DEFAULT_MAX_INACTIVE_INTERVAL;

	private String redisNamespace = RedisSessionRepository.DEFAULT_KEY_NAMESPACE;

	private FlushMode flushMode = FlushMode.ON_SAVE;

	private SaveMode saveMode = SaveMode.ON_SET_ATTRIBUTE;

	private RedisConnectionFactory redisConnectionFactory;

	private RedisSerializer<Object> defaultRedisSerializer;

	private List<SessionRepositoryCustomizer<T>> sessionRepositoryCustomizers;

	private ClassLoader classLoader;

	public abstract T sessionRepository();

	public void setMaxInactiveInterval(Duration maxInactiveInterval) {
		this.maxInactiveInterval = maxInactiveInterval;
	}

	@Deprecated
	public void setMaxInactiveIntervalInSeconds(int maxInactiveIntervalInSeconds) {
		setMaxInactiveInterval(Duration.ofSeconds(maxInactiveIntervalInSeconds));
	}

	protected Duration getMaxInactiveInterval() {
		return this.maxInactiveInterval;
	}

	public void setRedisNamespace(String namespace) {
		Assert.hasText(namespace, "namespace must not be empty");
		this.redisNamespace = namespace;
	}

	protected String getRedisNamespace() {
		return this.redisNamespace;
	}

	public void setFlushMode(FlushMode flushMode) {
		Assert.notNull(flushMode, "flushMode must not be null");
		this.flushMode = flushMode;
	}

	protected FlushMode getFlushMode() {
		return this.flushMode;
	}

	public void setSaveMode(SaveMode saveMode) {
		Assert.notNull(saveMode, "saveMode must not be null");
		this.saveMode = saveMode;
	}

	protected SaveMode getSaveMode() {
		return this.saveMode;
	}

	@Autowired
	public void setRedisConnectionFactory(
			@SpringSessionRedisConnectionFactory ObjectProvider<RedisConnectionFactory> springSessionRedisConnectionFactory,
			ObjectProvider<RedisConnectionFactory> redisConnectionFactory) {
		this.redisConnectionFactory = springSessionRedisConnectionFactory
				.getIfAvailable(redisConnectionFactory::getObject);
	}

	protected RedisConnectionFactory getRedisConnectionFactory() {
		return this.redisConnectionFactory;
	}

	@Autowired(required = false)
	@Qualifier("springSessionDefaultRedisSerializer")
	public void setDefaultRedisSerializer(RedisSerializer<Object> defaultRedisSerializer) {
		this.defaultRedisSerializer = defaultRedisSerializer;
	}

	protected RedisSerializer<Object> getDefaultRedisSerializer() {
		return this.defaultRedisSerializer;
	}

	@Autowired(required = false)
	public void setSessionRepositoryCustomizer(
			ObjectProvider<SessionRepositoryCustomizer<T>> sessionRepositoryCustomizers) {
		this.sessionRepositoryCustomizers = sessionRepositoryCustomizers.orderedStream().collect(Collectors.toList());
	}

	protected List<SessionRepositoryCustomizer<T>> getSessionRepositoryCustomizers() {
		return this.sessionRepositoryCustomizers;
	}

	@Override
	public void setBeanClassLoader(ClassLoader classLoader) {
		this.classLoader = classLoader;
	}

	protected RedisTemplate<String, Object> createRedisTemplate() {
		RedisTemplate<String, Object> redisTemplate = new RedisTemplate<>();
		redisTemplate.setKeySerializer(RedisSerializer.string());
		redisTemplate.setHashKeySerializer(RedisSerializer.string());
		if (getDefaultRedisSerializer() != null) {
			redisTemplate.setDefaultSerializer(getDefaultRedisSerializer());
		}
		redisTemplate.setConnectionFactory(getRedisConnectionFactory());
		redisTemplate.setBeanClassLoader(this.classLoader);
		redisTemplate.afterPropertiesSet();
		return redisTemplate;
	}

}
