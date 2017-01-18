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
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import org.apache.geode.cache.Cache;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.data.gemfire.CacheFactoryBean;
import org.springframework.data.gemfire.server.CacheServerFactoryBean;
import org.springframework.session.data.gemfire.config.annotation.web.http.EnableGemFireHttpSession;

// tag::class[]
@EnableGemFireHttpSession(maxInactiveIntervalInSeconds = 30) // <1>
public class ServerConfig {

	static final int SERVER_PORT = 12480;

	static final String DEFAULT_GEMFIRE_LOG_LEVEL = "warning";
	static final String SERVER_HOST = "localhost";

	@SuppressWarnings("resource")
	public static void main(String[] args) throws IOException { // <5>
		new AnnotationConfigApplicationContext(ServerConfig.class).registerShutdownHook();
	}

	@Bean
	static PropertySourcesPlaceholderConfigurer propertyPlaceholderConfigurer() {
		return new PropertySourcesPlaceholderConfigurer();
	}

	Properties gemfireProperties() { // <2>
		Properties gemfireProperties = new Properties();

		gemfireProperties.setProperty("name", applicationName());
		gemfireProperties.setProperty("mcast-port", "0");
		// gemfireProperties.setProperty("log-file", "gemfire-server.log");
		gemfireProperties.setProperty("log-level", logLevel());
		// gemfireProperties.setProperty("jmx-manager", "true");
		// gemfireProperties.setProperty("jmx-manager-start", "true");

		return gemfireProperties;
	}

	String applicationName() {
		return "samples:httpsession-gemfire-clientserver:".concat(getClass().getSimpleName());
	}

	String logLevel() {
		return System.getProperty("sample.httpsession.gemfire.log-level", DEFAULT_GEMFIRE_LOG_LEVEL);
	}

	@Bean
	CacheFactoryBean gemfireCache() { // <3>
		CacheFactoryBean gemfireCache = new CacheFactoryBean();

		gemfireCache.setClose(true);
		gemfireCache.setProperties(gemfireProperties());

		return gemfireCache;
	}

	@Bean
	CacheServerFactoryBean gemfireCacheServer(Cache gemfireCache,
			@Value("${spring.session.data.gemfire.port:" + SERVER_PORT + "}") int port) { // <4>

		CacheServerFactoryBean gemfireCacheServer = new CacheServerFactoryBean();

		gemfireCacheServer.setAutoStartup(true);
		gemfireCacheServer.setBindAddress(SERVER_HOST);
		gemfireCacheServer.setCache(gemfireCache);
		gemfireCacheServer.setHostNameForClients(SERVER_HOST);
		gemfireCacheServer.setMaxTimeBetweenPings(Long.valueOf(TimeUnit.SECONDS.toMillis(60)).intValue());
		gemfireCacheServer.setPort(port);

		return gemfireCacheServer;
	}
}
// end::class[]
