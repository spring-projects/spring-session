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
package org.springframework.session.redis.embedded;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * <p>
 * Runs an embedded Redis instance on a random available port.This is only necessary
 * sincewe do not want users to have to setup a Redis instance. In a production
 * environment, this would not be used since a Redis Server would be setup.
 * </p>
 * <p>
 * The port being used can be identified by using {@literal @RedisServerPort} on a Spring
 * Bean. For example:
 * </p>
 *
 * <pre>
 * {@literal @Configuration}
 * {@literal @EnableEmbeddedRedis}
 * public class RedisHttpSessionConfig {
 *
 *     {@literal @Bean}
 *     public JedisConnectionFactory connectionFactory({@literal @RedisServerPort} int port) throws Exception {
 *         JedisConnectionFactory connection = new JedisConnectionFactory();
 *         connection.setPort(port);
 *         return connection;
 *     }
 *
 * }
 * </pre>
 *
 * See <a href="https://github.com/spring-projects/spring-session/issues/121"
 * >spring-projects/spring-session/issues/121</a> for details on exposing embedded Redis
 * support.
 *
 * @author Rob Winch
 * @see RedisServerPort
 *
 */
@Retention(value=java.lang.annotation.RetentionPolicy.RUNTIME)
@Target(value={java.lang.annotation.ElementType.TYPE})
@Documented
@Import(EmbeddedRedisConfiguration.class)
@Configuration
public @interface EnableEmbeddedRedis {}
