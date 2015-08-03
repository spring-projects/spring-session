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

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Add this annotation to an {@code @Configuration} class to expose the
 * SessionRepositoryFilter as a bean named "springSessionRepositoryFilter" and
 * backed by Redis. In order to leverage the annotation, a single {@link RedisConnectionFactory}
 * must be provided. For example:
 *
 * <pre>
 * {@literal @Configuration}
 * {@literal @EnableRedisHttpSession}
 * public class RedisHttpSessionConfig {
 *
 *     {@literal @Bean}
 *     public JedisConnectionFactory connectionFactory() throws Exception {
 *         return new JedisConnectionFactory();
 *     }
 *
 * }
 * </pre>
 *
 * More advanced configurations can extend {@link RedisHttpSessionConfiguration} instead.
 *
 * @author Rob Winch
 * @since 1.0
 */
@Retention(value=java.lang.annotation.RetentionPolicy.RUNTIME)
@Target(value={java.lang.annotation.ElementType.TYPE})
@Documented
@Import(RedisHttpSessionConfiguration.class)
@Configuration
public @interface EnableRedisHttpSession {
	int maxInactiveIntervalInSeconds() default 1800;
	String boundedHashKeyPrefix() default "spring:session:sessions:";
}
