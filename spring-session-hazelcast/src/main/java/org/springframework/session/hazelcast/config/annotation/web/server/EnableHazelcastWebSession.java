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

package org.springframework.session.hazelcast.config.annotation.web.server;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import com.hazelcast.core.HazelcastInstance;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.session.MapSession;
import org.springframework.session.SaveMode;
import org.springframework.session.config.annotation.web.server.EnableSpringWebSession;
import org.springframework.web.server.session.WebSessionManager;

/**
 * Add this annotation to an {@code @Configuration} class to expose the
 * {@link WebSessionManager} as a bean named {@code webSessionManager} and backed by
 * Hazelcast. In order to leverage the annotation, a single {@link HazelcastInstance} must
 * be provided. For example:
 *
 * <pre class="code">
 * &#064;Configuration
 * &#064;EnableHazelcastWebSession
 * public class HazelcastHttpSessionConfig {
 *
 *     &#064;Bean
 *     public HazelcastInstance embeddedHazelcast() {
 *         Config hazelcastConfig = new Config();
 *         return Hazelcast.newHazelcastInstance(hazelcastConfig);
 *     }
 *
 * }
 * </pre>
 *
 * More advanced configurations can extend {@link HazelcastWebSessionConfiguration}
 * instead.
 *
 * @author Tommy Ludwig
 * @author Aleksandar Stojsavljevic
 * @author Vedran Pavic
 * @author Didier Loiseau
 * @since 2.6.4
 * @see EnableSpringWebSession
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Documented
@Import(HazelcastWebSessionConfiguration.class)
@Configuration(proxyBeanMethods = false)
public @interface EnableHazelcastWebSession {

	/**
	 * The session timeout in seconds. By default, it is set to 1800 seconds (30 minutes).
	 * This should be a non-negative integer.
	 * @return the seconds a session can be inactive before expiring
	 */
	int maxInactiveIntervalInSeconds() default MapSession.DEFAULT_MAX_INACTIVE_INTERVAL_SECONDS;

	/**
	 * This is the name of the Map that will be used in Hazelcast to store the session
	 * data. Default is "spring:session:sessions".
	 * @return the name of the Map to store the sessions in Hazelcast
	 */
	String sessionMapName() default "spring:session:sessions";

	/**
	 * Save mode for the session. The default is {@link SaveMode#ON_SET_ATTRIBUTE}, which
	 * only saves changes made to session.
	 * @return the save mode
	 * @since 2.2.0
	 */
	SaveMode saveMode() default SaveMode.ON_SET_ATTRIBUTE;

}
