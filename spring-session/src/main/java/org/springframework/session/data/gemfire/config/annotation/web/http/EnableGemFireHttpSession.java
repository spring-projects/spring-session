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

package org.springframework.session.data.gemfire.config.annotation.web.http;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import com.gemstone.gemfire.cache.RegionShortcut;
import com.gemstone.gemfire.cache.client.ClientRegionShortcut;

/**
 * Add this annotation to an {@code @Configuration} class to expose the SessionRepositoryFilter
 * as a bean named "springSessionRepositoryFilter" and backed by Pivotal GemFire or Apache Geode.
 *
 * In order to leverage the annotation, a single Pivotal GemFire/Apache Geode {@link com.gemstone.gemfire.cache.Cache}
 * or {@link com.gemstone.gemfire.cache.client.ClientCache} instance must be provided.
 *
 * For example:
 *
 * <pre>
 * <code>
 * {@literal @Configuration}
 * {@literal @EnableGemFireHttpSession}
 * public class GemFirePeerCacheHttpSessionConfiguration {
 *
 *     {@literal @Bean}
 *     public Properties gemfireProperties() {
 *       Properties gemfireProperties = new Properties();
 *       gemfireProperties.setProperty("name", "ExamplePeer");
 *       gemfireProperties.setProperty("mcast-port", "0");
 *       gemfireProperties.setProperty("log-level", "warning");
 *       return gemfireProperties;
 *     }
 *
 *     {@literal @Bean}
 *     public CacheFactoryBean gemfireCache() throws Exception {
 *       CacheFactoryBean clientCacheFactoryBean = new CacheFactoryBean();
 *       clientCacheFactoryBean.setLazyInitialize(false);
 *       clientCacheFactoryBean.setProperties(gemfireProperties());
 *       clientCacheFactoryBean.setUseBeanFactoryLocator(false);
 *       return clientCacheFactoryBean;
 *     }
 * }
 * </code>
 * </pre>
 *
 * Alternatively, a Spring Session can be configured to use Pivotal GemFire (Apache Geode) as a client
 * using a dedicated GemFire Server cluster and a {@link com.gemstone.gemfire.cache.client.ClientCache}.
 * For example:
 *
 * <code>
 * {@literal @Configuration}
 * {@literal @EnableGemFireHttpSession}
 * public class GemFireClientCacheHttpSessionConfiguration {
 *
 *     {@literal @Bean}
 *     public Properties gemfireProperties() {
 *       Properties gemfireProperties = new Properties();
 *       gemfireProperties.setProperty("name", "ExampleClient");
 *       gemfireProperties.setProperty("log-level", "warning");
 *       return gemfireProperties;
 *     }
 *
 *     {@literal @Bean}
 *     public ClientCacheFactoryBean gemfireCache() throws Exception {
 *       ClientCacheFactoryBean clientCacheFactoryBean = new ClientCacheFactoryBean();
 *       clientCacheFactoryBean.setLazyInitialize(false);
 *       clientCacheFactoryBean.setProperties(gemfireProperties());
 *       clientCacheFactoryBean.setUseBeanFactoryLocator(false);
 *       return clientCacheFactoryBean;
 *     }
 * }
 * </code>
 *
 * More advanced configurations can extend {@link GemFireHttpSessionConfiguration} instead.
 *
 * @author John Blum
 * @see org.springframework.context.annotation.Configuration
 * @see org.springframework.context.annotation.Import
 * @see org.springframework.session.data.gemfire.config.annotation.web.http.GemFireHttpSessionConfiguration
 * @see org.springframework.session.config.annotation.web.http.EnableSpringHttpSession
 * @since 1.1.0
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@SuppressWarnings("unused")
@Target(ElementType.TYPE)
@Configuration
@Import(GemFireHttpSessionConfiguration.class)
public @interface EnableGemFireHttpSession {

	/**
	 * Defines the GemFire ClientCache Region DataPolicy.
	 *
	 * @return a ClientRegionShortcut used to specify and configure the ClientCache Region DataPolicy.
	 * @see com.gemstone.gemfire.cache.client.ClientRegionShortcut
	 */
	ClientRegionShortcut clientRegionShortcut() default ClientRegionShortcut.PROXY;

	/**
	 * Defines the maximum interval in seconds that a Session can remain inactive before it is considered expired.
	 * Defaults to 1800 seconds, or 30 minutes.
	 *
	 * @return an integer value defining the maximum inactive interval in seconds for declaring a Session expired.
	 */
	int maxInactiveIntervalInSeconds() default 1800;

	/**
	 * Defines the name of the GemFire (Client)Cache Region used to store Sessions.
	 *
	 * @return a String specifying the name of the GemFire (Client)Cache Region used to store Sessions.
	 * @see com.gemstone.gemfire.cache.Region#getName()
	 */
	String regionName() default "ClusteredSpringSessions";

	/**
	 * Defines the GemFire, Peer Cache Region DataPolicy.
	 *
	 * @return a RegionShortcut used to specify and configure the Peer Cache Region DataPolicy.
	 * @see com.gemstone.gemfire.cache.RegionShortcut
	 */
	RegionShortcut serverRegionShortcut() default RegionShortcut.PARTITION;

}
