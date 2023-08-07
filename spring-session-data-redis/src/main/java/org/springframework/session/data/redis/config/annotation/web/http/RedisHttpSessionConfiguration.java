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

package org.springframework.session.data.redis.config.annotation.web.http;

import java.time.Duration;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.EmbeddedValueResolverAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportAware;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.session.SessionIdGenerator;
import org.springframework.session.data.redis.RedisSessionRepository;
import org.springframework.session.web.http.SessionRepositoryFilter;
import org.springframework.util.StringUtils;
import org.springframework.util.StringValueResolver;

/**
 * Exposes the {@link SessionRepositoryFilter} as a bean named
 * {@code springSessionRepositoryFilter} backed by {@link RedisSessionRepository}. In
 * order to use this a single {@link RedisConnectionFactory} must be exposed as a Bean.
 *
 * @author Rob Winch
 * @author Eddú Meléndez
 * @author Vedran Pavic
 * @since 1.0
 * @see EnableRedisHttpSession
 */
@Configuration(proxyBeanMethods = false)
public class RedisHttpSessionConfiguration extends AbstractRedisHttpSessionConfiguration<RedisSessionRepository>
		implements EmbeddedValueResolverAware, ImportAware {

	private StringValueResolver embeddedValueResolver;

	private SessionIdGenerator sessionIdGenerator = SessionIdGenerator.DEFAULT;

	@Bean
	@Override
	public RedisSessionRepository sessionRepository() {
		RedisTemplate<String, Object> redisTemplate = createRedisTemplate();
		RedisSessionRepository sessionRepository = new RedisSessionRepository(redisTemplate);
		sessionRepository.setDefaultMaxInactiveInterval(getMaxInactiveInterval());
		if (StringUtils.hasText(getRedisNamespace())) {
			sessionRepository.setRedisKeyNamespace(getRedisNamespace());
		}
		sessionRepository.setFlushMode(getFlushMode());
		sessionRepository.setSaveMode(getSaveMode());
		sessionRepository.setSessionIdGenerator(this.sessionIdGenerator);
		getSessionRepositoryCustomizers()
				.forEach((sessionRepositoryCustomizer) -> sessionRepositoryCustomizer.customize(sessionRepository));
		return sessionRepository;
	}

	@Override
	public void setEmbeddedValueResolver(StringValueResolver resolver) {
		this.embeddedValueResolver = resolver;
	}

	@Override
	public void setImportMetadata(AnnotationMetadata importMetadata) {
		Map<String, Object> attributeMap = importMetadata
				.getAnnotationAttributes(EnableRedisHttpSession.class.getName());
		AnnotationAttributes attributes = AnnotationAttributes.fromMap(attributeMap);
		if (attributes == null) {
			return;
		}
		setMaxInactiveInterval(Duration.ofSeconds(attributes.<Integer>getNumber("maxInactiveIntervalInSeconds")));
		String redisNamespaceValue = attributes.getString("redisNamespace");
		if (StringUtils.hasText(redisNamespaceValue)) {
			setRedisNamespace(this.embeddedValueResolver.resolveStringValue(redisNamespaceValue));
		}
		setFlushMode(attributes.getEnum("flushMode"));
		setSaveMode(attributes.getEnum("saveMode"));
	}

	@Autowired(required = false)
	public void setSessionIdGenerator(SessionIdGenerator sessionIdGenerator) {
		this.sessionIdGenerator = sessionIdGenerator;
	}

}
