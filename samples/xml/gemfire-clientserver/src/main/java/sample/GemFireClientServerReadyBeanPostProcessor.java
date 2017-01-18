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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.geode.cache.Region;
import org.apache.geode.cache.client.Pool;
import org.apache.geode.cache.client.PoolManager;
import org.apache.geode.cache.client.internal.PoolImpl;
import org.apache.geode.management.membership.ClientMembership;
import org.apache.geode.management.membership.ClientMembershipEvent;
import org.apache.geode.management.membership.ClientMembershipListenerAdapter;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.data.gemfire.config.xml.GemfireConstants;
import org.springframework.session.data.gemfire.config.annotation.web.http.GemFireHttpSessionConfiguration;
import org.springframework.util.Assert;

public class GemFireClientServerReadyBeanPostProcessor implements BeanPostProcessor {

	private static final long DEFAULT_TIMEOUT = TimeUnit.SECONDS.toMillis(60);

	private static final CountDownLatch LATCH = new CountDownLatch(1);

	private static final String GEMFIRE_DEFAULT_POOL_NAME = "DEFAULT";

	static {
		ClientMembership.registerClientMembershipListener(
			new ClientMembershipListenerAdapter() {
				@Override
				public void memberJoined(ClientMembershipEvent event) {
					LATCH.countDown();
				}
			}
		);
	}

	@Value("${spring.session.data.gemfire.port:${application.gemfire.client-server.port}}")
	private int port;

	@Value("${application.gemfire.client-server.host:localhost}")
	private String host;

	private final AtomicBoolean checkGemFireServerIsRunning = new AtomicBoolean(true);
	private final AtomicReference<Pool> gemfirePool = new AtomicReference<Pool>(null);

	public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
		if (shouldCheckWhetherGemFireServerIsRunning(bean, beanName)) {
			try {
				validateCacheClientNotified();
				validateCacheClientSubscriptionQueueConnectionEstablished();
			}
			catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}

		return bean;
	}

	private boolean shouldCheckWhetherGemFireServerIsRunning(Object bean, String beanName) {
		return (isGemFireRegion(bean, beanName)
			? this.checkGemFireServerIsRunning.compareAndSet(true, false)
			: whenGemFirePool(bean, beanName));
	}

	private boolean isGemFireRegion(Object bean, String beanName) {
		return (GemFireHttpSessionConfiguration.DEFAULT_SPRING_SESSION_GEMFIRE_REGION_NAME.equals(beanName)
			|| bean instanceof Region);
	}

	private boolean whenGemFirePool(Object bean, String beanName) {
		if (bean instanceof Pool) {
			this.gemfirePool.compareAndSet(null, (Pool) bean);
		}

		return false;
	}

	private void validateCacheClientNotified() throws InterruptedException {
		boolean didNotTimeout = LATCH.await(DEFAULT_TIMEOUT, TimeUnit.MILLISECONDS);

		Assert.state(didNotTimeout, String.format(
			"GemFire Cache Server failed to start on host [%s] and port [%d]", this.host, this.port));
	}

	@SuppressWarnings("all")
	private void validateCacheClientSubscriptionQueueConnectionEstablished() throws InterruptedException {
		boolean cacheClientSubscriptionQueueConnectionEstablished = false;

		Pool pool = defaultIfNull(this.gemfirePool.get(),
			GemfireConstants.DEFAULT_GEMFIRE_POOL_NAME, GEMFIRE_DEFAULT_POOL_NAME);

		if (pool instanceof PoolImpl) {
			long timeout = (System.currentTimeMillis() + DEFAULT_TIMEOUT);

			while (System.currentTimeMillis() < timeout
				&& !((PoolImpl) pool).isPrimaryUpdaterAlive()) {

				synchronized (pool) {
					TimeUnit.MILLISECONDS.timedWait(pool, 500L);
				}

			}

			cacheClientSubscriptionQueueConnectionEstablished |=
				((PoolImpl) pool).isPrimaryUpdaterAlive();
		}

		Assert.state(cacheClientSubscriptionQueueConnectionEstablished, String.format(
			"Cache client subscription queue connection not established; GemFire Pool was [%s];"
				+ " GemFire Pool configuration was [locators = %s, servers = %s]",
					pool, pool.getLocators(), pool.getServers()));
	}

	private Pool defaultIfNull(Pool pool, String... poolNames) {
		for (String poolName : poolNames) {
			pool = (pool != null ? pool : PoolManager.find(poolName));
		}

		return pool;
	}

	public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
		return bean;
	}
}
// tag::end[]
