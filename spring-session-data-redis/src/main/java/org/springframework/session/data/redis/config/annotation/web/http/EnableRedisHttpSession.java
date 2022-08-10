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

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.session.FlushMode;
import org.springframework.session.MapSession;
import org.springframework.session.SaveMode;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.session.config.annotation.web.http.EnableSpringHttpSession;
import org.springframework.session.data.redis.RedisIndexedSessionRepository;
import org.springframework.session.data.redis.RedisSessionRepository;
import org.springframework.session.web.http.SessionRepositoryFilter;

/**
 * Add this annotation to an {@code @Configuration} class to expose the
 * {@link SessionRepositoryFilter} as a bean named {@code springSessionRepositoryFilter}
 * and backed by Redis. In order to leverage the annotation, a single
 * {@link RedisConnectionFactory} must be provided. For example:
 *
 * <pre class="code">
 * &#064;Configuration
 * &#064;EnableRedisHttpSession
 * public class RedisHttpSessionConfig {
 *
 *     &#064;Bean
 *     public LettuceConnectionFactory redisConnectionFactory() {
 *         return new LettuceConnectionFactory();
 *     }
 *
 * }
 * </pre>
 *
 * More advanced configurations can extend {@link RedisHttpSessionConfiguration} or
 * {@link RedisIndexedHttpSessionConfiguration} instead.
 *
 * @author Rob Winch
 * @author Vedran Pavic
 * @since 1.0
 * @see EnableSpringHttpSession
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Documented
@Import(RedisHttpSessionConfigurationSelector.class)
@Configuration(proxyBeanMethods = false)
public @interface EnableRedisHttpSession {

	/**
	 * The session timeout in seconds. By default, it is set to 1800 seconds (30 minutes).
	 * This should be a non-negative integer.
	 * @return the seconds a session can be inactive before expiring
	 */
	int maxInactiveIntervalInSeconds() default MapSession.DEFAULT_MAX_INACTIVE_INTERVAL_SECONDS;

	/**
	 * Defines a unique namespace for keys. The value is used to isolate sessions by
	 * changing the prefix from default {@code spring:session:} to
	 * {@code <redisNamespace>:}.
	 * <p>
	 * For example, if you had an application named "Application A" that needed to keep
	 * the sessions isolated from "Application B" you could set two different values for
	 * the applications and they could function within the same Redis instance.
	 * @return the unique namespace for keys
	 */
	String redisNamespace() default RedisIndexedSessionRepository.DEFAULT_NAMESPACE;

	/**
	 * Flush mode for the Redis sessions. The default is {@code ON_SAVE} which only
	 * updates the backing Redis when {@link SessionRepository#save(Session)} is invoked.
	 * In a web environment this happens just before the HTTP response is committed.
	 * <p>
	 * Setting the value to {@code IMMEDIATE} will ensure that the any updates to the
	 * Session are immediately written to the Redis instance.
	 * @return the {@link FlushMode} to use
	 * @since 2.2.0
	 */
	FlushMode flushMode() default FlushMode.ON_SAVE;

	/**
	 * Save mode for the session. The default is {@link SaveMode#ON_SET_ATTRIBUTE}, which
	 * only saves changes made to session.
	 * @return the save mode
	 * @since 2.2.0
	 */
	SaveMode saveMode() default SaveMode.ON_SET_ATTRIBUTE;

	/**
	 * Indicate whether the {@link SessionRepository} should publish session events and
	 * support fetching sessions by index. If true, a
	 * {@link RedisIndexedSessionRepository} will be used in place of
	 * {@link RedisSessionRepository}. This will result in slower performance.
	 * @return true if indexing and events should be enabled, false otherwise
	 */
	boolean enableIndexingAndEvents() default false;

}
