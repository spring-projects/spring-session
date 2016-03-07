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

package org.springframework.session.data.redis;

import org.springframework.session.SessionRepository;

/**
 * Specifies when to write to the backing Redis instance.
 *
 * @author Rob Winch
 * @since 1.1
 */
public enum RedisFlushMode {
	/**
	 * Only writes to Redis when
	 * {@link SessionRepository#save(org.springframework.session.Session)} is invoked. In
	 * a web environment this is typically done as soon as the HTTP response is committed.
	 */
	ON_SAVE,

	/**
	 * Writes to Redis as soon as possible. For example
	 * {@link SessionRepository#createSession()} will write the session to Redis. Another
	 * example is that setting an attribute on the session will also write to Redis
	 * immediately.
	 */
	IMMEDIATE
}
