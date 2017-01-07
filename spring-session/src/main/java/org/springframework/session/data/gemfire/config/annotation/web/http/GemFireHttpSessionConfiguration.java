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

import java.util.concurrent.TimeUnit;

import org.apache.geode.cache.ExpirationAction;
import org.apache.geode.cache.ExpirationAttributes;
import org.apache.geode.cache.GemFireCache;
import org.apache.geode.cache.Region;
import org.apache.geode.cache.RegionAttributes;
import org.apache.geode.cache.RegionShortcut;
import org.apache.geode.cache.client.ClientRegionShortcut;
import org.apache.geode.cache.client.Pool;

import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.ImportAware;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.data.gemfire.GemfireOperations;
import org.springframework.data.gemfire.GemfireTemplate;
import org.springframework.data.gemfire.IndexFactoryBean;
import org.springframework.data.gemfire.IndexType;
import org.springframework.data.gemfire.RegionAttributesFactoryBean;
import org.springframework.data.gemfire.config.xml.GemfireConstants;
import org.springframework.session.ExpiringSession;
import org.springframework.session.config.annotation.web.http.SpringHttpSessionConfiguration;
import org.springframework.session.data.gemfire.AbstractGemFireOperationsSessionRepository.GemFireSession;
import org.springframework.session.data.gemfire.GemFireOperationsSessionRepository;
import org.springframework.session.data.gemfire.config.annotation.web.http.support.GemFireCacheTypeAwareRegionFactoryBean;
import org.springframework.session.data.gemfire.config.annotation.web.http.support.SessionAttributesIndexFactoryBean;
import org.springframework.session.data.gemfire.support.GemFireUtils;
import org.springframework.util.StringUtils;

/**
 * The {@link GemFireHttpSessionConfiguration} class is a Spring {@link Configuration @Configuration} class
 * used to configure and initialize Pivotal GemFire (or Apache Geode) as a clustered, replicated
 * {@link javax.servlet.http.HttpSession} provider implementation in Spring {@link ExpiringSession}.
 *
 * @author John Blum
 * @see org.apache.geode.cache.GemFireCache
 * @see org.apache.geode.cache.Region
 * @see org.apache.geode.cache.client.Pool
 * @see org.springframework.beans.factory.BeanClassLoaderAware
 * @see org.springframework.context.annotation.Bean
 * @see org.springframework.context.annotation.Configuration
 * @see org.springframework.context.annotation.ImportAware
 * @see org.springframework.data.gemfire.GemfireOperations
 * @see org.springframework.data.gemfire.GemfireTemplate
 * @see org.springframework.session.ExpiringSession
 * @see org.springframework.session.config.annotation.web.http.SpringHttpSessionConfiguration
 * @see org.springframework.session.data.gemfire.GemFireOperationsSessionRepository
 * @see org.springframework.session.data.gemfire.config.annotation.web.http.EnableGemFireHttpSession
 * @since 1.1.0
 */
