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

package sample.client;

import java.io.IOException;
import java.net.Socket;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.servlet.http.HttpSession;

import com.gemstone.gemfire.cache.client.Pool;
import com.gemstone.gemfire.management.membership.ClientMembership;
import com.gemstone.gemfire.management.membership.ClientMembershipEvent;
import com.gemstone.gemfire.management.membership.ClientMembershipListenerAdapter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.data.gemfire.client.ClientCacheFactoryBean;
import org.springframework.data.gemfire.client.PoolFactoryBean;
import org.springframework.data.gemfire.support.ConnectionEndpoint;
import org.springframework.session.data.gemfire.config.annotation.web.http.EnableGemFireHttpSession;
import org.springframework.session.data.gemfire.support.GemFireUtils;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * A Spring Boot-based GemFire cache client web application that reveals the current state of the HTTP Session.
 *
 * @author John Blum
 * @see javax.servlet.http.HttpSession
 * @see org.springframework.boot.SpringApplication
 * @see org.springframework.boot.autoconfigure.SpringBootApplication
 * @see org.springframework.context.annotation.Bean
 * @see org.springframework.session.data.gemfire.config.annotation.web.http.EnableGemFireHttpSession
 * @see org.springframework.stereotype.Controller
 * @see com.gemstone.gemfire.cache.client.ClientCache
 * @since 1.2.1
 */
// tag::class[]
@SpringBootApplication
@EnableGemFireHttpSession // <1>
@Controller
@SuppressWarnings("unused")
public class Application {

	static final int MAX_CONNECTIONS = 50;

	static final long DEFAULT_WAIT_DURATION = TimeUnit.SECONDS.toMillis(20);
	static final long DEFAULT_WAIT_INTERVAL = 500L;

	static final CountDownLatch latch = new CountDownLatch(1);

	static final String DEFAULT_GEMFIRE_LOG_LEVEL = "config";
	static final String INDEX_TEMPLATE_VIEW_NAME = "index";
	static final String PING_RESPONSE = "PONG";
	static final String REQUEST_COUNT_ATTRIBUTE_NAME = "requestCount";

	static { // <6>
		ClientMembership.registerClientMembershipListener(
			new ClientMembershipListenerAdapter() {
				public void memberJoined(ClientMembershipEvent event) {
					if (!event.isClient()) {
						latch.countDown();
					}
				}
			});
	}

	public static void main(String[] args) {
		SpringApplication.run(Application.class, args);
	}

	protected final Logger logger = LoggerFactory.getLogger(getClass());

	@Bean
	static PropertySourcesPlaceholderConfigurer propertyPlaceholderConfigurer() {
		return new PropertySourcesPlaceholderConfigurer();
	}

	String applicationName() {
		return "samples:httpsession-gemfire-boot-".concat(Application.class.getSimpleName());
	}

	String gemfireLogLevel() {
		return System.getProperty("gemfire.log-level", DEFAULT_GEMFIRE_LOG_LEVEL);
	}

	ConnectionEndpoint newConnectionEndpoint(String host, int port) {
		return new ConnectionEndpoint(host, port);
	}

	Properties gemfireProperties() { // <2>
		Properties gemfireProperties = new Properties();

		gemfireProperties.setProperty("name", applicationName());
		gemfireProperties.setProperty("log-level", gemfireLogLevel());

		return gemfireProperties;
	}

	@Bean
	ClientCacheFactoryBean gemfireCache() { // <3>
		ClientCacheFactoryBean gemfireCache = new ClientCacheFactoryBean();

		gemfireCache.setClose(true);
		gemfireCache.setProperties(gemfireProperties());

		return gemfireCache;
	}

	@Bean
	PoolFactoryBean gemfirePool(@Value("${gemfire.cache.server.host:localhost}") String host,
			@Value("${gemfire.cache.server.port:12480}") int port) { // <4>

		PoolFactoryBean gemfirePool = new PoolFactoryBean();

		gemfirePool.setMaxConnections(MAX_CONNECTIONS);
		gemfirePool.setPingInterval(TimeUnit.SECONDS.toMillis(15));
		gemfirePool.setRetryAttempts(1);
		gemfirePool.setSubscriptionEnabled(true);
		gemfirePool.setServerEndpoints(Collections.singleton(newConnectionEndpoint(host, port)));

		return gemfirePool;
	}

