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
import java.util.Properties;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.data.gemfire.CacheFactoryBean;
import org.springframework.data.gemfire.server.CacheServerFactoryBean;
import org.springframework.session.data.gemfire.config.annotation.web.http.EnableGemFireHttpSession;

import com.gemstone.gemfire.cache.Cache;

// tag::class[]
@EnableGemFireHttpSession(maxInactiveIntervalInSeconds = 30)// <1>
public class ServerConfig {

	static final int MAX_CONNECTIONS = 50;
	static final int SERVER_PORT = 12480;

	static final String SERVER_HOSTNAME = "localhost";

	@Bean
	PropertySourcesPlaceholderConfigurer propertySourcesPlaceholderConfigurer() {
		return new PropertySourcesPlaceholderConfigurer();
	}

	@Bean
	Properties gemfireProperties() { // <2>
		Properties gemfireProperties = new Properties();

		gemfireProperties.setProperty("name", "GemFireClientServerHttpSessionSample");
		gemfireProperties.setProperty("mcast-port", "0");
		gemfireProperties.setProperty("log-level",
			System.getProperty("sample.httpsession.gemfire.log-level", "warning"));
		gemfireProperties.setProperty("jmx-manager", "true");
		gemfireProperties.setProperty("jmx-manager-start", "true");

		return gemfireProperties;
	}

	@Bean
	CacheFactoryBean gemfireCache() { // <3>
		CacheFactoryBean gemfireCache = new CacheFactoryBean();

		gemfireCache.setProperties(gemfireProperties());
		gemfireCache.setUseBeanFactoryLocator(false);

		return gemfireCache;
	}

	@Bean
	CacheServerFactoryBean gemfireCacheServer(Cache gemfireCache, // <4>
		    @Value("${spring.session.data.gemfire.port:"+SERVER_PORT+"}") int port) {

		CacheServerFactoryBean cacheServerFactory = new CacheServerFactoryBean();

		cacheServerFactory.setAutoStartup(true);
		cacheServerFactory.setBindAddress(SERVER_HOSTNAME);
		cacheServerFactory.setCache(gemfireCache);
		cacheServerFactory.setMaxConnections(MAX_CONNECTIONS);
		cacheServerFactory.setPort(port);

		return cacheServerFactory;
	}

	@SuppressWarnings("resource")
	public static void main(final String[] args) throws IOException { // <5>
		new AnnotationConfigApplicationContext(ServerConfig.class)
			.registerShutdownHook();
	}
}
// end::class[]
