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

package org.springframework.session.data.gemfire.config.annotation.web.http.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.springframework.data.gemfire.client.Interest;

import com.gemstone.gemfire.cache.Cache;
import com.gemstone.gemfire.cache.GemFireCache;
import com.gemstone.gemfire.cache.InterestResultPolicy;
import com.gemstone.gemfire.cache.Region;
import com.gemstone.gemfire.cache.RegionAttributes;
import com.gemstone.gemfire.cache.RegionShortcut;
import com.gemstone.gemfire.cache.client.ClientCache;
import com.gemstone.gemfire.cache.client.ClientRegionShortcut;

/**
 * The GemFireCacheTypeAwareRegionFactoryBeanTest class is a test suite of test cases testing the contract
 * and functionality of the GemFireCacheTypeAwareRegionFactoryBean class.
 *
 * @author John Blum
 * @see org.junit.Rule
 * @see org.junit.Test
 * @see org.mockito.Mockito
 * @see org.springframework.session.data.gemfire.config.annotation.web.http.support.GemFireCacheTypeAwareRegionFactoryBean
 * @see com.gemstone.gemfire.cache.Cache
 * @see com.gemstone.gemfire.cache.GemFireCache
 * @see com.gemstone.gemfire.cache.InterestResultPolicy
 * @see com.gemstone.gemfire.cache.Region
 * @see com.gemstone.gemfire.cache.RegionAttributes
 * @see com.gemstone.gemfire.cache.RegionShortcut
 * @see com.gemstone.gemfire.cache.client.ClientCache
 * @see com.gemstone.gemfire.cache.client.ClientRegionShortcut
 * @since 1.0.0
 */
public class GemFireCacheTypeAwareRegionFactoryBeanTest {

	@Rule
	public ExpectedException expectedException = ExpectedException.none();

	private GemFireCacheTypeAwareRegionFactoryBean regionFactoryBean;

	@Before
	public void setup() {
		regionFactoryBean = new GemFireCacheTypeAwareRegionFactoryBean();
	}

	@Test
	public void afterPropertiesSetCreatesClientRegionForClientCache() throws Exception {
		final ClientCache mockClientCache = mock(ClientCache.class);

		final Region mockClientRegion = mock(Region.class, "MockClientRegion");
		final Region mockServerRegion = mock(Region.class, "MockServerRegion");

		regionFactoryBean = new GemFireCacheTypeAwareRegionFactoryBean() {
			@Override protected Region newClientRegion(GemFireCache gemfireCache) throws Exception {
				assertThat(gemfireCache).isSameAs(mockClientCache);
				return mockClientRegion;
			}

			@Override protected Region newServerRegion(final GemFireCache gemfireCache) throws Exception {
				assertThat(gemfireCache).isSameAs(mockClientCache);
				return mockServerRegion;
			}
		};

		regionFactoryBean.setGemfireCache(mockClientCache);
		regionFactoryBean.afterPropertiesSet();

		assertThat(regionFactoryBean.getGemfireCache()).isSameAs(mockClientCache);
		assertThat(regionFactoryBean.getObject()).isEqualTo(mockClientRegion);
	}

	@Test
	public void afterPropertiesSetCreatesServerRegionForPeerCache() throws Exception {
		final Cache mockCache = mock(Cache.class);

		final Region mockClientRegion = mock(Region.class, "MockClientRegion");
		final Region mockServerRegion = mock(Region.class, "MockServerRegion");

		regionFactoryBean = new GemFireCacheTypeAwareRegionFactoryBean() {
			@Override protected Region newClientRegion(GemFireCache gemfireCache) throws Exception {
				assertThat(gemfireCache).isSameAs(mockCache);
				return mockClientRegion;
			}

			@Override protected Region newServerRegion(final GemFireCache gemfireCache) throws Exception {
				assertThat(gemfireCache).isSameAs(mockCache);
				return mockServerRegion;
			}
		};

		regionFactoryBean.setGemfireCache(mockCache);
		regionFactoryBean.afterPropertiesSet();

		assertThat(regionFactoryBean.getGemfireCache()).isSameAs(mockCache);
		assertThat(regionFactoryBean.getObject()).isEqualTo(mockServerRegion);
	}

	@Test
	public void allKeysInterestRegistration() {
		Interest[] interests = regionFactoryBean.registerInterests(true);

		assertThat(interests).isNotNull();
		assertThat(interests.length).isEqualTo(1);
		assertThat(interests[0].isDurable()).isFalse();
		assertThat(interests[0].getKey().toString()).isEqualTo("ALL_KEYS");
		assertThat(interests[0].getPolicy()).isEqualTo(InterestResultPolicy.KEYS);
		assertThat(interests[0].isReceiveValues()).isTrue();
	}

