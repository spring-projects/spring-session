/*
 * Copyright 2014-2018 the original author or authors.
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
package org.springframework.session.config.annotation.web.server;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Add this annotation to a {@code @Configuration} class to configure a {@code WebSessionManager} for a WebFlux
 * application. This annotation assumes a {@code ReactiveSessionRepository} is defined somewhere in the application
 * context. If not, it will fail with a clear error message. For example:
 *
 * <pre>
 * <code>
 * {@literal @Configuration}
 * {@literal @EnableSpringWebSession}
 * public class SpringWebFluxConfig {
 *
 *     {@literal @Bean}
 *     public ReactiveSessionRepository sessionRepository() {
 *         return new ReactiveMapSessionRepository();
 *     }
 *
 * }
 * </code>
 * </pre>
 *
 * @author Greg Turnquist
 * @since 2.0
 */
@Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
@Target({ java.lang.annotation.ElementType.TYPE })
@Documented
@Import(SpringWebSessionConfiguration.class)
@Configuration
public @interface EnableSpringWebSession {
}
