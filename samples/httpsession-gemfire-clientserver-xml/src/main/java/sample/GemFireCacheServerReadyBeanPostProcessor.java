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

package sample;

import java.io.IOException;
import java.net.Socket;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.Resource;

import com.gemstone.gemfire.cache.client.Pool;
import com.gemstone.gemfire.management.membership.ClientMembership;
import com.gemstone.gemfire.management.membership.ClientMembershipEvent;
import com.gemstone.gemfire.management.membership.ClientMembershipListenerAdapter;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.data.gemfire.client.PoolFactoryBean;
import org.springframework.session.data.gemfire.support.GemFireUtils;
import org.springframework.util.Assert;

public class GemFireCacheServerReadyBeanPostProcessor implements BeanPostProcessor {

	static final long DEFAULT_WAIT_DURATION = TimeUnit.SECONDS.toMillis(20);
	static final long DEFAULT_WAIT_INTERVAL = 500L;

	static final CountDownLatch latch = new CountDownLatch(1);

	static final String DEFAULT_SERVER_HOST = "localhost";

	@Value("${spring.session.data.gemfire.port:${application.gemfire.client-server.port}}")
	int port;

// tag::class[]
	static {
		ClientMembership.registerClientMembershipListener(new ClientMembershipListenerAdapter() {
			public void memberJoined(final ClientMembershipEvent event) {
				if (!event.isClient()) {
					latch.countDown();
				}
			}
		});
	}

	@SuppressWarnings("all")
	@Resource(name = "applicationProperties")
	private Properties applicationProperties;

	public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
		if (bean instanceof PoolFactoryBean || bean instanceof Pool) {
			String host = getServerHost(DEFAULT_SERVER_HOST);
			Assert.isTrue(waitForCacheServerToStart(host, this.port), String.format(
				"GemFire Server failed to start [host: '%1$s', port: %2$d]%n", host, this.port));
		}

		return bean;
	}

	public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
		if (bean instanceof PoolFactoryBean || bean instanceof Pool) {
			try {
				latch.await(DEFAULT_WAIT_DURATION, TimeUnit.MILLISECONDS);
			}
			catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}

		return bean;
	}
// tag::end[]

	interface Condition {
		boolean evaluate();
	}

	String getServerHost(String defaultServerHost) {
		return this.applicationProperties.getProperty("application.gemfire.client-server.host", defaultServerHost);
	}

	boolean waitForCacheServerToStart(String host, int port) {
		return waitForCacheServerToStart(host, port, DEFAULT_WAIT_DURATION);
	}

	/* (non-Javadoc) */
	boolean waitForCacheServerToStart(final String host, final int port, long duration) {
		return waitOnCondition(new Condition() {
			AtomicBoolean connected = new AtomicBoolean(false);

			public boolean evaluate() {
				Socket socket = null;

				try {
					// NOTE: this code is not intended to be an atomic, compound action (a possible race condition);
					// opening another connection (at the expense of using system resources) after connectivity
					// has already been established is not detrimental in this use case
					if (!connected.get()) {
						socket = new Socket(host, port);
						connected.set(true);
					}
				}
				catch (IOException ignore) {
				}
				finally {
					GemFireUtils.close(socket);
				}

				return connected.get();
			}
		}, duration);
	}

	boolean waitOnCondition(Condition condition) {
		return waitOnCondition(condition, DEFAULT_WAIT_DURATION);
	}

	@SuppressWarnings("all")
	boolean waitOnCondition(Condition condition, long duration) {
		final long timeout = (System.currentTimeMillis() + duration);

		try {
			while (!condition.evaluate() && System.currentTimeMillis() < timeout) {
				synchronized (condition) {
					TimeUnit.MILLISECONDS.timedWait(condition, DEFAULT_WAIT_INTERVAL);
				}
			}
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}

		return condition.evaluate();
	}
}
