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

package org.springframework.session.data.gemfire;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import org.apache.geode.cache.Cache;
import org.apache.geode.cache.client.ClientCache;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.data.gemfire.CacheFactoryBean;
import org.springframework.data.gemfire.client.ClientCacheFactoryBean;
import org.springframework.data.gemfire.client.PoolFactoryBean;
import org.springframework.data.gemfire.server.CacheServerFactoryBean;
import org.springframework.data.gemfire.support.ConnectionEndpoint;
import org.springframework.session.ExpiringSession;
import org.springframework.session.data.gemfire.config.annotation.web.http.EnableGemFireHttpSession;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.util.FileSystemUtils;
import org.springframework.util.SocketUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests testing the addition/removal of HTTP Session Attributes
 * and the proper persistence of the HTTP Session state in a GemFire cache
 * across a client/server topology.
 *
 * @author John Blum
 * @see org.junit.Test
 * @see org.junit.runner.RunWith
 * @see org.springframework.context.ConfigurableApplicationContext
 * @see org.springframework.session.ExpiringSession
 * @see org.springframework.session.data.gemfire.AbstractGemFireIntegrationTests
 * @see org.springframework.session.data.gemfire.config.annotation.web.http.EnableGemFireHttpSession
 * @see org.springframework.session.data.gemfire.config.annotation.web.http.GemFireHttpSessionConfiguration
 * @see org.springframework.test.context.ContextConfiguration
 * @see org.springframework.test.context.junit4.SpringRunner
 * @see org.apache.geode.cache.Cache
 * @see org.apache.geode.cache.Region
 * @see org.apache.geode.cache.client.ClientCache
 * @since 1.3.1
 */
@RunWith(SpringRunner.class)
@ContextConfiguration(classes =
	ClientServerHttpSessionAttributesDeltaIntegrationTests.SpringSessionDataGemFireClientConfiguration.class)
public class ClientServerHttpSessionAttributesDeltaIntegrationTests extends AbstractGemFireIntegrationTests {

	private static final int MAX_INACTIVE_INTERVAL_IN_SECONDS = 1;

	private static final DateFormat TIMESTAMP = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss");

	private static File processWorkingDirectory;

	private static Process gemfireServer;

	@BeforeClass
	public static void startGemFireServer() throws IOException {
		long t0 = System.currentTimeMillis();

		int port = SocketUtils.findAvailableTcpPort();

		System.err.printf("Starting a GemFire Server running on host [%1$s] listening on port [%2$d]%n",
			SpringSessionDataGemFireServerConfiguration.SERVER_HOSTNAME, port);

		System.setProperty("spring.session.data.gemfire.port", String.valueOf(port));

		String processWorkingDirectoryPathname =
			String.format("gemfire-client-server-tests-%1$s", TIMESTAMP.format(new Date()));

		processWorkingDirectory = createDirectory(processWorkingDirectoryPathname);

		gemfireServer = run(SpringSessionDataGemFireServerConfiguration.class, processWorkingDirectory,
			String.format("-Dspring.session.data.gemfire.port=%1$d", port));

		assertThat(waitForCacheServerToStart(SpringSessionDataGemFireServerConfiguration.SERVER_HOSTNAME, port))
			.isTrue();

		System.err.printf("GemFire Server [startup time = %1$d ms]%n", System.currentTimeMillis() - t0);
	}

	@AfterClass
	public static void stopGemFireServerAndDeleteArtifacts() {
		if (gemfireServer != null) {
			gemfireServer.destroy();
			System.err.printf("GemFire Server [exit code = %1$d]%n",
				waitForProcessToStop(gemfireServer, processWorkingDirectory));
		}

		if (Boolean.valueOf(System.getProperty("spring.session.data.gemfire.fork.clean", Boolean.TRUE.toString()))) {
			FileSystemUtils.deleteRecursively(processWorkingDirectory);
		}

		assertThat(waitForClientCacheToClose(DEFAULT_WAIT_DURATION)).isTrue();
	}

	@Test
	public void sessionCreationAndAccessIsSuccessful() {
		ExpiringSession session = save(touch(createSession()));

		assertThat(session).isNotNull();
		assertThat(session.isExpired()).isFalse();

		session.setAttribute("attrOne", 1);
		session.setAttribute("attrTwo", 2);

		save(touch(session));

		ExpiringSession loadedSession = get(session.getId());

		assertThat(loadedSession).isNotNull();
		assertThat(loadedSession.isExpired()).isFalse();
		assertThat(loadedSession).isNotSameAs(session);
		assertThat(loadedSession.getId()).isEqualTo(session.getId());
		assertThat(loadedSession.<Integer>getAttribute("attrOne")).isEqualTo(1);
		assertThat(loadedSession.<Integer>getAttribute("attrTwo")).isEqualTo(2);

		loadedSession.removeAttribute("attrTwo");

		assertThat(loadedSession.getAttributeNames()).doesNotContain("attrTwo");
		assertThat(loadedSession.getAttributeNames()).hasSize(1);

		save(touch(loadedSession));

		ExpiringSession reloadedSession = get(loadedSession.getId());

		assertThat(reloadedSession).isNotNull();
		assertThat(reloadedSession.isExpired()).isFalse();
		assertThat(reloadedSession).isNotSameAs(loadedSession);
		assertThat(reloadedSession.getId()).isEqualTo(loadedSession.getId());
		assertThat(reloadedSession.getAttributeNames()).hasSize(1);
		assertThat(reloadedSession.getAttributeNames()).doesNotContain("attrTwo");
		assertThat(reloadedSession.<Integer>getAttribute("attrOne")).isEqualTo(1);
	}

