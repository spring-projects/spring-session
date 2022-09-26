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

package org.springframework.session.data.mongo.config.annotation.web.http;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.context.annotation.Import;
import org.springframework.session.MapSession;
import org.springframework.session.data.mongo.MongoIndexedSessionRepository;

/**
 * Add this annotation to a {@code @Configuration} class to expose the
 * SessionRepositoryFilter as a bean named "springSessionRepositoryFilter" and backed by
 * Mongo. Use {@code collectionName} to change default name of the collection used to
 * store sessions.
 *
 * <pre>
 * <code>
 * {@literal @Configuration(proxyBeanMethods = false)}
 * {@literal @EnableMongoHttpSession}
 * public class MongoHttpSessionConfig {
 *
 *     {@literal @Bean}
 *     public MongoOperations mongoOperations() throws UnknownHostException {
 *         return new MongoTemplate(new MongoClient(), "databaseName");
 *     }
 *
 * }
 * </code> </pre>
 *
 * @author Jakub Kubrynski
 * @since 1.2
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Documented
@Import(MongoHttpSessionConfiguration.class)
public @interface EnableMongoHttpSession {

	/**
	 * The maximum time a session will be kept if it is inactive.
	 * @return default max inactive interval in seconds
	 */
	int maxInactiveIntervalInSeconds() default MapSession.DEFAULT_MAX_INACTIVE_INTERVAL_SECONDS;

	/**
	 * The collection name to use.
	 * @return name of the collection to store session
	 */
	String collectionName() default MongoIndexedSessionRepository.DEFAULT_COLLECTION_NAME;

}
