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

package org.springframework.session.events;

import org.springframework.session.Session;
import org.springframework.session.SessionRepository;

/**
 * For {@link SessionRepository} implementations that support it, this event is fired when
 * a {@link Session} is destroyed either explicitly or via expiration.
 *
 * @author Rob Winch
 * @since 1.0
 *
 */
@SuppressWarnings("serial")
public class SessionCreatedEvent extends AbstractSessionEvent {

	public SessionCreatedEvent(Object source, String sessionId) {
		super(source, sessionId);
	}

	/**
	 * Create a new {@link SessionCreatedEvent}.
	 * @param source The Source of the SessionCreatedEvent
	 * @param session the Session that was created
	 */
	public SessionCreatedEvent(Object source, Session session) {
		super(source, session);
	}

}
