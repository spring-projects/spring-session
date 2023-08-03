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
import java.util.Map;

import org.apache.commons.logging.LogFactory;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.EmbeddedValueResolverAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportAware;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.data.redis.connection.ReactiveRedisConnection;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.session.IndexResolver;
import org.springframework.session.Session;
import org.springframework.session.data.redis.ReactiveRedisIndexedSessionRepository;
import org.springframework.session.data.redis.config.ConfigureReactiveRedisAction;
import org.springframework.session.data.redis.config.annotation.ConfigureNotifyKeyspaceEventsReactiveAction;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;
import org.springframework.util.StringValueResolver;
import org.springframework.web.server.session.WebSessionManager;

/**
 * Exposes the {@link WebSessionManager} as a bean named {@code webSessionManager} backed
 * by {@link ReactiveRedisIndexedSessionRepository}. In order to use this a single
 * {@link ReactiveRedisConnectionFactory} must be exposed as a Bean.
 *
 * @author Marcus da Coregio
 * @since 3.3
 * @see EnableRedisIndexedWebSession
 */
@Configuration(proxyBeanMethods = false)
public class RedisIndexedWebSessionConfiguration
		extends AbstractRedisWebSessionConfiguration<ReactiveRedisIndexedSessionRepository>
		implements EmbeddedValueResolverAware, ImportAware {

	private static final boolean lettucePresent;

	private static final boolean jedisPresent;

	private ConfigureReactiveRedisAction configureRedisAction = new ConfigureNotifyKeyspaceEventsReactiveAction();

	private StringValueResolver embeddedValueResolver;

	private ApplicationEventPublisher eventPublisher;

	private IndexResolver<Session> indexResolver;

	static {
		ClassLoader classLoader = RedisIndexedWebSessionConfiguration.class.getClassLoader();
		lettucePresent = ClassUtils.isPresent("io.lettuce.core.RedisClient", classLoader);
		jedisPresent = ClassUtils.isPresent("redis.clients.jedis.Jedis", classLoader);
	}

	@Override
	@Bean
	public ReactiveRedisIndexedSessionRepository sessionRepository() {
		ReactiveRedisTemplate<String, Object> reactiveRedisTemplate = createReactiveRedisTemplate();
		ReactiveRedisIndexedSessionRepository sessionRepository = new ReactiveRedisIndexedSessionRepository(
				reactiveRedisTemplate, createReactiveStringRedisTemplate());
		sessionRepository.setDefaultMaxInactiveInterval(getMaxInactiveInterval());
		sessionRepository.setEventPublisher(this.eventPublisher);
		if (this.indexResolver != null) {
			sessionRepository.setIndexResolver(this.indexResolver);
		}
		if (StringUtils.hasText(getRedisNamespace())) {
			sessionRepository.setRedisKeyNamespace(getRedisNamespace());
		}
		int database = resolveDatabase();
		sessionRepository.setDatabase(database);
		sessionRepository.setSaveMode(getSaveMode());
		sessionRepository.setSessionIdGenerator(getSessionIdGenerator());
		if (getSessionRepositoryCustomizers() != null) {
			getSessionRepositoryCustomizers().forEach((customizer) -> customizer.customize(sessionRepository));
		}
		return sessionRepository;
	}

	private ReactiveStringRedisTemplate createReactiveStringRedisTemplate() {
		return new ReactiveStringRedisTemplate(getRedisConnectionFactory());
	}

	@Bean
	public InitializingBean enableRedisKeyspaceNotificationsInitializer() {
		return new EnableRedisKeyspaceNotificationsInitializer(getRedisConnectionFactory(), this.configureRedisAction);
	}

	@Autowired
	public void setEventPublisher(ApplicationEventPublisher eventPublisher) {
		this.eventPublisher = eventPublisher;
	}

	/**
	 * Sets the action to perform for configuring Redis.
	 * @param configureRedisAction the configuration to apply to Redis. The default is
	 * {@link ConfigureNotifyKeyspaceEventsReactiveAction}
	 */
	@Autowired(required = false)
	public void setConfigureRedisAction(ConfigureReactiveRedisAction configureRedisAction) {
		this.configureRedisAction = configureRedisAction;
	}

	@Autowired(required = false)
	public void setIndexResolver(IndexResolver<Session> indexResolver) {
		this.indexResolver = indexResolver;
	}

	@Override
	public void setEmbeddedValueResolver(StringValueResolver resolver) {
		this.embeddedValueResolver = resolver;
	}

	@Override
	public void setImportMetadata(AnnotationMetadata importMetadata) {
		Map<String, Object> attributeMap = importMetadata
			.getAnnotationAttributes(EnableRedisIndexedWebSession.class.getName());
		AnnotationAttributes attributes = AnnotationAttributes.fromMap(attributeMap);
		if (attributes == null) {
			return;
		}
		setMaxInactiveInterval(Duration.ofSeconds(attributes.<Integer>getNumber("maxInactiveIntervalInSeconds")));
		String redisNamespaceValue = attributes.getString("redisNamespace");
		if (StringUtils.hasText(redisNamespaceValue)) {
			setRedisNamespace(this.embeddedValueResolver.resolveStringValue(redisNamespaceValue));
		}
		setSaveMode(attributes.getEnum("saveMode"));
	}

	private int resolveDatabase() {
		if (lettucePresent && getRedisConnectionFactory() instanceof LettuceConnectionFactory lettuce) {
			return lettuce.getDatabase();
		}
		if (jedisPresent && getRedisConnectionFactory() instanceof JedisConnectionFactory jedis) {
			return jedis.getDatabase();
		}
		return ReactiveRedisIndexedSessionRepository.DEFAULT_DATABASE;
	}

	/**
	 * Ensures that Redis is configured to send keyspace notifications. This is important
	 * to ensure that expiration and deletion of sessions trigger SessionDestroyedEvents.
	 * Without the SessionDestroyedEvent resources may not get cleaned up properly. For
	 * example, the mapping of the Session to WebSocket connections may not get cleaned
	 * up.
	 */
	static class EnableRedisKeyspaceNotificationsInitializer implements InitializingBean {

		private final ReactiveRedisConnectionFactory connectionFactory;

		private final ConfigureReactiveRedisAction configure;

		EnableRedisKeyspaceNotificationsInitializer(ReactiveRedisConnectionFactory connectionFactory,
				ConfigureReactiveRedisAction configure) {
			this.connectionFactory = connectionFactory;
			this.configure = configure;
		}

		@Override
		public void afterPropertiesSet() {
			if (this.configure == ConfigureReactiveRedisAction.NO_OP) {
				return;
			}
			ReactiveRedisConnection connection = this.connectionFactory.getReactiveConnection();
			try {
				this.configure.configure(connection).block();
			}
			finally {
				try {
					connection.close();
				}
				catch (Exception ex) {
					LogFactory.getLog(getClass()).error("Error closing RedisConnection", ex);
				}
			}
		}

	}

}
