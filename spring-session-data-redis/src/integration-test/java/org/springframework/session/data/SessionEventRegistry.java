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

package org.springframework.session.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

import org.jetbrains.annotations.Nullable;

import org.springframework.context.ApplicationListener;
import org.springframework.session.events.AbstractSessionEvent;

public class SessionEventRegistry implements ApplicationListener<AbstractSessionEvent> {

	private Map<String, List<AbstractSessionEvent>> events = new HashMap<>();

	private ConcurrentMap<String, Object> locks = new ConcurrentHashMap<>();

	@Override
	public void onApplicationEvent(AbstractSessionEvent event) {
		String sessionId = event.getSessionId();
		this.events.computeIfAbsent(sessionId, (key) -> new ArrayList<>()).add(event);
		Object lock = getLock(sessionId);
		synchronized (lock) {
			lock.notifyAll();
		}
	}

	public void clear() {
		this.events.clear();
		this.locks.clear();
	}

	public <E extends AbstractSessionEvent> boolean receivedEvent(String sessionId, Class<E> type)
			throws InterruptedException {
		return waitForEvent(sessionId, type) != null;
	}

	@SuppressWarnings("unchecked")
	public <E extends AbstractSessionEvent> E waitForEvent(String sessionId, Class<E> type)
			throws InterruptedException {
		Object lock = getLock(sessionId);
		long waitInMs = TimeUnit.SECONDS.toMillis(10);
		long start = System.currentTimeMillis();
		boolean doneWaiting = false;
		synchronized (lock) {
			while (!doneWaiting) {
				E result = getEvent(sessionId, type);
				if (result == null) {
					// wait until timeout or notified
					// might need to continue trying if the notification
					// was for a different event
					lock.wait(waitInMs);
				}
				long now = System.currentTimeMillis();
				doneWaiting = (now - start) >= waitInMs;
			}
			return getEvent(sessionId, type);
		}
	}

	private <E extends AbstractSessionEvent> @Nullable E getEvent(String sessionId, Class<E> type) {
		List<AbstractSessionEvent> events = this.events.get(sessionId);
		E result = (events != null) ? (E) events.stream()
			.filter((event) -> type.isAssignableFrom(event.getClass()))
			.findFirst()
			.orElse(null) : null;
		return result;
	}

	private Object getLock(String sessionId) {
		return this.locks.computeIfAbsent(sessionId, (k) -> new Object());
	}

}
