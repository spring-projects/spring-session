/*
 * Copyright 2002-2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
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
import java.util.Collections;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.data.gemfire.client.ClientCacheFactoryBean;
import org.springframework.data.gemfire.client.PoolFactoryBean;
import org.springframework.data.gemfire.config.GemfireConstants;
import org.springframework.data.gemfire.support.ConnectionEndpoint;
import org.springframework.session.data.gemfire.config.annotation.web.http.EnableGemFireHttpSession;
import org.springframework.session.data.gemfire.support.GemFireUtils;
import org.springframework.util.Assert;

import com.gemstone.gemfire.cache.client.Pool;
import com.gemstone.gemfire.management.membership.ClientMembership;
import com.gemstone.gemfire.management.membership.ClientMembershipEvent;
import com.gemstone.gemfire.management.membership.ClientMembershipListenerAdapter;

// tag::class[]
@EnableGemFireHttpSession(maxInactiveIntervalInSeconds = 30) // <1>
public class ClientConfig {

	static final long DEFAULT_WAIT_DURATION = TimeUnit.SECONDS.toMillis(20);
	static final long DEFAULT_WAIT_INTERVAL = 500l;

	static final CountDownLatch latch = new CountDownLatch(1);

	static {
		System.setProperty("gemfire.log-level",
			System.getProperty("sample.httpsession.gemfire.log-level", "warning"));

		ClientMembership.registerClientMembershipListener(
			new ClientMembershipListenerAdapter() {
				public void memberJoined(ClientMembershipEvent event) {
					if (!event.isClient()) {
						latch.countDown();
					}
				}
		});
	}

	@Bean
	PropertySourcesPlaceholderConfigurer propertySourcesPlaceholderConfigurer() {
		return new PropertySourcesPlaceholderConfigurer();
	}

	@Bean
	Properties gemfireProperties() { // <2>
		return new Properties();
	}

	@Bean(name = GemfireConstants.DEFAULT_GEMFIRE_POOL_NAME)
	PoolFactoryBean gemfirePool( // <3>
			@Value("${spring.session.data.gemfire.port:"+ServerConfig.SERVER_PORT+"}") int port) {

		PoolFactoryBean poolFactory = new PoolFactoryBean();

		poolFactory.setName(GemfireConstants.DEFAULT_GEMFIRE_POOL_NAME);
		poolFactory.setFreeConnectionTimeout(5000); // 5 seconds
		poolFactory.setKeepAlive(false);
		poolFactory.setMaxConnections(ServerConfig.MAX_CONNECTIONS);
		poolFactory.setPingInterval(TimeUnit.SECONDS.toMillis(5));
		poolFactory.setReadTimeout(2000); // 2 seconds
		poolFactory.setRetryAttempts(2);
		poolFactory.setSubscriptionEnabled(true);
		poolFactory.setThreadLocalConnections(false);

		poolFactory.setServerEndpoints(Collections.singletonList(new ConnectionEndpoint(
			ServerConfig.SERVER_HOSTNAME, port)));

		return poolFactory;
	}

	@Bean
	ClientCacheFactoryBean gemfireCache(Pool gemfirePool) { // <4>
		ClientCacheFactoryBean clientCacheFactory = new ClientCacheFactoryBean();

		clientCacheFactory.setClose(true);
		clientCacheFactory.setProperties(gemfireProperties());
		clientCacheFactory.setPool(gemfirePool);
		clientCacheFactory.setUseBeanFactoryLocator(false);

		return clientCacheFactory;
	}

	@Bean
	BeanPostProcessor gemfireCacheServerReadyBeanPostProcessor( // <5>
			@Value("${spring.session.data.gemfire.port:"+ServerConfig.SERVER_PORT+"}") final int port) {

		return new BeanPostProcessor() {

			public Object postProcessBeforeInitialization(
					Object bean, String beanName) throws BeansException {
				if (bean instanceof PoolFactoryBean || bean instanceof Pool) {
					Assert.isTrue(waitForCacheServerToStart(ServerConfig.SERVER_HOSTNAME, port),
						String.format("GemFire Server failed to start [hostname: %1$s, port: %2$d]",
							ServerConfig.SERVER_HOSTNAME, port));
				}

				return bean;
			}

			public Object postProcessAfterInitialization(
					Object bean, String beanName) throws BeansException {
				if (bean instanceof PoolFactoryBean || bean instanceof Pool) {
					try {
						latch.await(DEFAULT_WAIT_DURATION,
							TimeUnit.MILLISECONDS);
					}
					catch (InterruptedException e) {
						Thread.currentThread().interrupt();
					}
				}

				return bean;
			}
		};
	}
// end::class[]

	interface Condition {
		boolean evaluate();
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
