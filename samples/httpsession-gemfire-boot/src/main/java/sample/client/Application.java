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

package sample.client;

import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.servlet.http.HttpSession;

import com.gemstone.gemfire.cache.client.Pool;
import com.gemstone.gemfire.management.membership.ClientMembership;
import com.gemstone.gemfire.management.membership.ClientMembershipEvent;
import com.gemstone.gemfire.management.membership.ClientMembershipListenerAdapter;

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
import org.springframework.data.gemfire.util.CollectionUtils;
import org.springframework.session.data.gemfire.config.annotation.web.http.EnableGemFireHttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * A Spring Boot, GemFire cache client, web application that reveals the current state of the HTTP Session.
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
public class Application {

	static final long DEFAULT_WAIT_DURATION = TimeUnit.SECONDS.toMillis(60);

	static final CountDownLatch latch = new CountDownLatch(1);

	static final String DEFAULT_GEMFIRE_LOG_LEVEL = "warning";
	static final String INDEX_TEMPLATE_VIEW_NAME = "index";
	static final String PING_RESPONSE = "PONG";
	static final String REQUEST_COUNT_ATTRIBUTE_NAME = "requestCount";

	public static void main(String[] args) {
		SpringApplication.run(Application.class, args);
	}

	@Bean
	static PropertySourcesPlaceholderConfigurer propertyPlaceholderConfigurer() {
		return new PropertySourcesPlaceholderConfigurer();
	}

	Properties gemfireProperties() { // <2>
		Properties gemfireProperties = new Properties();

		gemfireProperties.setProperty("name", applicationName());
		gemfireProperties.setProperty("log-level", logLevel());

		return gemfireProperties;
	}

	String applicationName() {
		return "samples:httpsession-gemfire-boot:"
			.concat(getClass().getSimpleName());
	}

	String logLevel() {
		return System.getProperty("gemfire.log-level", DEFAULT_GEMFIRE_LOG_LEVEL);
	}

	@Bean
	ClientCacheFactoryBean gemfireCache() { // <3>
		ClientCacheFactoryBean gemfireCache = new ClientCacheFactoryBean();

		gemfireCache.setClose(true);
		gemfireCache.setProperties(gemfireProperties());

		return gemfireCache;
	}

	@Bean
	PoolFactoryBean gemfirePool(
			@Value("${gemfire.cache.server.host:localhost}") String host,
			@Value("${gemfire.cache.server.port:12480}") int port) { // <4>

		PoolFactoryBean gemfirePool = new PoolFactoryBean();

		gemfirePool.setKeepAlive(false);
		gemfirePool.setPingInterval(TimeUnit.SECONDS.toMillis(5));
		gemfirePool.setReadTimeout(Long.valueOf(TimeUnit.SECONDS.toMillis(15)).intValue());
		gemfirePool.setRetryAttempts(1);
		gemfirePool.setSubscriptionEnabled(true);
		gemfirePool.setThreadLocalConnections(false);
		gemfirePool.setServers(Collections.singleton(newConnectionEndpoint(host, port)));

		return gemfirePool;
	}

	ConnectionEndpoint newConnectionEndpoint(String host, int port) {
		return new ConnectionEndpoint(host, port);
	}

	@Bean
	BeanPostProcessor gemfireCacheServerReadyBeanPostProcessor(
			@Value("${gemfire.cache.server.host:localhost}") final String host,
			@Value("${gemfire.cache.server.port:12480}") final int port) { // <5>

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
	public String index() { // <6>
		return INDEX_TEMPLATE_VIEW_NAME;
	}

	@RequestMapping(method = RequestMethod.GET, path = "/ping")
	@ResponseBody
	public String ping() { // <7>
		return PING_RESPONSE;
	}

	@RequestMapping(method = RequestMethod.POST, path = "/session")
	public String session(HttpSession session, ModelMap modelMap,
			@RequestParam(name = "attributeName", required = false) String name,
			@RequestParam(name = "attributeValue", required = false) String value) { // <8>

		modelMap.addAttribute("sessionAttributes",
			attributes(setAttribute(updateRequestCount(session), name, value)));

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
		return (nullSafeIntValue(value) + 1);
	}

	/* (non-Javadoc) */
	int nullSafeIntValue(Number value) {
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

	/* (non-Javadoc) */
	Map<String, String> attributes(HttpSession session) {
		Map<String, String> sessionAttributes = new HashMap<String, String>();

		for (String attributeName : toIterable(session.getAttributeNames())) {
			sessionAttributes.put(attributeName,
				String.valueOf(session.getAttribute(attributeName)));
		}

		return sessionAttributes;
	}

	/* (non-Javadoc) */
	<T> Iterable<T> toIterable(final Enumeration<T> enumeration) {
		return new Iterable<T>() {
			public Iterator<T> iterator() {
				return (enumeration == null ? Collections.<T>emptyIterator()
					: CollectionUtils.toIterator(enumeration));
			}
		};
	}
}
