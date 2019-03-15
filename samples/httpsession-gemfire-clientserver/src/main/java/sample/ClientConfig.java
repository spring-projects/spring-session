/*
 * Copyright 2014-2016 the original author or authors.
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

package sample;

import java.util.Collections;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.gemstone.gemfire.cache.client.Pool;
import com.gemstone.gemfire.management.membership.ClientMembership;
import com.gemstone.gemfire.management.membership.ClientMembershipEvent;
import com.gemstone.gemfire.management.membership.ClientMembershipListenerAdapter;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.data.gemfire.client.ClientCacheFactoryBean;
import org.springframework.data.gemfire.support.ConnectionEndpoint;
import org.springframework.session.data.gemfire.config.annotation.web.http.EnableGemFireHttpSession;
import org.springframework.util.Assert;

// tag::class[]
@EnableGemFireHttpSession(maxInactiveIntervalInSeconds = 30, poolName = "DEFAULT") // <1>
public class ClientConfig {

	static final long DEFAULT_WAIT_DURATION = TimeUnit.SECONDS.toMillis(20);

	static final CountDownLatch latch = new CountDownLatch(1);

	static final String DEFAULT_GEMFIRE_LOG_LEVEL = "warning";

	@Bean
	static PropertySourcesPlaceholderConfigurer propertySourcesPlaceholderConfigurer() {
		return new PropertySourcesPlaceholderConfigurer();
	}

	Properties gemfireProperties() { // <2>
		Properties gemfireProperties = new Properties();
		gemfireProperties.setProperty("name", applicationName());
		gemfireProperties.setProperty("log-level", logLevel());
		return gemfireProperties;
	}

	String applicationName() {
		return "samples:httpsession-gemfire-clientserver:"
			.concat(getClass().getSimpleName());
	}

	String logLevel() {
		return System.getProperty("sample.httpsession.gemfire.log-level",
			DEFAULT_GEMFIRE_LOG_LEVEL);
	}

	@Bean
	ClientCacheFactoryBean gemfireCache(
			@Value("${spring.session.data.gemfire.port:" + ServerConfig.SERVER_PORT + "}") int port) { // <3>

		ClientCacheFactoryBean clientCacheFactory = new ClientCacheFactoryBean();

		clientCacheFactory.setClose(true);
		clientCacheFactory.setProperties(gemfireProperties());

		// GemFire Pool settings <4>
		clientCacheFactory.setKeepAlive(false);
		clientCacheFactory.setPingInterval(TimeUnit.SECONDS.toMillis(5));
		clientCacheFactory.setReadTimeout(2000); // 2 seconds
		clientCacheFactory.setRetryAttempts(1);
		clientCacheFactory.setSubscriptionEnabled(true);
		clientCacheFactory.setThreadLocalConnections(false);

		clientCacheFactory.setServers(Collections.singletonList(
			newConnectionEndpoint(ServerConfig.SERVER_HOST, port)));

		return clientCacheFactory;
	}

	ConnectionEndpoint newConnectionEndpoint(String host, int port) {
		return new ConnectionEndpoint(host, port);
	}

	@Bean
	BeanPostProcessor gemfireCacheServerReadyBeanPostProcessor() { // <5>
		return new BeanPostProcessor() {

			public Object postProcessBeforeInitialization(Object bean, String beanName)
				throws BeansException {

				if ("gemfirePool".equals(beanName)) {
					ClientMembership.registerClientMembershipListener(
						new ClientMembershipListenerAdapter() {
							@Override
							public void memberJoined(ClientMembershipEvent event) {
								latch.countDown();
							}
						});
				}

				return bean;
			}

			public Object postProcessAfterInitialization(Object bean, String beanName)
				throws BeansException {

				if (bean instanceof Pool && "gemfirePool".equals(beanName)) {
					try {
						Assert.state(latch.await(DEFAULT_WAIT_DURATION, TimeUnit.MILLISECONDS),
							String.format("GemFire Cache Server failed to start on host [%1$s] and port [%2$d]",
								ServerConfig.SERVER_HOST, ServerConfig.SERVER_PORT));
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
}
