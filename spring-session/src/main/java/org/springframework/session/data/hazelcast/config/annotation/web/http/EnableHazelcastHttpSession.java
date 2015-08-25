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
package org.springframework.session.data.hazelcast.config.annotation.web.http;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.session.config.annotation.web.http.EnableSpringHttpSession;

/**
 * Add this annotation to a {@code @Configuration} class to expose the
 * SessionRepositoryFilter as a bean named "springSessionRepositoryFilter" and
 * backed by Hazelcast. In order to leverage the annotation, a single {@link HazelcastInstance}
 * must be provided. For example:
 * <pre>
 * <code>
 * {@literal @Configuration}
 * {@literal @EnableHazelcastHttpSession}
 * public class HazelcastHttpSessionConfig {
 *
 *     {@literal @Bean}
 *     public HazelcastInstance embeddedHazelcast() {
 *         Config hazelcastConfig = new Config();
 *         return Hazelcast.newHazelcastInstance(hazelcastConfig);
 *     }
 *
 * }
 * </code>
 * </pre>
 *
 * More advanced configurations can extend {@link HazelcastHttpSessionConfiguration} instead.
 * 
 * @author Tommy Ludwig
 * @since 1.1
 * @see EnableSpringHttpSession
 */
@Retention(value=java.lang.annotation.RetentionPolicy.RUNTIME)
@Target(value={java.lang.annotation.ElementType.TYPE})
@Documented
@Import(HazelcastHttpSessionConfiguration.class)
@Configuration
public @interface EnableHazelcastHttpSession {
	/**
	 * This is the session timeout in seconds. By default, it is set to 1800 seconds (30 minutes).
	 * This should be a non-negative integer.
	 * <p>If you wish to use external configuration (outside of this annotation) to set this value, you can
	 * set this to {@link HazelcastHttpSessionConfiguration#DO_NOT_CONFIGURE_INACTIVE_INTERVAL_STRING},
	 * which will prevent this configuration from overriding the external configuration for this value.</p>
	 * 
	 * @return the seconds a session can be inactive before expiring
	 */
	String maxInactiveIntervalInSeconds() default "1800";
	
	/**
	 * This is the name of the Map that will be used in Hazelcast to store the session data.
	 * Default is "spring:session:sessions".
	 * @return the name of the Map to store the sessions in Hazelcast
	 */
	String sessionMapName() default "spring:session:sessions";

}
