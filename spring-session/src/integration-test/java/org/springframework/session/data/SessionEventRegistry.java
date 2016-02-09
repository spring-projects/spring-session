/*
 * Copyright 2002-2016 the original author or authors.
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
package org.springframework.session.data;

import org.springframework.context.ApplicationListener;
import org.springframework.session.events.AbstractSessionEvent;

public class SessionEventRegistry implements ApplicationListener<AbstractSessionEvent> {
	private AbstractSessionEvent event;
	private Object lock = new Object();

	public void onApplicationEvent(AbstractSessionEvent event) {
		this.event = event;
		synchronized (lock) {
			lock.notifyAll();
		}
	}

	public void setLock(Object lock) {
		this.lock = lock;
	}

	public void clear() {
		this.event = null;
	}

	public boolean receivedEvent() throws InterruptedException {
		return waitForEvent() != null;
	}

	@SuppressWarnings("unchecked")
	public <E extends AbstractSessionEvent> E getEvent() throws InterruptedException {
		return (E) waitForEvent();
	}

	@SuppressWarnings("unchecked")
	private <E extends AbstractSessionEvent> E waitForEvent() throws InterruptedException {
		synchronized(lock) {
			if(event == null) {
				lock.wait(10000);
			}
		}
		return (E) event;
	}
}
