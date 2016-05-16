/*
 * Copyright 2014-2016 the original author or authors.
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

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.session.SessionRepository;
import org.springframework.session.config.annotation.web.http.EnableSpringHttpSession;
import org.springframework.session.data.redis.RedisFlushMode;

/**
 * Add this annotation to an {@code @Configuration} class to expose the
 * SessionRepositoryFilter as a bean named "springSessionRepositoryFilter" and backed by
 * Redis. In order to leverage the annotation, a single {@link RedisConnectionFactory}
 * must be provided. For example: <pre>
 * <code>
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
 * </code> </pre>
 *
 * More advanced configurations can extend {@link RedisHttpSessionConfiguration} instead.
 *
 * @author Rob Winch
 * @since 1.0
 * @see EnableSpringHttpSession
 */
@Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
@Target({ java.lang.annotation.ElementType.TYPE })
@Documented
@Import(RedisHttpSessionConfiguration.class)
@Configuration
public @interface EnableRedisHttpSession {
	int maxInactiveIntervalInSeconds() default 1800;

	/**
	 * <p>
	 * Defines a unique namespace for keys. The value is used to isolate sessions by
	 * changing the prefix from "spring:session:" to
	 * "spring:session:&lt;redisNamespace&gt;:". The default is "" such that all Redis
	 * keys begin with "spring:session".
	 * </p>
	 *
	 * <p>
	 * For example, if you had an application named "Application A" that needed to keep
	 * the sessions isolated from "Application B" you could set two different values for
	 * the applications and they could function within the same Redis instance.
	 * </p>
	 *
	 * @return the unique namespace for keys
	 */
	String redisNamespace() default "";

	/**
	 * <p>
	 * Sets the flush mode for the Redis sessions. The default is ON_SAVE which only
	 * updates the backing Redis when
	 * {@link SessionRepository#save(org.springframework.session.Session)} is invoked. In
	 * a web environment this happens just before the HTTP response is committed.
	 * </p>
	 * <p>
	 * Setting the value to IMMEDIATE will ensure that the any updates to the Session are
	 * immediately written to the Redis instance.
	 * </p>
	 *
	 * @return the {@link RedisFlushMode} to use
	 * @since 1.1
	 */
	RedisFlushMode redisFlushMode() default RedisFlushMode.ON_SAVE;
}
