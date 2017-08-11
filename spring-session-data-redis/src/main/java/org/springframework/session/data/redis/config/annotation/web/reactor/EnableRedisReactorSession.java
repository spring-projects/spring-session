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

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.session.EnableSpringWebSession;
import org.springframework.session.ReactorSessionRepository;
import org.springframework.session.Session;
import org.springframework.session.data.redis.RedisFlushMode;

/**
 * Add this annotation to an {@code @Configuration} class to expose the
 * {@link org.springframework.web.server.session.WebSessionManager} as a bean named
 * {@code webSessionManager} and backed by Reactive Redis. In order to leverage the
 * annotation, a single {@link ReactiveRedisConnectionFactory} must be provided. For
 * example: <pre class="code">
 * &#064;Configuration
 * &#064;EnableRedisReactorSession
 * public class RedisReactorSessionConfig {
 *
 *     &#064;Bean
 *     public LettuceConnectionFactory redisConnectionFactory() {
 *         return new LettuceConnectionFactory();
 *     }
 *
 * }
 * </pre>
 *
 * More advanced configurations can extend {@link RedisReactorSessionConfiguration}
 * instead.
 *
 * @author Vedran Pavic
 * @since 2.0.0
 * @see EnableSpringWebSession
 */
@Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
@Target({ java.lang.annotation.ElementType.TYPE })
@Documented
@Import(RedisReactorSessionConfiguration.class)
@Configuration
public @interface EnableRedisReactorSession {

	int maxInactiveIntervalInSeconds() default 1800;

	/**
	 * <p>
	 * Defines a unique namespace for keys. The value is used to isolate sessions by
	 * changing the prefix from {@code spring:session:} to
	 * {@code spring:session:<redisNamespace>:}. The default is "" such that all Redis
	 * keys begin with {@code spring:session:}.
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
	 * updates the backing Redis when {@link ReactorSessionRepository#save(Session)} is
	 * invoked. In a web environment this happens just before the HTTP response is
	 * committed.
	 * </p>
	 *
	 * <p>
	 * Setting the value to IMMEDIATE will ensure that the any updates to the Session are
	 * immediately written to the Redis instance.
	 * </p>
	 *
	 * @return the {@link RedisFlushMode} to use
	 */
	RedisFlushMode redisFlushMode() default RedisFlushMode.ON_SAVE;

}