	@Bean
	BeanPostProcessor gemfireCacheServerAvailabilityBeanPostProcessor(
			@Value("${gemfire.cache.server.host:localhost}") final String host,
			@Value("${gemfire.cache.server.port:12480}") final int port) { // <5>

		return new BeanPostProcessor() {

			public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
				if (bean instanceof PoolFactoryBean || bean instanceof Pool) {
					if (!waitForCacheServerToStart(host, port)) {
						Application.this.logger.warn("No GemFire Cache Server found on [host: {}, port: {}]",
							host, port);
					}
				}

				return bean;
			}

			public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
				if (bean instanceof PoolFactoryBean || bean instanceof Pool) {
					try {
						Assert.state(latch.await(DEFAULT_WAIT_DURATION, TimeUnit.MILLISECONDS),
							String.format("GemFire Cache Server failed to start on [host: %1$s, port: %2$d]",
								host, port));
					}
					catch (InterruptedException e) {
						Thread.currentThread().interrupt();
					}
				}

				return bean;
			}
		};
	}

	@RequestMapping("/")
	public String index() { // <7>
		return INDEX_TEMPLATE_VIEW_NAME;
	}

	@RequestMapping(method = RequestMethod.GET, path = "/ping")
	@ResponseBody
	public String ping() { // <8>
		return PING_RESPONSE;
	}

	@RequestMapping(method = RequestMethod.POST, path = "/session")
	public String session(HttpSession session, ModelMap modelMap,
			@RequestParam(name = "attributeName", required = false) String name,
			@RequestParam(name = "attributeValue", required = false) String value) { // <9>

		modelMap.addAttribute("sessionAttributes", attributes(setAttribute(updateRequestCount(session), name, value)));

		return INDEX_TEMPLATE_VIEW_NAME;
	}
// end::class[]

	/* (non-Javadoc) */
	@SuppressWarnings("all")
	HttpSession updateRequestCount(HttpSession session) {
		synchronized (session) {
			Integer currentRequestCount = (Integer) session.getAttribute(REQUEST_COUNT_ATTRIBUTE_NAME);
			session.setAttribute(REQUEST_COUNT_ATTRIBUTE_NAME, nullSafeIncrement(currentRequestCount));
			return session;
		}
	}

	/* (non-Javadoc) */
	Integer nullSafeIncrement(Integer value) {
		return (nullSafeInt(value) + 1);
	}

	/* (non-Javadoc) */
	int nullSafeInt(Number value) {
		return (value != null ? value.intValue() : 0);
	}

	/* (non-Javadoc) */
	HttpSession setAttribute(HttpSession session, String attributeName, String attributeValue) {
		if (isSet(attributeName, attributeValue)) {
			session.setAttribute(attributeName, attributeValue);
		}

		return session;
	}

	/* (non-Javadoc) */
	boolean isSet(String... values) {
		boolean set = true;

		for (String value : values) {
			set &= StringUtils.hasText(value);
		}

		return set;
	}

	Map<String, String> attributes(HttpSession session) {
		Map<String, String> sessionAttributes = new HashMap<String, String>();

		for (String attributeName : toIterable(session.getAttributeNames())) {
			sessionAttributes.put(attributeName, String.valueOf(session.getAttribute(attributeName)));
		}

		return sessionAttributes;
	}

	<T> Iterable<T> toIterable(final Enumeration<T> enumeration) {
		return new Iterable<T>() {
			@Override
			public Iterator<T> iterator() {
				return (enumeration == null ? Collections.<T>emptyIterator() : new Iterator<T>() {
					@Override
					public boolean hasNext() {
						return enumeration.hasMoreElements();
					}

					@Override
					public T next() {
						return enumeration.nextElement();
					}
				});
			}
		};
	}

	/* (non-Javadoc) */
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

	interface Condition {
		boolean evaluate();
	}
}
