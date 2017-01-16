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

import com.gemstone.gemfire.cache.client.Pool;
import com.gemstone.gemfire.management.membership.ClientMembership;
import com.gemstone.gemfire.management.membership.ClientMembershipEvent;
import com.gemstone.gemfire.management.membership.ClientMembershipListenerAdapter;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.util.Assert;

public class GemFireCacheServerReadyBeanPostProcessor implements BeanPostProcessor {

	static final long DEFAULT_WAIT_DURATION = TimeUnit.SECONDS.toMillis(20);

	static final CountDownLatch latch = new CountDownLatch(1);

	@Value("${spring.session.data.gemfire.port:${application.gemfire.client-server.port}}")
	int port;

	@Value("${application.gemfire.client-server.host:localhost}")
	String host;

	public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
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
						this.host, this.port));
			}
			catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}

		return bean;
	}
	// tag::end[]
}
