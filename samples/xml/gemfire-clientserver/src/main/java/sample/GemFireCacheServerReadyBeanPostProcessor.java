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

import org.apache.geode.cache.Region;
import org.apache.geode.management.membership.ClientMembership;
import org.apache.geode.management.membership.ClientMembershipEvent;
import org.apache.geode.management.membership.ClientMembershipListenerAdapter;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.util.Assert;

public class GemFireCacheServerReadyBeanPostProcessor implements BeanPostProcessor {

	static final long DEFAULT_TIMEOUT = TimeUnit.SECONDS.toMillis(60);

	static final CountDownLatch LATCH = new CountDownLatch(1);

	@Value("${spring.session.data.gemfire.port:${application.gemfire.client-server.port}}")
	int port;

	@Value("${application.gemfire.client-server.host:localhost}")
	String host;

	public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
		if ("gemfireCache".equals(beanName)) {
			ClientMembership.registerClientMembershipListener(
				new ClientMembershipListenerAdapter() {
					@Override
					public void memberJoined(ClientMembershipEvent event) {
						LATCH.countDown();
					}
				});
		}
		else if (bean instanceof Region) {
			try {
				boolean didNotTimeout = LATCH.await(DEFAULT_TIMEOUT, TimeUnit.MILLISECONDS);

				Assert.state(didNotTimeout, String.format(
					"GemFire Cache Server failed to start on host [%s] and port [%d]", this.host, this.port));
			}
			catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}

		return bean;
	}

	public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
		return bean;
	}
	// tag::end[]
}
