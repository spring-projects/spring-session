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

package org.springframework.session.config.annotation.web.http;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.session.SessionRepository;
import org.springframework.session.events.SessionCreatedEvent;
import org.springframework.session.events.SessionDestroyedEvent;

/**
 * Add this annotation to an {@code @Configuration} class to expose the
 * SessionRepositoryFilter as a bean named "springSessionRepositoryFilter" and backed by a
 * user provided implementation of {@link SessionRepository}. In order to leverage the
 * annotation, a single {@link SessionRepository} bean must be provided. For example:
 *
 * <pre>
 * <code>
 * {@literal @Configuration}
 * {@literal @EnableSpringHttpSession}
 * public class SpringHttpSessionConfig {
 *
 *     {@literal @Bean}
 *     public MapSessionRepository sessionRepository() {
 *         return new MapSessionRepository();
 *     }
 *
 * }
 * </code> </pre>
 *
 * <p>
 * It is important to note that no infrastructure for session expirations is configured
 * for you out of the box. This is because things like session expiration are highly
 * implementation dependent. This means if you require cleaning up expired sessions, you
 * are responsible for cleaning up the expired sessions.
 * </p>
 *
 * <p>
 * The following is provided for you with the base configuration:
 * </p>
 *
 * <ul>
 * <li>SessionRepositoryFilter - is responsible for wrapping the HttpServletRequest with
 * an implementation of HttpSession that is backed by a SessionRepository</li>
 * <li>SessionEventHttpSessionListenerAdapter - is responsible for translating Spring
 * Session events into HttpSessionEvent. In order for it to work, the implementation of
 * SessionRepository you provide must support {@link SessionCreatedEvent} and
 * {@link SessionDestroyedEvent}.</li>
 * <li>
 * </ul>
 *
 * @author Rob Winch
 * @since 1.1
 */
@Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
@Target({ java.lang.annotation.ElementType.TYPE })
@Documented
@Import(SpringHttpSessionConfiguration.class)
@Configuration
public @interface EnableSpringHttpSession {
}
