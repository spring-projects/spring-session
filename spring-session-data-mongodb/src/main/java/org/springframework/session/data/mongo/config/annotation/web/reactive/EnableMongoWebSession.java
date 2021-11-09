/*
 * Copyright 2017 the original author or authors.
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

package org.springframework.session.data.mongo.config.annotation.web.reactive;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.session.data.mongo.ReactiveMongoSessionRepository;

/**
 * Add this annotation to a {@code @Configuration} class to configure a MongoDB-based
 * {@code WebSessionManager} for a WebFlux application. This annotation assumes a
 * {@code ReactorMongoOperations} is defined somewhere in the application context. If not,
 * it will fail with a clear error messages. For example:
 *
 * <pre>
 * <code>
 * {@literal @Configuration}
 * {@literal @EnableMongoWebSession}
 * public class SpringWebFluxConfig {
 *
 *     {@literal @Bean}
 *     public ReactorMongoOperations operations() {
 *         return new MaReactorMongoOperations(...);
 *     }
 *
 * }
 * </code> </pre>
 *
 * @author Greg Turnquist
 * @author Vedran Pavić
 * @since 2.0
 */
@Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
@Target({ java.lang.annotation.ElementType.TYPE })
@Documented
@Import(ReactiveMongoWebSessionConfiguration.class)
@Configuration(proxyBeanMethods = false)
public @interface EnableMongoWebSession {

	/**
	 * The maximum time a session will be kept if it is inactive.
	 * @return default max inactive interval in seconds
	 */
	int maxInactiveIntervalInSeconds() default ReactiveMongoSessionRepository.DEFAULT_INACTIVE_INTERVAL;

	/**
	 * The collection name to use.
	 * @return name of the collection to store session
	 */
	String collectionName() default ReactiveMongoSessionRepository.DEFAULT_COLLECTION_NAME;

}
