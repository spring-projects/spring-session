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

package org.springframework.session.data.gemfire.config.annotation.web.http.support;

import com.gemstone.gemfire.cache.GemFireCache;
import com.gemstone.gemfire.cache.InterestResultPolicy;
import com.gemstone.gemfire.cache.Region;
import com.gemstone.gemfire.cache.RegionAttributes;
import com.gemstone.gemfire.cache.RegionShortcut;
import com.gemstone.gemfire.cache.client.ClientRegionShortcut;
import com.gemstone.gemfire.cache.client.Pool;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.data.gemfire.GenericRegionFactoryBean;
import org.springframework.data.gemfire.client.ClientRegionFactoryBean;
import org.springframework.data.gemfire.client.Interest;
import org.springframework.session.data.gemfire.config.annotation.web.http.GemFireHttpSessionConfiguration;
import org.springframework.session.data.gemfire.support.GemFireUtils;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * The GemFireCacheTypeAwareRegionFactoryBean class is a Spring {@link FactoryBean} used
 * to construct, configure and initialize the GemFire cache {@link Region} used to store
 * and manage Session state.
 *
 * @param <K> the type of keys
 * @param <V> the type of values
 * @author John Blum
 * @since 1.1.0
 * @see org.springframework.data.gemfire.GenericRegionFactoryBean
 * @see org.springframework.beans.factory.BeanFactoryAware
 * @see org.springframework.beans.factory.FactoryBean
 * @see org.springframework.beans.factory.InitializingBean
 */
