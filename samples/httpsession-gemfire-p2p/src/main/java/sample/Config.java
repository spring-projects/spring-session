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

import java.util.Properties;

import org.springframework.context.annotation.Bean;
import org.springframework.data.gemfire.CacheFactoryBean;
import org.springframework.session.data.gemfire.config.annotation.web.http.EnableGemFireHttpSession;

// tag::class[]
@EnableGemFireHttpSession // <1>
public class Config {

	@Bean
	Properties gemfireProperties() { // <2>
		Properties gemfireProperties = new Properties();

		gemfireProperties.setProperty("name", "GemFireP2PHttpSessionSample");
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

		gemfireCache.setClose(true);
		gemfireCache.setProperties(gemfireProperties());

		return gemfireCache;
	}
}
// end::class[]
