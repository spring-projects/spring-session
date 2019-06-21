/*
 * Copyright 2014-2019 the original author or authors.
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

/**
 * Supported modes of writing the session to session store.
 *
 * @author Rob Winch
 * @author Vedran Pavic
 * @since 2.2.0
 */
public enum FlushMode {

	/**
	 * Only writes to session store when {@link SessionRepository#save(Session)} is
	 * invoked. In a web environment this is typically done as soon as the HTTP response
	 * is committed.
	 */
	ON_SAVE,

	/**
	 * Writes to session store as soon as possible. For example
	 * {@link SessionRepository#createSession()} will write the session to session store.
	 * Another example is that setting an attribute using
	 * {@link Session#setAttribute(String, Object)} will also write to session store
	 * immediately.
	 */
	IMMEDIATE

}
