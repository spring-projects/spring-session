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
package sample;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.session.ExpiringSession;
import org.springframework.session.events.SessionExpiredEvent;

import com.hazelcast.core.EntryEvent;
import com.hazelcast.map.listener.EntryEvictedListener;

/**
*
* @author Mark Anderson
*
*/
public class SessionEvictedListener
		implements EntryEvictedListener<String, ExpiringSession> {

	private ApplicationEventPublisher eventPublisher;

	public SessionEvictedListener(ApplicationEventPublisher eventPublisher) {
		this.eventPublisher = eventPublisher;
	}

	public void entryEvicted(EntryEvent<String, ExpiringSession> event) {
		System.out.println("Session removed: " + event);
		eventPublisher.publishEvent(new SessionExpiredEvent(this, event.getOldValue()));
	}

}