	@EnableGemFireHttpSession(maxInactiveIntervalInSeconds = MAX_INACTIVE_INTERVAL_IN_SECONDS)
	static class SpringSessionDataGemFireClientConfiguration {

		@Bean
		static PropertySourcesPlaceholderConfigurer propertySourcesPlaceholderConfigurer() {
			return new PropertySourcesPlaceholderConfigurer();
		}

		Properties gemfireProperties() {
			Properties gemfireProperties = new Properties();
			gemfireProperties.setProperty("log-level", GEMFIRE_LOG_LEVEL);
			return gemfireProperties;
		}

		@Bean
		ClientCacheFactoryBean gemfireCache() {
			ClientCacheFactoryBean clientCacheFactory = new ClientCacheFactoryBean();

			clientCacheFactory.setClose(true);
			clientCacheFactory.setProperties(gemfireProperties());

			return clientCacheFactory;
		}

		@Bean
		PoolFactoryBean gemfirePool(@Value("${spring.session.data.gemfire.port:"
				+ DEFAULT_GEMFIRE_SERVER_PORT + "}") int port) {

			PoolFactoryBean poolFactory = new PoolFactoryBean();

			poolFactory.setKeepAlive(false);
			poolFactory.setPingInterval(TimeUnit.SECONDS.toMillis(5));
			poolFactory.setReadTimeout(2000); // 2 seconds
			poolFactory.setRetryAttempts(1);
			poolFactory.setSubscriptionEnabled(true);

			poolFactory.setServers(Collections.singletonList(new ConnectionEndpoint(
				SpringSessionDataGemFireServerConfiguration.SERVER_HOSTNAME, port)));

			return poolFactory;
		}

		// used for debugging purposes
		@SuppressWarnings("resource")
		public static void main(String[] args) {
			ConfigurableApplicationContext applicationContext =
				new AnnotationConfigApplicationContext(SpringSessionDataGemFireClientConfiguration.class);

			applicationContext.registerShutdownHook();

			ClientCache clientCache = applicationContext.getBean(ClientCache.class);

			for (InetSocketAddress server : clientCache.getCurrentServers()) {
				System.err.printf("GemFire Server [host: %1$s, port: %2$d]%n",
					server.getHostName(), server.getPort());
			}
		}
	}

	@EnableGemFireHttpSession(maxInactiveIntervalInSeconds = MAX_INACTIVE_INTERVAL_IN_SECONDS)
	static class SpringSessionDataGemFireServerConfiguration {

		static final String SERVER_HOSTNAME = "localhost";

		@Bean
		static PropertySourcesPlaceholderConfigurer propertySourcesPlaceholderConfigurer() {
			return new PropertySourcesPlaceholderConfigurer();
		}

		Properties gemfireProperties() {
			Properties gemfireProperties = new Properties();

			gemfireProperties.setProperty("name", name());
			gemfireProperties.setProperty("mcast-port", "0");
			gemfireProperties.setProperty("log-level", GEMFIRE_LOG_LEVEL);

			return gemfireProperties;
		}

		String name() {
			return SpringSessionDataGemFireServerConfiguration.class.getName();
		}

		@Bean
		CacheFactoryBean gemfireCache() {
			CacheFactoryBean gemfireCache = new CacheFactoryBean();

			gemfireCache.setClose(true);
			gemfireCache.setProperties(gemfireProperties());

			return gemfireCache;
		}

		@Bean
		CacheServerFactoryBean gemfireCacheServer(Cache gemfireCache,
			@Value("${spring.session.data.gemfire.port:" + DEFAULT_GEMFIRE_SERVER_PORT + "}") int port) {

			CacheServerFactoryBean cacheServerFactory = new CacheServerFactoryBean();

			cacheServerFactory.setCache(gemfireCache);
			cacheServerFactory.setAutoStartup(true);
			cacheServerFactory.setBindAddress(SERVER_HOSTNAME);
			cacheServerFactory.setPort(port);

			return cacheServerFactory;
		}

		@SuppressWarnings("resource")
		public static void main(String[] args) throws IOException {
			AnnotationConfigApplicationContext context =
				new AnnotationConfigApplicationContext(SpringSessionDataGemFireServerConfiguration.class);

			context.registerShutdownHook();

			writeProcessControlFile(WORKING_DIRECTORY);
		}
	}
}
