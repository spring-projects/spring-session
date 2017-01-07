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

package org.springframework.session.data.gemfire.config.annotation.web.http;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.apache.geode.cache.RegionShortcut;
import org.apache.geode.cache.client.ClientRegionShortcut;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Add this annotation to a Spring {@code @Configuration} class to expose the SessionRepositoryFilter
 * as a bean named "springSessionRepositoryFilter" and backed by Pivotal GemFire or Apache Geode.
 *
 * In order to leverage the annotation, a single Pivotal GemFire/Apache Geode
 * {@link org.apache.geode.cache.Cache}
 * or {@link org.apache.geode.cache.client.ClientCache} instance must be provided.
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
 *       CacheFactoryBean cache  = new CacheFactoryBean();
 *       cache.setProperties(gemfireProperties());
 *       return cache;
 *     }
 * }
 * </code> </pre>
 *
 * Alternatively, Spring Session can be configured to use Pivotal GemFire (Apache Geode) as a client
 * using a dedicated GemFire Server cluster and a {@link org.apache.geode.cache.client.ClientCache}.
 *
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
 *       ClientCacheFactoryBean clientCache = new ClientCacheFactoryBean();
 *       clientCache.setClose(true)
 *       clientCache.setProperties(gemfireProperties());
 *       return clientCache;
 *     }
 *
 *     {@literal @Bean}
 *     public PoolFactoryBean gemfirePool() {
 *         PoolFactoryBean pool = new PoolFactoryBean();
 *         pool.addServer(new ConnectionEndpoint("serverHost", 40404);
 *         return pool;
 *     }
 * }
 * </code>
 *
 * More advanced configurations can extend {@link GemFireHttpSessionConfiguration} instead.
 *
 * @author John Blum
 * @see org.springframework.session.config.annotation.web.http.EnableSpringHttpSession
 * @see GemFireHttpSessionConfiguration
 * @since 1.1.0
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Configuration
@Import(GemFireHttpSessionConfiguration.class)
public @interface EnableGemFireHttpSession {

	/**
	 * Defines the GemFire ClientCache Region DataPolicy.
	 *
	 * @return a ClientRegionShortcut used to specify and configure the ClientCache Region
	 * DataPolicy.
	 * @see org.apache.geode.cache.client.ClientRegionShortcut
	 */
	ClientRegionShortcut clientRegionShortcut() default ClientRegionShortcut.PROXY;

	/**
	 * Identifies the Session attributes by name that should be indexed for query
	 * operations. For instance, find all Sessions in GemFire having attribute A defined
	 * with value X.
	 *
	 * @return an array of Strings identifying the names of Session attributes to index.
	 */
	String[] indexableSessionAttributes() default {};

	/**
	 * Defines the maximum interval in seconds that a Session can remain inactive before
	 * it is considered expired. Defaults to 1800 seconds, or 30 minutes.
	 *
	 * @return an integer value defining the maximum inactive interval in seconds for
	 * declaring a Session expired.
	 */
	int maxInactiveIntervalInSeconds() default 1800;

	/**
	 * Specifies the name of the specific GemFire {@link org.apache.geode.cache.client.Pool} used
	 * by the Spring Session Data GemFire client Region ('ClusteredSpringSessions') when performing
	 * cache operations.  This is attribute is only used in the client/server topology.
	 *
	 * @return the name of the GemFire {@link org.apache.geode.cache.client.Pool} to be used
	 * by the client Region used to manage (HTTP) Sessions.
	 * @see org.springframework.data.gemfire.config.xml.GemfireConstants#DEFAULT_GEMFIRE_POOL_NAME
	 */
	String poolName() default GemFireHttpSessionConfiguration.DEFAULT_GEMFIRE_POOL_NAME;

	/**
	 * Defines the name of the GemFire (Client)Cache Region used to store Sessions.
	 *
	 * @return a String specifying the name of the GemFire (Client)Cache Region used to
	 * store Sessions.
	 * @see org.apache.geode.cache.Region#getName()
	 */
	String regionName() default "ClusteredSpringSessions";

	/**
	 * Defines the GemFire, Peer Cache Region DataPolicy.
	 *
	 * @return a RegionShortcut used to specify and configure the Peer Cache Region
	 * DataPolicy.
	 * @see org.apache.geode.cache.RegionShortcut
	 */
	RegionShortcut serverRegionShortcut() default RegionShortcut.PARTITION;

}
