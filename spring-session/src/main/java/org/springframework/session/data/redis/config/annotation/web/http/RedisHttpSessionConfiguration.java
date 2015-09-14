/*
 * Copyright 2002-2015 the original author or authors.
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
package org.springframework.session.data.redis.config.annotation.web.http;

import java.util.Arrays;
import java.util.Map;

import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportAware;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.session.config.annotation.web.http.SpringHttpSessionConfiguration;
import org.springframework.session.data.redis.RedisOperationsSessionRepository;
import org.springframework.session.data.redis.config.ConfigureNotifyKeyspaceEventsAction;
import org.springframework.session.data.redis.config.ConfigureRedisAction;
import org.springframework.session.web.http.SessionRepositoryFilter;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

/**
 * Exposes the {@link SessionRepositoryFilter} as a bean named
 * "springSessionRepositoryFilter". In order to use this a single
 * {@link RedisConnectionFactory} must be exposed as a Bean.
 *
 * @author Rob Winch
 * @since 1.0
 *
 * @see EnableRedisHttpSession
 */
@Configuration
@EnableScheduling
public class RedisHttpSessionConfiguration extends SpringHttpSessionConfiguration implements ImportAware, BeanClassLoaderAware {

	private ClassLoader beanClassLoader;

	private Integer maxInactiveIntervalInSeconds = 1800;

	private ConfigureRedisAction configureRedisAction = new ConfigureNotifyKeyspaceEventsAction();

	private String redisNamespace = "";

	private RedisSerializer<Object> defaultRedisSerializer;

	@Bean
	public RedisMessageListenerContainer redisMessageListenerContainer(
			RedisConnectionFactory connectionFactory, RedisOperationsSessionRepository messageListener) {

		RedisMessageListenerContainer container = new RedisMessageListenerContainer();
		container.setConnectionFactory(connectionFactory);
		container.addMessageListener(messageListener,
				Arrays.asList(new PatternTopic("__keyevent@*:del"), new PatternTopic("__keyevent@*:expired")));
		container.addMessageListener(messageListener, Arrays.asList(new PatternTopic(messageListener.getSessionCreatedChannelPrefix() + "*")));
		return container;
	}

	@Bean
	public RedisTemplate<Object,Object> sessionRedisTemplate(RedisConnectionFactory connectionFactory) {
		RedisTemplate<Object, Object> template = new RedisTemplate<Object, Object>();
		template.setKeySerializer(new StringRedisSerializer());
		template.setHashKeySerializer(new StringRedisSerializer());
		if(defaultRedisSerializer != null) {
			template.setDefaultSerializer(defaultRedisSerializer);
		}
		template.setConnectionFactory(connectionFactory);
		return template;
	}

	@Bean
	public RedisOperationsSessionRepository sessionRepository(@Qualifier("sessionRedisTemplate") RedisOperations<Object, Object> sessionRedisTemplate, ApplicationEventPublisher applicationEventPublisher) {
		RedisOperationsSessionRepository sessionRepository = new RedisOperationsSessionRepository(sessionRedisTemplate);
		sessionRepository.setApplicationEventPublisher(applicationEventPublisher);
		sessionRepository.setDefaultMaxInactiveInterval(maxInactiveIntervalInSeconds);

		String redisNamespace = getRedisNamespace();
		if(StringUtils.hasText(redisNamespace)) {
			sessionRepository.setRedisKeyNamespace(redisNamespace);
		}
		return sessionRepository;
	}

	public void setMaxInactiveIntervalInSeconds(int maxInactiveIntervalInSeconds) {
		this.maxInactiveIntervalInSeconds = maxInactiveIntervalInSeconds;
	}

	public void setRedisNamespace(String namespace) {
		this.redisNamespace = namespace;
	}

	private String getRedisNamespace() {
		if(StringUtils.hasText(this.redisNamespace)) {
			return this.redisNamespace;
		}
		return System.getProperty("spring.session.redis.namespace","");
	}

	public void setImportMetadata(AnnotationMetadata importMetadata) {

		Map<String, Object> enableAttrMap = importMetadata.getAnnotationAttributes(EnableRedisHttpSession.class.getName());
		AnnotationAttributes enableAttrs = AnnotationAttributes.fromMap(enableAttrMap);
		if(enableAttrs == null) {
			// search parent classes
			Class<?> currentClass = ClassUtils.resolveClassName(importMetadata.getClassName(), beanClassLoader);
			for(Class<?> classToInspect = currentClass ;classToInspect != null; classToInspect = classToInspect.getSuperclass()) {
				EnableRedisHttpSession enableRedisHttpSessionAnnotation = AnnotationUtils.findAnnotation(classToInspect, EnableRedisHttpSession.class);
				if(enableRedisHttpSessionAnnotation == null) {
					continue;
				}
				enableAttrMap = AnnotationUtils
						.getAnnotationAttributes(enableRedisHttpSessionAnnotation);
				enableAttrs = AnnotationAttributes.fromMap(enableAttrMap);
			}
		}
		maxInactiveIntervalInSeconds = enableAttrs.getNumber("maxInactiveIntervalInSeconds");
		this.redisNamespace = enableAttrs.getString("redisNamespace");
	}

	@Bean
	public InitializingBean enableRedisKeyspaceNotificationsInitializer(RedisConnectionFactory connectionFactory) {
		return new EnableRedisKeyspaceNotificationsInitializer(connectionFactory, configureRedisAction);
	}

	/**
	 * Ensures that Redis is configured to send keyspace notifications. This is important to ensure that expiration and
	 * deletion of sessions trigger SessionDestroyedEvents. Without the SessionDestroyedEvent resources may not get
	 * cleaned up properly. For example, the mapping of the Session to WebSocket connections may not get cleaned up.
	 */
	static class EnableRedisKeyspaceNotificationsInitializer implements InitializingBean {
		private final RedisConnectionFactory connectionFactory;

		private ConfigureRedisAction configure;

		EnableRedisKeyspaceNotificationsInitializer(RedisConnectionFactory connectionFactory, ConfigureRedisAction configure) {
			this.connectionFactory = connectionFactory;
			this.configure = configure;
		}

		public void afterPropertiesSet() throws Exception {
			RedisConnection connection = connectionFactory.getConnection();
			configure.configure(connection);
		}
	}

	/**
	 * Sets the action to perform for configuring Redis.
	 *
	 * @param configureRedisAction the configureRedis to set. The default is {@link ConfigureNotifyKeyspaceEventsAction}.
	 */
	@Autowired(required = false)
	public void setConfigureRedisAction(ConfigureRedisAction configureRedisAction) {
		this.configureRedisAction = configureRedisAction;
	}

	@Autowired(required = false)
	@Qualifier("defaultRedisSerializer")
	public void setDefaultRedisSerializer(RedisSerializer<Object> defaultRedisSerializer) {
		this.defaultRedisSerializer = defaultRedisSerializer;
	}

	/* (non-Javadoc)
	 * @see org.springframework.beans.factory.BeanClassLoaderAware#setBeanClassLoader(java.lang.ClassLoader)
	 */
	public void setBeanClassLoader(ClassLoader classLoader) {
		this.beanClassLoader = classLoader;
	}
}
