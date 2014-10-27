/*
 * Copyright 2002-2014 the original author or authors.
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

import java.util.Map;

import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportAware;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.session.ExpiringSession;
import org.springframework.session.SessionRepository;
import org.springframework.session.data.redis.RedisOperationsSessionRepository;
import org.springframework.session.web.http.HttpSessionStrategy;
import org.springframework.session.web.http.SessionRepositoryFilter;
import org.springframework.util.ClassUtils;

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
public class RedisHttpSessionConfiguration implements ImportAware, BeanClassLoaderAware {

    private ClassLoader beanClassLoader;

    private Integer maxInactiveIntervalInSeconds;

    private HttpSessionStrategy httpSessionStrategy;

    @Bean
    public RedisTemplate<String,ExpiringSession> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, ExpiringSession> template = new RedisTemplate<String, ExpiringSession>();
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setConnectionFactory(connectionFactory);
        return template;
    }

    @Bean
    public RedisOperationsSessionRepository sessionRepository(RedisTemplate<String, ExpiringSession> redisTemplate) {
        RedisOperationsSessionRepository sessionRepository = new RedisOperationsSessionRepository(redisTemplate);
        sessionRepository.setDefaultMaxInactiveInterval(maxInactiveIntervalInSeconds);
        return sessionRepository;
    }

    @Bean
    public <S extends ExpiringSession> SessionRepositoryFilter<? extends ExpiringSession> springSessionRepositoryFilter(SessionRepository<S> sessionRepository) {
        SessionRepositoryFilter<S> sessionRepositoryFilter = new SessionRepositoryFilter<S>(sessionRepository);
        if(httpSessionStrategy != null) {
            sessionRepositoryFilter.setHttpSessionStrategy(httpSessionStrategy);
        }
        return sessionRepositoryFilter;
    }

    @Override
    public void setImportMetadata(AnnotationMetadata importMetadata) {

        Map<String, Object> enableAttrMap = importMetadata.getAnnotationAttributes(EnableRedisHttpSession.class.getName());
        AnnotationAttributes enableAttrs = AnnotationAttributes.fromMap(enableAttrMap);
        if(enableAttrs == null) {
            // search parent classes
            Class<?> currentClass = ClassUtils.resolveClassName(importMetadata.getClassName(), beanClassLoader);
            for(Class<?> classToInspect = currentClass ;classToInspect != null; classToInspect = classToInspect.getSuperclass()) {
                EnableRedisHttpSession enableWebSecurityAnnotation = AnnotationUtils.findAnnotation(classToInspect, EnableRedisHttpSession.class);
                if(enableWebSecurityAnnotation == null) {
                    continue;
                }
                enableAttrMap = AnnotationUtils
                        .getAnnotationAttributes(enableWebSecurityAnnotation);
                enableAttrs = AnnotationAttributes.fromMap(enableAttrMap);
            }
        }
        maxInactiveIntervalInSeconds = enableAttrs.getNumber("maxInactiveIntervalInSeconds");
    }

    @Autowired(required = false)
    public void setHttpSessionStrategy(HttpSessionStrategy httpSessionStrategy) {
        this.httpSessionStrategy = httpSessionStrategy;
    }


    /* (non-Javadoc)
     * @see org.springframework.beans.factory.BeanClassLoaderAware#setBeanClassLoader(java.lang.ClassLoader)
     */
    public void setBeanClassLoader(ClassLoader classLoader) {
        this.beanClassLoader = classLoader;
    }
}
