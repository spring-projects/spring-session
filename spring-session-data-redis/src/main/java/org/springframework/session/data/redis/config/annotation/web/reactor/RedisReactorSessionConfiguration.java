/*
 * Copyright 2014-2017 the original author or authors.
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

package org.springframework.session.data.redis.config.annotation.web.reactor;

import java.util.Map;

import org.springframework.context.EmbeddedValueResolverAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportAware;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.serializer.JdkSerializationRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.session.config.annotation.web.server.SpringWebSessionConfiguration;
import org.springframework.session.data.redis.ReactiveRedisOperationsSessionRepository;
import org.springframework.session.data.redis.RedisFlushMode;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.util.StringValueResolver;
import org.springframework.web.server.session.WebSessionManager;

/**
 * Exposes the {@link WebSessionManager} as a bean named {@code webSessionManager}. In
 * order to use this a single {@link ReactiveRedisConnectionFactory} must be exposed as a
 * Bean.
 *
 * @author Vedran Pavic
 * @see EnableRedisReactorSession
 * @since 2.0.0
 */
@Configuration
public class RedisReactorSessionConfiguration extends SpringWebSessionConfiguration
		implements EmbeddedValueResolverAware, ImportAware {

	private static final RedisSerializer<String> keySerializer = new StringRedisSerializer();

	private static final RedisSerializer<Object> valueSerializer = new JdkSerializationRedisSerializer();

	private Integer maxInactiveIntervalInSeconds = 1800;

	private String redisNamespace = "";

	private RedisFlushMode redisFlushMode = RedisFlushMode.ON_SAVE;

	private StringValueResolver embeddedValueResolver;

	@Bean
	public ReactiveRedisOperationsSessionRepository sessionRepository(
			ReactiveRedisConnectionFactory redisConnectionFactory) {
		ReactiveRedisOperationsSessionRepository sessionRepository = new ReactiveRedisOperationsSessionRepository(
				createDefaultTemplate(redisConnectionFactory));
		sessionRepository
				.setDefaultMaxInactiveInterval(this.maxInactiveIntervalInSeconds);

		String redisNamespace = getRedisNamespace();

		if (StringUtils.hasText(redisNamespace)) {
			sessionRepository.setRedisKeyNamespace(redisNamespace);
		}

		sessionRepository.setRedisFlushMode(this.redisFlushMode);

		return sessionRepository;
	}

	public void setMaxInactiveIntervalInSeconds(int maxInactiveIntervalInSeconds) {
		this.maxInactiveIntervalInSeconds = maxInactiveIntervalInSeconds;
	}

	public void setRedisNamespace(String namespace) {
		this.redisNamespace = namespace;
	}

	public void setRedisFlushMode(RedisFlushMode redisFlushMode) {
		Assert.notNull(redisFlushMode, "redisFlushMode cannot be null");
		this.redisFlushMode = redisFlushMode;
	}

	public void setEmbeddedValueResolver(StringValueResolver resolver) {
		this.embeddedValueResolver = resolver;
	}

	public void setImportMetadata(AnnotationMetadata importMetadata) {
		Map<String, Object> enableAttrMap = importMetadata
				.getAnnotationAttributes(EnableRedisReactorSession.class.getName());
		AnnotationAttributes enableAttrs = AnnotationAttributes.fromMap(enableAttrMap);

		if (enableAttrs != null) {
			this.maxInactiveIntervalInSeconds = enableAttrs
					.getNumber("maxInactiveIntervalInSeconds");
			String redisNamespaceValue = enableAttrs.getString("redisNamespace");
			if (StringUtils.hasText(redisNamespaceValue)) {
				this.redisNamespace = this.embeddedValueResolver
						.resolveStringValue(redisNamespaceValue);
			}
			this.redisFlushMode = enableAttrs.getEnum("redisFlushMode");
		}
	}

	private static ReactiveRedisTemplate<String, Object> createDefaultTemplate(
			ReactiveRedisConnectionFactory connectionFactory) {
		RedisSerializationContext<String, Object> serializationContext = RedisSerializationContext
				.<String, Object>newSerializationContext(valueSerializer)
				.key(keySerializer).hashKey(keySerializer).build();

		return new ReactiveRedisTemplate<>(connectionFactory, serializationContext);
	}

	private String getRedisNamespace() {
		if (StringUtils.hasText(this.redisNamespace)) {
			return this.redisNamespace;
		}

		return System.getProperty("spring.session.redis.namespace", "");
	}

}
