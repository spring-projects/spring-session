/*
 * Copyright 2014-2023 the original author or authors.
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

package org.springframework.session;

import org.springframework.lang.NonNull;

/**
 * An interface for specifying a strategy for generating session identifiers.
 *
 * @author Marcus da Coregio
 * @author Yanming Zhou
 * @since 3.2
 */
public interface SessionIdGenerator {

	/**
	 * The default instance.
	 */
	SessionIdGenerator DEFAULT = new UuidSessionIdGenerator();

	/**
	 * Generate identifier for creating new session.
	 * @return the generated session identifier
	 */
	@NonNull
	String generate();

	/**
	 * Generate identifier for changing id of existing session.
	 * @param session the existing {@link Session} object
	 * @return the generated session identifier
	 * @see Session#changeSessionId(SessionIdGenerator)
	 */
	@NonNull
	default String regenerate(Session session) {
		return generate();
	}

}