public class GemFireCacheTypeAwareRegionFactoryBean<K, V>
		implements BeanFactoryAware, FactoryBean<Region<K, V>>, InitializingBean {

	protected static final ClientRegionShortcut DEFAULT_CLIENT_REGION_SHORTCUT =
		GemFireHttpSessionConfiguration.DEFAULT_CLIENT_REGION_SHORTCUT;

	protected static final RegionShortcut DEFAULT_SERVER_REGION_SHORTCUT =
		GemFireHttpSessionConfiguration.DEFAULT_SERVER_REGION_SHORTCUT;

	protected static final String DEFAULT_GEMFIRE_POOL_NAME =
		GemFireHttpSessionConfiguration.DEFAULT_GEMFIRE_POOL_NAME;

	protected static final String DEFAULT_SPRING_SESSION_GEMFIRE_REGION_NAME =
		GemFireHttpSessionConfiguration.DEFAULT_SPRING_SESSION_GEMFIRE_REGION_NAME;

	private BeanFactory beanFactory;

	private ClientRegionShortcut clientRegionShortcut;

	private GemFireCache gemfireCache;

	private Region<K, V> region;

	private RegionAttributes<K, V> regionAttributes;

	private RegionShortcut serverRegionShortcut;

	private String poolName;
	private String regionName;

	/**
	 * Post-construction initialization callback to create, configure and initialize the
	 * GemFire cache {@link Region} used to store, replicate (distribute) and manage
	 * Session state. This method intelligently handles both client-server and
	 * peer-to-peer (p2p) GemFire supported distributed system topologies.
	 *
	 * @throws Exception if the initialization of the GemFire cache {@link Region} fails.
	 * @see org.springframework.session.data.gemfire.support.GemFireUtils#isClient(GemFireCache)
	 * @see #getGemfireCache()
	 * @see #newClientRegion(GemFireCache)
	 * @see #newServerRegion(GemFireCache)
	 */
	public void afterPropertiesSet() throws Exception {
		GemFireCache gemfireCache = getGemfireCache();

		this.region = (GemFireUtils.isClient(gemfireCache) ? newClientRegion(gemfireCache)
				: newServerRegion(gemfireCache));
	}

	/**
	 * Constructs a GemFire cache {@link Region} using a peer-to-peer (p2p) GemFire
	 * topology to store and manage Session state in a GemFire server cluster accessible
	 * from a GemFire cache client.
	 *
	 * @param gemfireCache a reference to the GemFire
	 * {@link com.gemstone.gemfire.cache.Cache}.
	 * @return a peer-to-peer-based GemFire cache {@link Region} to store and manage
	 * Session state.
	 * @throws Exception if the instantiation, configuration and initialization of the
	 * GemFire cache {@link Region} fails.
	 * @see org.springframework.data.gemfire.GenericRegionFactoryBean
	 * @see com.gemstone.gemfire.cache.GemFireCache
	 * @see com.gemstone.gemfire.cache.Region
	 * @see #getRegionAttributes()
	 * @see #getRegionName()
	 * @see #getServerRegionShortcut()
	 */
	protected Region<K, V> newServerRegion(GemFireCache gemfireCache) throws Exception {
		GenericRegionFactoryBean<K, V> serverRegion = new GenericRegionFactoryBean<K, V>();

		serverRegion.setAttributes(getRegionAttributes());
		serverRegion.setCache(gemfireCache);
		serverRegion.setRegionName(getRegionName());
		serverRegion.setShortcut(getServerRegionShortcut());
		serverRegion.afterPropertiesSet();

		return serverRegion.getObject();
	}

	/**
	 * Constructs a GemFire cache {@link Region} using the client-server GemFire topology
	 * to store and manage Session state in a GemFire server cluster accessible from a
	 * GemFire cache client.
	 *
	 * @param gemfireCache a reference to the GemFire
	 * {@link com.gemstone.gemfire.cache.Cache}.
	 * @return a client-server-based GemFire cache {@link Region} to store and manage
	 * Session state.
	 * @throws Exception if the instantiation, configuration and initialization of the
	 * GemFire cache {@link Region} fails.
	 * @see org.springframework.data.gemfire.client.ClientRegionFactoryBean
	 * @see com.gemstone.gemfire.cache.GemFireCache
	 * @see com.gemstone.gemfire.cache.Region
	 * @see #getClientRegionShortcut()
	 * @see #getRegionAttributes()
	 * @see #getRegionName()
	 * @see #registerInterests(boolean)
	 */
	protected Region<K, V> newClientRegion(GemFireCache gemfireCache) throws Exception {
		ClientRegionFactoryBean<K, V> clientRegion = new ClientRegionFactoryBean<K, V>();

		ClientRegionShortcut shortcut = getClientRegionShortcut();

		clientRegion.setAttributes(getRegionAttributes());
		clientRegion.setBeanFactory(getBeanFactory());
		clientRegion.setCache(gemfireCache);
		clientRegion.setInterests(registerInterests(!GemFireUtils.isLocal(shortcut)));
		clientRegion.setPoolName(getPoolName());
		clientRegion.setRegionName(getRegionName());
		clientRegion.setShortcut(shortcut);
		clientRegion.afterPropertiesSet();

		return clientRegion.getObject();
	}

	/**
	 * Decides whether interests will be registered for all keys. Interests is only registered on
	 * a client and typically only when the client is a (CACHING) PROXY to the server (i.e. non-LOCAL only).
	 *
	 * @param register a boolean value indicating whether interests should be registered.
	 * @return an array of Interests KEY/VALUE registrations.
	 * @see org.springframework.data.gemfire.client.Interest
	 */
	@SuppressWarnings("unchecked")
	protected Interest<K>[] registerInterests(boolean register) {
		return (!register ? new Interest[0] : new Interest[] {
			new Interest<String>("ALL_KEYS", InterestResultPolicy.KEYS)
		});
	}

	/**
	 * Returns a reference to the constructed GemFire cache {@link Region} used to store
	 * and manage Session state.
	 *
	 * @return the {@link Region} used to store and manage Session state.
	 * @throws Exception if the {@link Region} reference cannot be obtained.
	 * @see com.gemstone.gemfire.cache.Region
	 */
	public Region<K, V> getObject() throws Exception {
		return this.region;
	}

	/**
	 * Returns the specific type of GemFire cache {@link Region} this factory creates when
	 * initialized or Region.class when uninitialized.
	 *
	 * @return the GemFire cache {@link Region} class type constructed by this factory.
	 * @see com.gemstone.gemfire.cache.Region
	 * @see java.lang.Class
	 */
	public Class<?> getObjectType() {
		return (this.region != null ? this.region.getClass() : Region.class);
	}

	/**
	 * Returns true indicating the GemFire cache {@link Region} created by this factory is
	 * the sole instance.
	 *
	 * @return true to indicate the GemFire cache {@link Region} storing and managing
	 * Sessions is a Singleton.
	 */
	public boolean isSingleton() {
		return true;
	}

	/**
	 * Sets a reference to the Spring {@link BeanFactory} responsible for
	 * creating GemFire components.
	 *
	 * @param beanFactory reference to the Spring {@link BeanFactory}
	 * @throws IllegalArgumentException if the {@link BeanFactory} reference is null.
	 * @see org.springframework.beans.factory.BeanFactory
	 */
	public void setBeanFactory(BeanFactory beanFactory) {
		Assert.notNull(beanFactory, "BeanFactory must not be null");
		this.beanFactory = beanFactory;
	}

	/**
	 * Gets a reference to the Spring {@link BeanFactory} responsible for
	 * creating GemFire components.
	 *
	 * @return a reference to the Spring {@link BeanFactory}
	 * @throws IllegalStateException if the {@link BeanFactory} reference
	 * is null.
	 * @see org.springframework.beans.factory.BeanFactory
	 */
	protected BeanFactory getBeanFactory() {
		Assert.state(this.beanFactory != null, "A reference to the BeanFactory was not properly configured");
		return this.beanFactory;
	}

	/**
	 * Sets the {@link Region} data policy used by the GemFire cache client to manage
	 * Session state.
	 *
	 * @param clientRegionShortcut a {@link ClientRegionShortcut} to specify the client
	 * {@link Region} data management policy.
	 * @see com.gemstone.gemfire.cache.client.ClientRegionShortcut
	 */
	public void setClientRegionShortcut(ClientRegionShortcut clientRegionShortcut) {
		this.clientRegionShortcut = clientRegionShortcut;
	}

	/**
	 * Returns the {@link Region} data policy used by the GemFire cache client to manage
	 * Session state. Defaults to {@link ClientRegionShortcut#PROXY}.
	 *
	 * @return a {@link ClientRegionShortcut} specifying the client {@link Region} data
	 * management policy.
	 * @see org.springframework.session.data.gemfire.config.annotation.web.http.GemFireHttpSessionConfiguration#DEFAULT_CLIENT_REGION_SHORTCUT
	 * @see com.gemstone.gemfire.cache.client.ClientRegionShortcut
	 */
	protected ClientRegionShortcut getClientRegionShortcut() {
		return (this.clientRegionShortcut != null ? this.clientRegionShortcut : DEFAULT_CLIENT_REGION_SHORTCUT);
	}

	/**
	 * Sets a reference to the GemFire cache used to construct the appropriate
	 * {@link Region}.
	 *
	 * @param gemfireCache a reference to the GemFire cache.
	 * @throws IllegalArgumentException if the {@link GemFireCache} reference is null.
	 */
	public void setGemfireCache(GemFireCache gemfireCache) {
		Assert.notNull(gemfireCache, "GemFireCache must not be null");
		this.gemfireCache = gemfireCache;
	}

	/**
	 * Returns a reference to the GemFire cache used to construct the appropriate
	 * {@link Region}.
	 *
	 * @return a reference to the GemFire cache.
	 * @throws IllegalStateException if the {@link GemFireCache} reference is null.
	 */
	protected GemFireCache getGemfireCache() {
		Assert.state(this.gemfireCache != null, "A reference to the GemFireCache was not properly configured");
		return this.gemfireCache;
	}

	/**
	 * Sets the name of the GemFire {@link Pool} used by the client Region for managing Sessions
	 * during cache operations involving the server.
	 *
	 * @param poolName the name of a GemFire {@link Pool}.
	 * @see Pool#getName()
	 */
	public void setPoolName(final String poolName) {
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
	 * Sets the GemFire {@link RegionAttributes} used to configure the GemFire cache
	 * {@link Region} used to store and manage Session state.
	 *
	 * @param regionAttributes the GemFire {@link RegionAttributes} used to configure the
	 * GemFire cache {@link Region}.
	 * @see com.gemstone.gemfire.cache.RegionAttributes
	 */
	public void setRegionAttributes(RegionAttributes<K, V> regionAttributes) {
		this.regionAttributes = regionAttributes;
	}

	/**
	 * Returns the GemFire {@link RegionAttributes} used to configure the GemFire cache
	 * {@link Region} used to store and manage Session state.
	 *
	 * @return the GemFire {@link RegionAttributes} used to configure the GemFire cache
	 * {@link Region}.
	 * @see com.gemstone.gemfire.cache.RegionAttributes
	 */
	protected RegionAttributes<K, V> getRegionAttributes() {
		return this.regionAttributes;
	}

	/**
	 * Sets the name of the GemFire cache {@link Region} use to store and manage Session
	 * state.
	 *
	 * @param regionName a String specifying the name of the GemFire cache {@link Region}.
	 */
	public void setRegionName(final String regionName) {
		this.regionName = regionName;
	}

	/**
	 * Returns the configured name of the GemFire cache {@link Region} use to store and
	 * manage Session state. Defaults to "ClusteredSpringSessions"
	 *
	 * @return a String specifying the name of the GemFire cache {@link Region}.
	 * @see com.gemstone.gemfire.cache.Region#getName()
	 */
	protected String getRegionName() {
		return (StringUtils.hasText(this.regionName) ? this.regionName : DEFAULT_SPRING_SESSION_GEMFIRE_REGION_NAME);
	}

	/**
	 * Sets the {@link Region} data policy used by the GemFire peer cache to manage
	 * Session state.
	 *
	 * @param serverRegionShortcut a {@link RegionShortcut} to specify the peer
	 * {@link Region} data management policy.
	 * @see com.gemstone.gemfire.cache.RegionShortcut
	 */
	public void setServerRegionShortcut(RegionShortcut serverRegionShortcut) {
		this.serverRegionShortcut = serverRegionShortcut;
	}

	/**
	 * Returns the {@link Region} data policy used by the GemFire peer cache to manage
	 * Session state. Defaults to {@link RegionShortcut#PARTITION}.
	 *
	 * @return a {@link RegionShortcut} specifying the peer {@link Region} data management
	 * policy.
	 * @see com.gemstone.gemfire.cache.RegionShortcut
	 */
	protected RegionShortcut getServerRegionShortcut() {
		return (this.serverRegionShortcut != null ? this.serverRegionShortcut : DEFAULT_SERVER_REGION_SHORTCUT);
	}

}
