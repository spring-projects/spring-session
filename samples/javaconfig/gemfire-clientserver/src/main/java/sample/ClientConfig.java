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

import java.util.Collections;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.geode.cache.Region;
import org.apache.geode.cache.client.ClientCache;
import org.apache.geode.cache.client.Pool;
import org.apache.geode.cache.client.PoolManager;
import org.apache.geode.cache.client.internal.PoolImpl;
import org.apache.geode.management.membership.ClientMembership;
import org.apache.geode.management.membership.ClientMembershipEvent;
import org.apache.geode.management.membership.ClientMembershipListenerAdapter;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.data.gemfire.client.ClientCacheFactoryBean;
import org.springframework.data.gemfire.config.xml.GemfireConstants;
import org.springframework.data.gemfire.support.ConnectionEndpoint;
import org.springframework.session.data.gemfire.config.annotation.web.http.EnableGemFireHttpSession;
import org.springframework.session.data.gemfire.config.annotation.web.http.GemFireHttpSessionConfiguration;
import org.springframework.util.Assert;

// tag::class[]
@EnableGemFireHttpSession(maxInactiveIntervalInSeconds = 30, poolName = "DEFAULT") // <1>
public class ClientConfig {

	static final long DEFAULT_TIMEOUT = TimeUnit.SECONDS.toMillis(60);

	static final CountDownLatch LATCH = new CountDownLatch(1);

	static final String DEFAULT_GEMFIRE_LOG_LEVEL = "warning";
	static final String GEMFIRE_DEFAULT_POOL_NAME = "DEFAULT";
	static final String PROXY_HOST = "dummy.example.com";
	static final String PROXY_PORT = "3128";

	@Bean
	static PropertySourcesPlaceholderConfigurer propertyPlaceholderConfigurer() {
		return new PropertySourcesPlaceholderConfigurer();
	}

	Properties gemfireProperties() { // <2>
		Properties gemfireProperties = new Properties();
		gemfireProperties.setProperty("name", applicationName());
		// gemfireProperties.setProperty("log-file", "gemfire-client.log");
		gemfireProperties.setProperty("log-level", logLevel());
		return gemfireProperties;
	}

	String applicationName() {
		return "spring-session-data-gemfire-clientserver-javaconfig-sample:".concat(getClass().getSimpleName());
	}

	String logLevel() {
		return System.getProperty("spring.session.data.gemfire.log-level", DEFAULT_GEMFIRE_LOG_LEVEL);
	}

	@Bean
	ClientCacheFactoryBean gemfireCache(
			@Value("${spring.session.data.gemfire.host:" + ServerConfig.SERVER_HOST + "}") String host,
			@Value("${spring.session.data.gemfire.port:" + ServerConfig.SERVER_PORT + "}") int port) { // <3>

		ClientCacheFactoryBean clientCacheFactory = new ClientCacheFactoryBean();

		clientCacheFactory.setClose(true);
		clientCacheFactory.setProperties(gemfireProperties());

		// GemFire Pool settings <4>
		clientCacheFactory.setKeepAlive(false);
		clientCacheFactory.setPingInterval(TimeUnit.SECONDS.toMillis(5));
		clientCacheFactory.setReadTimeout(intValue(TimeUnit.SECONDS.toMillis(15)));
		clientCacheFactory.setRetryAttempts(1);
		clientCacheFactory.setSubscriptionEnabled(true);
		clientCacheFactory.setThreadLocalConnections(false);
		clientCacheFactory.setServers(Collections.singletonList(newConnectionEndpoint(host, port)));

		registerClientMembershipListener(); // <5>

		return clientCacheFactory;
	}

	int intValue(Number number) {
		return number.intValue();
	}

	ConnectionEndpoint newConnectionEndpoint(String host, int port) {
		return new ConnectionEndpoint(host, port);
	}

	void registerClientMembershipListener() {
		ClientMembership.registerClientMembershipListener(
			new ClientMembershipListenerAdapter() {
				@Override
				public void memberJoined(ClientMembershipEvent event) {
					LATCH.countDown();
				}
			});
	}

	@Bean
	BeanPostProcessor gemfireClientServerReadyBeanPostProcessor(
		@Value("${spring-session-data-gemfire.cache.server.host:localhost}") final String host,
		@Value("${spring-session-data-gemfire.cache.server.port:12480}") final int port) { // <5>

		return new BeanPostProcessor() {

			private final AtomicBoolean checkGemFireServerIsRunning = new AtomicBoolean(true);
			private final AtomicReference<Pool> defaultPool = new AtomicReference<Pool>(null);

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
					? checkGemFireServerIsRunning.compareAndSet(true, false)
					: whenGemFireCache(bean, beanName));
			}

			private boolean isGemFireRegion(Object bean, String beanName) {
				return (GemFireHttpSessionConfiguration.DEFAULT_SPRING_SESSION_GEMFIRE_REGION_NAME.equals(beanName)
					|| bean instanceof Region);
			}

			private boolean whenGemFireCache(Object bean, String beanName) {
				if (bean instanceof ClientCache) {
					defaultPool.compareAndSet(null, ((ClientCache) bean).getDefaultPool());
				}

				return false;
			}

			private void validateCacheClientNotified() throws InterruptedException {
				boolean didNotTimeout = LATCH.await(DEFAULT_TIMEOUT, TimeUnit.MILLISECONDS);

				Assert.state(didNotTimeout, String.format(
					"GemFire Cache Server failed to start on host [%s] and port [%d]", host, port));
			}

			@SuppressWarnings("all")
			private void validateCacheClientSubscriptionQueueConnectionEstablished() throws InterruptedException {
				boolean cacheClientSubscriptionQueueConnectionEstablished = false;

				Pool pool = defaultIfNull(this.defaultPool.get(), GemfireConstants.DEFAULT_GEMFIRE_POOL_NAME,
					GEMFIRE_DEFAULT_POOL_NAME);

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
		};
	}
	// end::class[]
}