@Configuration
public class GemFireHttpSessionConfiguration extends SpringHttpSessionConfiguration
		implements BeanClassLoaderAware, ImportAware {

	/**
	 * The default maximum interval in seconds in which a Session can remain inactive
	 * before it is considered expired.
	 */
	public static final int DEFAULT_MAX_INACTIVE_INTERVAL_IN_SECONDS = (int) TimeUnit.MINUTES.toSeconds(30);

	protected static final Class<Object> SPRING_SESSION_GEMFIRE_REGION_KEY_CONSTRAINT = Object.class;
	protected static final Class<GemFireSession> SPRING_SESSION_GEMFIRE_REGION_VALUE_CONSTRAINT = GemFireSession.class;

	/**
	 * The default {@link ClientRegionShortcut} used to configure the GemFire ClientCache
	 * Region that will store Spring Sessions.
	 */
	public static final ClientRegionShortcut DEFAULT_CLIENT_REGION_SHORTCUT = ClientRegionShortcut.PROXY;

	/**
	 * The default {@link RegionShortcut} used to configure the GemFire Cache Region that
	 * will store Spring Sessions.
	 */
	public static final RegionShortcut DEFAULT_SERVER_REGION_SHORTCUT = RegionShortcut.PARTITION;

	/**
	 * Name of the GemFire {@link Pool} used by the client Region for managing Session state information.
	 */
	public static final String DEFAULT_GEMFIRE_POOL_NAME = GemfireConstants.DEFAULT_GEMFIRE_POOL_NAME;

	/**
	 * The default name of the Gemfire (Client)Cache Region used to store Sessions.
	 */
	public static final String DEFAULT_SPRING_SESSION_GEMFIRE_REGION_NAME = "ClusteredSpringSessions";

	/**
	 * The default names of all Session attributes that should be indexed by GemFire.
	 */
	public static final String[] DEFAULT_INDEXABLE_SESSION_ATTRIBUTES = {};

	private int maxInactiveIntervalInSeconds = DEFAULT_MAX_INACTIVE_INTERVAL_IN_SECONDS;

	private ClassLoader beanClassLoader;

	private ClientRegionShortcut clientRegionShortcut = DEFAULT_CLIENT_REGION_SHORTCUT;

	private RegionShortcut serverRegionShortcut = DEFAULT_SERVER_REGION_SHORTCUT;

	private String poolName = DEFAULT_GEMFIRE_POOL_NAME;

	private String springSessionGemFireRegionName = DEFAULT_SPRING_SESSION_GEMFIRE_REGION_NAME;

	private String[] indexableSessionAttributes = DEFAULT_INDEXABLE_SESSION_ATTRIBUTES;

	/**
	 * Sets a reference to the {@link ClassLoader} used to load bean definition class
	 * types in a Spring context.
	 *
	 * @param beanClassLoader the ClassLoader used by the Spring container to load bean
	 * class types.
	 * @see org.springframework.beans.factory.BeanClassLoaderAware#setBeanClassLoader(ClassLoader)
	 * @see java.lang.ClassLoader
	 */
	public void setBeanClassLoader(ClassLoader beanClassLoader) {
		this.beanClassLoader = beanClassLoader;
	}

	/**
	 * Gets a reference to the {@link ClassLoader} used to load bean definition class
	 * types in a Spring context.
	 *
	 * @return the ClassLoader used by the Spring container to load bean class types.
	 * @see java.lang.ClassLoader
	 */
	protected ClassLoader getBeanClassLoader() {
		return this.beanClassLoader;
	}

	/**
	 * Sets the {@link ClientRegionShortcut} used to configure the GemFire ClientCache
	 * Region that will store Spring Sessions.
	 *
	 * @param shortcut the ClientRegionShortcut used to configure the GemFire ClientCache
	 * Region.
	 * @see org.apache.geode.cache.client.ClientRegionShortcut
	 */
	public void setClientRegionShortcut(ClientRegionShortcut shortcut) {
		this.clientRegionShortcut = shortcut;
	}

	/**
	 * Gets the {@link ClientRegionShortcut} used to configure the GemFire ClientCache
	 * Region that will store Spring Sessions. Defaults to
	 * {@link ClientRegionShortcut#PROXY}.
	 *
	 * @return the ClientRegionShortcut used to configure the GemFire ClientCache Region.
	 * @see org.apache.geode.cache.client.ClientRegionShortcut
	 * @see EnableGemFireHttpSession#clientRegionShortcut()
	 */
	protected ClientRegionShortcut getClientRegionShortcut() {
		return (this.clientRegionShortcut != null ? this.clientRegionShortcut : DEFAULT_CLIENT_REGION_SHORTCUT);
	}

	/**
	 * Sets the names of all Session attributes that should be indexed by GemFire.
	 *
	 * @param indexableSessionAttributes an array of Strings indicating the names of all
	 * Session attributes for which an Index will be created by GemFire.
	 */
	public void setIndexableSessionAttributes(String[] indexableSessionAttributes) {
		this.indexableSessionAttributes = indexableSessionAttributes;
	}

	/**
	 * Get the names of all Session attributes that should be indexed by GemFire.
	 *
	 * @return an array of Strings indicating the names of all Session attributes for
	 * which an Index will be created by GemFire. Defaults to an empty String array if
	 * unspecified.
	 * @see EnableGemFireHttpSession#indexableSessionAttributes()
	 */
	protected String[] getIndexableSessionAttributes() {
		return (this.indexableSessionAttributes != null ? this.indexableSessionAttributes
			: DEFAULT_INDEXABLE_SESSION_ATTRIBUTES);
	}

	/**
	 * Sets the maximum interval in seconds in which a Session can remain inactive before
	 * it is considered expired.
	 *
	 * @param maxInactiveIntervalInSeconds an integer value specifying the maximum
	 * interval in seconds that a Session can remain inactive before it is considered
	 * expired.
	 */
	public void setMaxInactiveIntervalInSeconds(int maxInactiveIntervalInSeconds) {
		this.maxInactiveIntervalInSeconds = maxInactiveIntervalInSeconds;
	}

	/**
	 * Gets the maximum interval in seconds in which a Session can remain inactive before
	 * it is considered expired.
	 *
	 * @return an integer value specifying the maximum interval in seconds that a Session
	 * can remain inactive before it is considered expired.
	 * @see EnableGemFireHttpSession#maxInactiveIntervalInSeconds()
	 */
	protected int getMaxInactiveIntervalInSeconds() {
		return this.maxInactiveIntervalInSeconds;
	}

	/**
	 * Sets the name of the GemFire {@link Pool} used by the client Region for managing Sessions
	 * during cache operations involving the server.
	 *
	 * @param poolName the name of a GemFire {@link Pool}.
	 * @see Pool#getName()
	 */
	public void setPoolName(String poolName) {
		this.poolName = poolName;
	}

	/**
	 * Returns the name of the GemFire {@link Pool} used by the client Region for managing Sessions
	 * during cache operations involving the server.
	 *
	 * @return the name of a GemFire {@link Pool}.
	 * @see Pool#getName()
	 */
	protected String getPoolName() {
		return (StringUtils.hasText(this.poolName) ? this.poolName : DEFAULT_GEMFIRE_POOL_NAME);
	}

	/**
	 * Sets the {@link RegionShortcut} used to configure the GemFire Cache Region that
	 * will store Spring Sessions.
	 *
	 * @param shortcut the RegionShortcut used to configure the GemFire Cache Region.
	 * @see org.apache.geode.cache.RegionShortcut
	 */
	public void setServerRegionShortcut(RegionShortcut shortcut) {
		this.serverRegionShortcut = shortcut;
	}

	/**
	 * Gets the {@link RegionShortcut} used to configure the GemFire Cache Region that
	 * will store Spring Sessions. Defaults to {@link RegionShortcut#PARTITION}.
	 *
	 * @return the RegionShortcut used to configure the GemFire Cache Region.
	 * @see org.apache.geode.cache.RegionShortcut
	 * @see EnableGemFireHttpSession#serverRegionShortcut()
	 */
	protected RegionShortcut getServerRegionShortcut() {
		return (this.serverRegionShortcut != null ? this.serverRegionShortcut : DEFAULT_SERVER_REGION_SHORTCUT);
	}

	/**
	 * Sets the name of the Gemfire (Client)Cache Region used to store Sessions.
	 *
	 * @param springSessionGemFireRegionName a String specifying the name of the GemFire
	 * (Client)Cache Region used to store the Session.
	 */
	public void setSpringSessionGemFireRegionName(String springSessionGemFireRegionName) {
		this.springSessionGemFireRegionName = springSessionGemFireRegionName;
	}

	/**
	 * Gets the name of the Gemfire (Client)Cache Region used to store Sessions. Defaults
	 * to 'ClusteredSpringSessions'.
	 *
	 * @return a String specifying the name of the GemFire (Client)Cache Region used to
	 * store the Session.
	 * @see org.apache.geode.cache.Region#getName()
	 * @see EnableGemFireHttpSession#regionName()
	 */
	protected String getSpringSessionGemFireRegionName() {
		return (StringUtils.hasText(this.springSessionGemFireRegionName) ? this.springSessionGemFireRegionName
			: DEFAULT_SPRING_SESSION_GEMFIRE_REGION_NAME);
	}

	/**
	 * Callback with the {@link AnnotationMetadata} of the class containing @Import
	 * annotation that imported this @Configuration class.
	 *
	 * @param importMetadata the AnnotationMetadata of the class importing
	 * this @Configuration class.
	 */
	public void setImportMetadata(AnnotationMetadata importMetadata) {
		AnnotationAttributes enableGemFireHttpSessionAnnotationAttributes =
			AnnotationAttributes.fromMap(importMetadata.getAnnotationAttributes(
				EnableGemFireHttpSession.class.getName()));

		setClientRegionShortcut(ClientRegionShortcut.class.cast(enableGemFireHttpSessionAnnotationAttributes
			.getEnum("clientRegionShortcut")));

		setIndexableSessionAttributes(enableGemFireHttpSessionAnnotationAttributes
			.getStringArray("indexableSessionAttributes"));

		setMaxInactiveIntervalInSeconds(enableGemFireHttpSessionAnnotationAttributes
			.getNumber("maxInactiveIntervalInSeconds").intValue());

		setPoolName(enableGemFireHttpSessionAnnotationAttributes.getString("poolName"));

		setServerRegionShortcut(RegionShortcut.class.cast(enableGemFireHttpSessionAnnotationAttributes
			.getEnum("serverRegionShortcut")));

		setSpringSessionGemFireRegionName(enableGemFireHttpSessionAnnotationAttributes
			.getString("regionName"));
	}

	/**
	 * Defines the Spring SessionRepository bean used to interact with GemFire as a Spring
	 * Session provider.
	 *
	 * @param gemfireOperations an instance of {@link GemfireOperations} used to manage
	 * Spring Sessions in GemFire.
	 * @return a GemFireOperationsSessionRepository for managing (clustering/replicating)
	 * Sessions using GemFire.
	 */
	@Bean
	public GemFireOperationsSessionRepository sessionRepository(
			@Qualifier("sessionRegionTemplate") GemfireOperations gemfireOperations) {

		GemFireOperationsSessionRepository sessionRepository =
			new GemFireOperationsSessionRepository(gemfireOperations);

		sessionRepository.setMaxInactiveIntervalInSeconds(getMaxInactiveIntervalInSeconds());

		return sessionRepository;
	}

	/**
	 * Defines a Spring GemfireTemplate bean used to interact with GemFire's (Client)Cache
	 * {@link Region} storing Sessions.
	 *
	 * @param gemFireCache reference to the single GemFire cache instance used by the
	 * {@link GemfireTemplate} to perform GemFire cache data access operations.
	 * @return a {@link GemfireTemplate} used to interact with GemFire's (Client)Cache
	 * {@link Region} storing Sessions.
	 * @see org.springframework.data.gemfire.GemfireTemplate
	 * @see org.apache.geode.cache.Region
	 */
	@Bean
	@DependsOn(DEFAULT_SPRING_SESSION_GEMFIRE_REGION_NAME)
	public GemfireTemplate sessionRegionTemplate(GemFireCache gemFireCache) {
		return new GemfireTemplate(gemFireCache.getRegion(getSpringSessionGemFireRegionName()));
	}

	/**
	 * Defines a Spring GemFire {@link org.apache.geode.cache.Cache} {@link Region}
	 * bean used to store and manage Sessions using either a client-server or peer-to-peer
	 * (p2p) topology.
	 *
	 * @param gemfireCache a reference to the GemFire
	 * {@link org.apache.geode.cache.Cache}.
	 * @param sessionRegionAttributes the GemFire {@link RegionAttributes} used to
	 * configure the {@link Region}.
	 * @return a {@link GemFireCacheTypeAwareRegionFactoryBean} used to configure and
	 * initialize a GemFire Cache {@link Region} for storing and managing Sessions.
	 * @see #getClientRegionShortcut()
	 * @see #getSpringSessionGemFireRegionName()
	 * @see #getServerRegionShortcut()
	 */
	@Bean(name = DEFAULT_SPRING_SESSION_GEMFIRE_REGION_NAME)
	public GemFireCacheTypeAwareRegionFactoryBean<Object, ExpiringSession> sessionRegion(GemFireCache gemfireCache,
			@Qualifier("sessionRegionAttributes") RegionAttributes<Object, ExpiringSession> sessionRegionAttributes) {

		GemFireCacheTypeAwareRegionFactoryBean<Object, ExpiringSession> sessionRegion =
			new GemFireCacheTypeAwareRegionFactoryBean<Object, ExpiringSession>();

		sessionRegion.setClientRegionShortcut(getClientRegionShortcut());
		sessionRegion.setGemfireCache(gemfireCache);
		sessionRegion.setPoolName(getPoolName());
		sessionRegion.setRegionAttributes(sessionRegionAttributes);
		sessionRegion.setRegionName(getSpringSessionGemFireRegionName());
		sessionRegion.setServerRegionShortcut(getServerRegionShortcut());

		return sessionRegion;
	}

	/**
	 * Defines a Spring GemFire {@link RegionAttributes} bean used to configure and
	 * initialize the GemFire cache {@link Region} storing Sessions. Expiration is also
	 * configured for the {@link Region} on the basis that the GemFire cache
	 * {@link Region} is a not a proxy, on either the client or server.
	 *
	 * @param gemfireCache a reference to the GemFire cache.
	 * @return an instance of {@link RegionAttributes} used to configure and initialize
	 * the GemFire cache {@link Region} for storing and managing Sessions.
	 * @see org.springframework.data.gemfire.RegionAttributesFactoryBean
	 * @see org.apache.geode.cache.GemFireCache
	 * @see org.apache.geode.cache.PartitionAttributes
	 * @see #isExpirationAllowed(GemFireCache)
	 */
	@Bean
	@SuppressWarnings({ "unchecked", "deprecation" })
	public RegionAttributesFactoryBean sessionRegionAttributes(GemFireCache gemfireCache) {
		RegionAttributesFactoryBean regionAttributes = new RegionAttributesFactoryBean();

		regionAttributes.setKeyConstraint(SPRING_SESSION_GEMFIRE_REGION_KEY_CONSTRAINT);
		regionAttributes.setValueConstraint(SPRING_SESSION_GEMFIRE_REGION_VALUE_CONSTRAINT);

		if (isExpirationAllowed(gemfireCache)) {
			regionAttributes.setStatisticsEnabled(true);
			regionAttributes.setEntryIdleTimeout(
				new ExpirationAttributes(Math.max(getMaxInactiveIntervalInSeconds(), 0), ExpirationAction.INVALIDATE));
		}

		return regionAttributes;
	}

	/**
	 * Determines whether expiration configuration is allowed to be set on the GemFire
	 * cache {@link Region} used to store and manage Sessions.
	 *
	 * @param gemfireCache a reference to the GemFire cache.
	 * @return a boolean indicating if a {@link Region} can be configured for Region entry
	 * idle-timeout expiration.
	 * @see GemFireUtils#isClient(GemFireCache)
	 * @see GemFireUtils#isProxy(ClientRegionShortcut)
	 * @see GemFireUtils#isProxy(RegionShortcut)
	 */
	boolean isExpirationAllowed(GemFireCache gemfireCache) {
		return !(GemFireUtils.isClient(gemfireCache) ? GemFireUtils.isProxy(getClientRegionShortcut())
			: GemFireUtils.isProxy(getServerRegionShortcut()));
	}

	/**
	 * Defines a GemFire Index bean on the GemFire cache {@link Region} storing and managing Sessions,
	 * specifically on the 'principalName' property for quick lookup of Sessions by 'principalName'.
	 *
	 * @param gemfireCache a reference to the GemFire cache.
	 * @return a {@link IndexFactoryBean} to create an GemFire Index on the 'principalName' property
	 * for Sessions stored in the GemFire cache {@link Region}.
	 * @see org.springframework.data.gemfire.IndexFactoryBean
	 * @see org.apache.geode.cache.GemFireCache
	 */
	@Bean
	@DependsOn(DEFAULT_SPRING_SESSION_GEMFIRE_REGION_NAME)
	public IndexFactoryBean principalNameIndex(GemFireCache gemfireCache) {
		IndexFactoryBean index = new IndexFactoryBean();

		index.setCache(gemfireCache);
		index.setName("principalNameIndex");
		index.setExpression("principalName");
		index.setFrom(GemFireUtils.toRegionPath(getSpringSessionGemFireRegionName()));
		index.setOverride(true);
		index.setType(IndexType.HASH);

		return index;
	}

	/**
	 * Defines a GemFire Index bean on the GemFire cache {@link Region} storing and managing Sessions,
	 * specifically on all Session attributes for quick lookup and queries on Session attribute names
	 * with a given value.
	 *
	 * @param gemfireCache a reference to the GemFire cache.
	 * @return a {@link IndexFactoryBean} to create an GemFire Index on attributes of Sessions
	 * stored in the GemFire cache {@link Region}.
	 * @see org.springframework.data.gemfire.IndexFactoryBean
	 * @see org.apache.geode.cache.GemFireCache
	 */
	@Bean
	@DependsOn(DEFAULT_SPRING_SESSION_GEMFIRE_REGION_NAME)
	public SessionAttributesIndexFactoryBean sessionAttributesIndex(GemFireCache gemfireCache) {
		SessionAttributesIndexFactoryBean sessionAttributesIndex = new SessionAttributesIndexFactoryBean();

		sessionAttributesIndex.setGemFireCache(gemfireCache);
		sessionAttributesIndex.setIndexableSessionAttributes(getIndexableSessionAttributes());
		sessionAttributesIndex.setRegionName(getSpringSessionGemFireRegionName());

		return sessionAttributesIndex;
	}
}
