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
package org.springframework.session.data.couchbase.config.annotation.web.http;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Enables Couchbase backed HTTP sessions.
 *
 * @author Mariusz Kopylec
 * @since 1.2.0
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Configuration
@Import(CouchbaseHttpSessionConfiguration.class)
public @interface EnableCouchbaseHttpSession {

	/**
	 * Max inactive HTTP session interval in seconds after which the session will be
	 * destroyed.
	 *
	 * @return session timeout
	 */
	int timeoutInSeconds() default 1800;

	/**
	 * Enables or disables finding HTTP sessions by principal. Can significantly decrease
	 * application performance when enabled.
	 *
	 * @return are principal sessions enabled
	 * @see org.springframework.session.FindByIndexNameSessionRepository
	 */
	boolean principalSessionsEnabled() default false;
}