	@Test
	public void emptyInterestsRegistration() {
		Interest[] interests = regionFactoryBean.registerInterests(false);

		assertThat(interests).isNotNull();
		assertThat(interests.length).isEqualTo(0);
	}

	@Test
	@SuppressWarnings("unchecked")
	public void getObjectTypeBeforeInitializationIsRegionClass() {
		assertThat((Class<Region>) regionFactoryBean.getObjectType()).isEqualTo(Region.class);
	}

	@Test
	public void isSingletonIsTrue() {
		assertThat(regionFactoryBean.isSingleton()).isTrue();
	}

	@Test
	public void setAndGetClientRegionShortcut() {
		assertThat(regionFactoryBean.getClientRegionShortcut()).isEqualTo(
			GemFireCacheTypeAwareRegionFactoryBean.DEFAULT_CLIENT_REGION_SHORTCUT);

		regionFactoryBean.setClientRegionShortcut(ClientRegionShortcut.LOCAL_PERSISTENT);

		assertThat(regionFactoryBean.getClientRegionShortcut()).isEqualTo(ClientRegionShortcut.LOCAL_PERSISTENT);

		regionFactoryBean.setClientRegionShortcut(null);

		assertThat(regionFactoryBean.getClientRegionShortcut()).isEqualTo(
			GemFireCacheTypeAwareRegionFactoryBean.DEFAULT_CLIENT_REGION_SHORTCUT);
	}

	@Test
	public void setAndGetGemfireCache() {
		Cache mockCache = mock(Cache.class);

		regionFactoryBean.setGemfireCache(mockCache);

		assertThat(regionFactoryBean.getGemfireCache()).isEqualTo(mockCache);
	}

	@Test
	public void setGemfireCacheToNullThrowsIllegalArgumentException() {
		expectedException.expect(IllegalArgumentException.class);
		expectedException.expectMessage("The GemFireCache reference must not be null");
		regionFactoryBean.setGemfireCache(null);
	}

	@Test
	public void getGemfireCacheWhenNullThrowsIllegalStateException() {
		expectedException.expect(IllegalStateException.class);
		expectedException.expectMessage("A reference to a GemFireCache was not properly configured");
		regionFactoryBean.getGemfireCache();
	}

	@Test
	@SuppressWarnings("unchecked")
	public void setAndGetRegionAttributes() {
		RegionAttributes mockRegionAttributes = mock(RegionAttributes.class);

		assertThat(regionFactoryBean.getRegionAttributes()).isNull();

		regionFactoryBean.setRegionAttributes(mockRegionAttributes);

		assertThat(regionFactoryBean.getRegionAttributes()).isSameAs(mockRegionAttributes);

		regionFactoryBean.setRegionAttributes(null);

		assertThat(regionFactoryBean.getRegionAttributes()).isNull();
	}

	@Test
	public void setAndGetRegionName() {
		assertThat(regionFactoryBean.getRegionName()).isEqualTo(
			GemFireCacheTypeAwareRegionFactoryBean.DEFAULT_SPRING_SESSION_GEMFIRE_REGION_NAME);

		regionFactoryBean.setRegionName("Example");

		assertThat(regionFactoryBean.getRegionName()).isEqualTo("Example");

		regionFactoryBean.setRegionName("  ");

		assertThat(regionFactoryBean.getRegionName()).isEqualTo(
			GemFireCacheTypeAwareRegionFactoryBean.DEFAULT_SPRING_SESSION_GEMFIRE_REGION_NAME);

		regionFactoryBean.setRegionName("");

		assertThat(regionFactoryBean.getRegionName()).isEqualTo(
			GemFireCacheTypeAwareRegionFactoryBean.DEFAULT_SPRING_SESSION_GEMFIRE_REGION_NAME);

		regionFactoryBean.setRegionName(null);

		assertThat(regionFactoryBean.getRegionName()).isEqualTo(
			GemFireCacheTypeAwareRegionFactoryBean.DEFAULT_SPRING_SESSION_GEMFIRE_REGION_NAME);
	}

	@Test
	public void setAndGetServerRegionShortcut() {
		assertThat(regionFactoryBean.getServerRegionShortcut()).isEqualTo(
			GemFireCacheTypeAwareRegionFactoryBean.DEFAULT_SERVER_REGION_SHORTCUT);

		regionFactoryBean.setServerRegionShortcut(RegionShortcut.LOCAL_PERSISTENT);

		assertThat(regionFactoryBean.getServerRegionShortcut()).isEqualTo(RegionShortcut.LOCAL_PERSISTENT);

		regionFactoryBean.setServerRegionShortcut(null);

		assertThat(regionFactoryBean.getServerRegionShortcut()).isEqualTo(
			GemFireCacheTypeAwareRegionFactoryBean.DEFAULT_SERVER_REGION_SHORTCUT);
	}

}
