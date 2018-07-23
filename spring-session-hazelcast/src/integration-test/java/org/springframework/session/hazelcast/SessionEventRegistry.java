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

package org.springframework.session.hazelcast;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.context.ApplicationListener;
import org.springframework.session.events.AbstractSessionEvent;

public class SessionEventRegistry implements ApplicationListener<AbstractSessionEvent> {
	private Map<String, AbstractSessionEvent> events = new HashMap<>();
	private ConcurrentMap<String, Object> locks = new ConcurrentHashMap<>();

	@Override
	public void onApplicationEvent(AbstractSessionEvent event) {
		String sessionId = event.getSessionId();
		this.events.put(sessionId, event);
		Object lock = getLock(sessionId);
		synchronized (lock) {
			lock.notifyAll();
		}
	}

	public void clear() {
		this.events.clear();
		this.locks.clear();
	}

	public boolean receivedEvent(String sessionId) throws InterruptedException {
		return waitForEvent(sessionId) != null;
	}

	@SuppressWarnings("unchecked")
	public <E extends AbstractSessionEvent> E getEvent(String sessionId)
			throws InterruptedException {
		return (E) waitForEvent(sessionId);
	}

	@SuppressWarnings("unchecked")
	private <E extends AbstractSessionEvent> E waitForEvent(String sessionId)
			throws InterruptedException {
		Object lock = getLock(sessionId);
		synchronized (lock) {
			if (!this.events.containsKey(sessionId)) {
				lock.wait(10000);
			}
		}
		return (E) this.events.get(sessionId);
	}

	private Object getLock(String sessionId) {
		return this.locks.computeIfAbsent(sessionId, (k) -> new Object());
	}
}
