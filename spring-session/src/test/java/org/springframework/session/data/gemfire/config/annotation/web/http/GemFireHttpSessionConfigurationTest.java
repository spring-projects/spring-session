/*
 * Copyright 2014-2017 the original author or authors.
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

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import org.apache.geode.cache.Cache;
import org.apache.geode.cache.ExpirationAction;
import org.apache.geode.cache.ExpirationAttributes;
import org.apache.geode.cache.GemFireCache;
import org.apache.geode.cache.Region;
import org.apache.geode.cache.RegionAttributes;
import org.apache.geode.cache.RegionShortcut;
import org.apache.geode.cache.client.ClientCache;
import org.apache.geode.cache.client.ClientRegionShortcut;
import org.junit.Before;
import org.junit.Test;

import org.springframework.core.type.AnnotationMetadata;
import org.springframework.data.gemfire.GemfireOperations;
import org.springframework.data.gemfire.GemfireTemplate;
import org.springframework.data.gemfire.RegionAttributesFactoryBean;
import org.springframework.session.ExpiringSession;
import org.springframework.session.data.gemfire.GemFireOperationsSessionRepository;
import org.springframework.session.data.gemfire.config.annotation.web.http.support.GemFireCacheTypeAwareRegionFactoryBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

/**
 * The GemFireHttpSessionConfigurationTest class is a test suite of test cases testing the
 * contract and functionality of the {@link GemFireHttpSessionConfiguration} class.
 *
 * @author John Blum
 * @since 1.1.0
 * @see org.junit.Test
 * @see org.mockito.Mockito
 * @see org.springframework.data.gemfire.GemfireOperations
 * @see org.springframework.data.gemfire.GemfireTemplate
 * @see org.springframework.session.data.gemfire.GemFireOperationsSessionRepository
 * @see org.springframework.session.data.gemfire.config.annotation.web.http.GemFireHttpSessionConfiguration
 * @see org.apache.geode.cache.Cache
 * @see org.apache.geode.cache.GemFireCache
 * @see org.apache.geode.cache.Region
 * @see org.apache.geode.cache.client.ClientCache
 */
public class GemFireHttpSessionConfigurationTest {

	private GemFireHttpSessionConfiguration gemfireConfiguration;

	@SuppressWarnings("unchecked")
	protected <T> T getField(Object obj, String fieldName) {
		try {
			Field field = obj.getClass().getDeclaredField(fieldName);
			field.setAccessible(true);
			return (T) field.get(obj);
		}
		catch (NoSuchFieldException e) {
			throw new IllegalArgumentException(String.format(
				"field with name [%1$s] was not found in class [%2$s]", fieldName, obj), e);
		}
		catch (IllegalAccessException e) {
			throw new Error(String.format("unable to access field [%1$s] on object of type [%2$s]",
				fieldName, obj.getClass().getName()), e);
		}
	}

	protected <T> T[] toArray(T... array) {
		return array;
	}

	@Before
	public void setup() {
		this.gemfireConfiguration = new GemFireHttpSessionConfiguration();
	}

	@Test
	public void setAndGetBeanClassLoader() {
		assertThat(this.gemfireConfiguration.getBeanClassLoader()).isNull();

		this.gemfireConfiguration.setBeanClassLoader(Thread.currentThread().getContextClassLoader());

		assertThat(this.gemfireConfiguration.getBeanClassLoader()).isEqualTo(
			Thread.currentThread().getContextClassLoader());

		this.gemfireConfiguration.setBeanClassLoader(null);

		assertThat(this.gemfireConfiguration.getBeanClassLoader()).isNull();
	}

	@Test
	public void setAndGetClientRegionShortcut() {
		assertThat(this.gemfireConfiguration.getClientRegionShortcut()).isEqualTo(
			GemFireHttpSessionConfiguration.DEFAULT_CLIENT_REGION_SHORTCUT);

		this.gemfireConfiguration.setClientRegionShortcut(ClientRegionShortcut.CACHING_PROXY);

		assertThat(this.gemfireConfiguration.getClientRegionShortcut()).isEqualTo(
			ClientRegionShortcut.CACHING_PROXY);

		this.gemfireConfiguration.setClientRegionShortcut(null);

		assertThat(this.gemfireConfiguration.getClientRegionShortcut()).isEqualTo(
			GemFireHttpSessionConfiguration.DEFAULT_CLIENT_REGION_SHORTCUT);
	}

	@Test
	public void setAndGetMaxInactiveIntervalInSeconds() {
		assertThat(this.gemfireConfiguration.getMaxInactiveIntervalInSeconds()).isEqualTo(
				GemFireHttpSessionConfiguration.DEFAULT_MAX_INACTIVE_INTERVAL_IN_SECONDS);

		this.gemfireConfiguration.setMaxInactiveIntervalInSeconds(300);

		assertThat(this.gemfireConfiguration.getMaxInactiveIntervalInSeconds()).isEqualTo(300);

		this.gemfireConfiguration.setMaxInactiveIntervalInSeconds(Integer.MAX_VALUE);

		assertThat(this.gemfireConfiguration.getMaxInactiveIntervalInSeconds()).isEqualTo(Integer.MAX_VALUE);

		this.gemfireConfiguration.setMaxInactiveIntervalInSeconds(-1);

		assertThat(this.gemfireConfiguration.getMaxInactiveIntervalInSeconds()).isEqualTo(-1);

		this.gemfireConfiguration.setMaxInactiveIntervalInSeconds(Integer.MIN_VALUE);

		assertThat(this.gemfireConfiguration.getMaxInactiveIntervalInSeconds()).isEqualTo(Integer.MIN_VALUE);
	}

	@Test
	public void setAndGetPoolName() {
		assertThat(this.gemfireConfiguration.getPoolName()).isEqualTo(
			GemFireHttpSessionConfiguration.DEFAULT_GEMFIRE_POOL_NAME);

		this.gemfireConfiguration.setPoolName("TestPoolName");

		assertThat(this.gemfireConfiguration.getPoolName()).isEqualTo("TestPoolName");

		this.gemfireConfiguration.setPoolName("  ");

		assertThat(this.gemfireConfiguration.getPoolName()).isEqualTo(
			GemFireHttpSessionConfiguration.DEFAULT_GEMFIRE_POOL_NAME);

		this.gemfireConfiguration.setPoolName("");

		assertThat(this.gemfireConfiguration.getPoolName()).isEqualTo(
			GemFireHttpSessionConfiguration.DEFAULT_GEMFIRE_POOL_NAME);

		this.gemfireConfiguration.setPoolName(null);

		assertThat(this.gemfireConfiguration.getPoolName()).isEqualTo(
			GemFireHttpSessionConfiguration.DEFAULT_GEMFIRE_POOL_NAME);
	}

	@Test
	public void setAndGetServerRegionShortcut() {
		assertThat(this.gemfireConfiguration.getServerRegionShortcut()).isEqualTo(
			GemFireHttpSessionConfiguration.DEFAULT_SERVER_REGION_SHORTCUT);

		this.gemfireConfiguration.setServerRegionShortcut(RegionShortcut.REPLICATE_PERSISTENT);

		assertThat(this.gemfireConfiguration.getServerRegionShortcut()).isEqualTo(
			RegionShortcut.REPLICATE_PERSISTENT);

		this.gemfireConfiguration.setServerRegionShortcut(null);

		assertThat(this.gemfireConfiguration.getServerRegionShortcut()).isEqualTo(
			GemFireHttpSessionConfiguration.DEFAULT_SERVER_REGION_SHORTCUT);
	}

	@Test
	public void setAndGetSpringSessionGemFireRegionName() {
		assertThat(this.gemfireConfiguration.getSpringSessionGemFireRegionName()).isEqualTo(
			GemFireHttpSessionConfiguration.DEFAULT_SPRING_SESSION_GEMFIRE_REGION_NAME);

		this.gemfireConfiguration.setSpringSessionGemFireRegionName("test");

		assertThat(this.gemfireConfiguration.getSpringSessionGemFireRegionName()).isEqualTo("test");

		this.gemfireConfiguration.setSpringSessionGemFireRegionName("  ");

		assertThat(this.gemfireConfiguration.getSpringSessionGemFireRegionName()).isEqualTo(
			GemFireHttpSessionConfiguration.DEFAULT_SPRING_SESSION_GEMFIRE_REGION_NAME);

		this.gemfireConfiguration.setSpringSessionGemFireRegionName("");

		assertThat(this.gemfireConfiguration.getSpringSessionGemFireRegionName()).isEqualTo(
			GemFireHttpSessionConfiguration.DEFAULT_SPRING_SESSION_GEMFIRE_REGION_NAME);

		this.gemfireConfiguration.setSpringSessionGemFireRegionName(null);

		assertThat(this.gemfireConfiguration.getSpringSessionGemFireRegionName()).isEqualTo(
			GemFireHttpSessionConfiguration.DEFAULT_SPRING_SESSION_GEMFIRE_REGION_NAME);
	}

	@Test
	public void setsImportMetadata() {
		AnnotationMetadata mockAnnotationMetadata = mock(AnnotationMetadata.class);

		Map<String, Object> annotationAttributes = new HashMap<String, Object>(4);

		annotationAttributes.put("clientRegionShortcut", ClientRegionShortcut.CACHING_PROXY);
		annotationAttributes.put("indexableSessionAttributes", toArray("one", "two", "three"));
		annotationAttributes.put("maxInactiveIntervalInSeconds", 600);
		annotationAttributes.put("poolName", "TestPool");
		annotationAttributes.put("serverRegionShortcut", RegionShortcut.REPLICATE);
		annotationAttributes.put("regionName", "TEST");

		given(mockAnnotationMetadata.getAnnotationAttributes(eq(EnableGemFireHttpSession.class.getName())))
			.willReturn(annotationAttributes);

		this.gemfireConfiguration.setImportMetadata(mockAnnotationMetadata);

		assertThat(this.gemfireConfiguration.getClientRegionShortcut()).isEqualTo(ClientRegionShortcut.CACHING_PROXY);
		assertThat(this.gemfireConfiguration.getIndexableSessionAttributes()).isEqualTo(toArray("one", "two", "three"));
		assertThat(this.gemfireConfiguration.getMaxInactiveIntervalInSeconds()).isEqualTo(600);
		assertThat(this.gemfireConfiguration.getPoolName()).isEqualTo("TestPool");
		assertThat(this.gemfireConfiguration.getServerRegionShortcut()).isEqualTo(RegionShortcut.REPLICATE);
		assertThat(this.gemfireConfiguration.getSpringSessionGemFireRegionName()).isEqualTo("TEST");

		verify(mockAnnotationMetadata, times(1)).getAnnotationAttributes(eq(EnableGemFireHttpSession.class.getName()));
	}

	@Test
	public void createsAndInitializesSessionRepositoryBean() {
		GemfireOperations mockGemfireOperations = mock(GemfireOperations.class);

		this.gemfireConfiguration.setMaxInactiveIntervalInSeconds(120);

		GemFireOperationsSessionRepository sessionRepository = this.gemfireConfiguration.sessionRepository(
			mockGemfireOperations);

		assertThat(sessionRepository).isNotNull();
		assertThat(sessionRepository.getTemplate()).isSameAs(mockGemfireOperations);
		assertThat(sessionRepository.getMaxInactiveIntervalInSeconds()).isEqualTo(120);
	}

	@Test
	@SuppressWarnings("unchecked")
	public void createsAndInitializesSessionRegionTemplateBean() {
		GemFireCache mockGemFireCache = mock(GemFireCache.class);
		Region<Object, Object> mockRegion = mock(Region.class);

		given(mockGemFireCache.getRegion(eq("Example"))).willReturn(mockRegion);

		this.gemfireConfiguration.setSpringSessionGemFireRegionName("Example");

		GemfireTemplate template = this.gemfireConfiguration.sessionRegionTemplate(mockGemFireCache);

		assertThat(this.gemfireConfiguration.getSpringSessionGemFireRegionName()).isEqualTo("Example");
		assertThat(template).isNotNull();
		assertThat(template.getRegion()).isSameAs(mockRegion);

		verify(mockGemFireCache, times(1)).getRegion(eq("Example"));
	}

	@Test
	@SuppressWarnings("unchecked")
	public void createsAndInitializesSessionRegionBean() {
		GemFireCache mockGemFireCache = mock(GemFireCache.class);
		RegionAttributes<Object, ExpiringSession> mockRegionAttributes = mock(RegionAttributes.class);

		this.gemfireConfiguration.setClientRegionShortcut(ClientRegionShortcut.CACHING_PROXY);
		this.gemfireConfiguration.setPoolName("TestPool");
		this.gemfireConfiguration.setServerRegionShortcut(RegionShortcut.REPLICATE_PERSISTENT);
		this.gemfireConfiguration.setSpringSessionGemFireRegionName("TestRegion");

		GemFireCacheTypeAwareRegionFactoryBean<Object, ExpiringSession> sessionRegionFactoryBean =
			this.gemfireConfiguration.sessionRegion(mockGemFireCache, mockRegionAttributes);

		assertThat(sessionRegionFactoryBean).isNotNull();
		assertThat(getField(sessionRegionFactoryBean, "clientRegionShortcut")).isEqualTo(
			ClientRegionShortcut.CACHING_PROXY);
		assertThat(getField(sessionRegionFactoryBean, "gemfireCache")).isEqualTo(mockGemFireCache);
		assertThat(getField(sessionRegionFactoryBean, "poolName")).isEqualTo("TestPool");
		assertThat(getField(sessionRegionFactoryBean, "regionAttributes")).isEqualTo(mockRegionAttributes);
		assertThat(getField(sessionRegionFactoryBean, "regionName")).isEqualTo("TestRegion");
		assertThat(getField(sessionRegionFactoryBean, "serverRegionShortcut")).isEqualTo(
			RegionShortcut.REPLICATE_PERSISTENT);

		verifyZeroInteractions(mockGemFireCache);
		verifyZeroInteractions(mockRegionAttributes);
	}

	@Test
	@SuppressWarnings("unchecked")
	public void createsAndInitializesSessionRegionAttributesWithExpiration() throws Exception {
		Cache mockCache = mock(Cache.class);

		this.gemfireConfiguration.setMaxInactiveIntervalInSeconds(300);
		this.gemfireConfiguration.setServerRegionShortcut(RegionShortcut.LOCAL);

		RegionAttributesFactoryBean regionAttributesFactory =
			this.gemfireConfiguration.sessionRegionAttributes(mockCache);

		assertThat(regionAttributesFactory).isNotNull();

		regionAttributesFactory.afterPropertiesSet();

		RegionAttributes<Object, ExpiringSession> sessionRegionAttributes = regionAttributesFactory.getObject();

		assertThat(sessionRegionAttributes).isNotNull();
		assertThat(sessionRegionAttributes.getKeyConstraint()).isEqualTo(
			GemFireHttpSessionConfiguration.SPRING_SESSION_GEMFIRE_REGION_KEY_CONSTRAINT);
		assertThat(sessionRegionAttributes.getValueConstraint()).isEqualTo(
			GemFireHttpSessionConfiguration.SPRING_SESSION_GEMFIRE_REGION_VALUE_CONSTRAINT);

		ExpirationAttributes entryIdleTimeoutExpiration = sessionRegionAttributes.getEntryIdleTimeout();

		assertThat(entryIdleTimeoutExpiration).isNotNull();
		assertThat(entryIdleTimeoutExpiration.getAction()).isEqualTo(ExpirationAction.INVALIDATE);
		assertThat(entryIdleTimeoutExpiration.getTimeout()).isEqualTo(300);
	}

	@Test
	@SuppressWarnings("unchecked")
	public void createsAndInitializesSessionRegionAttributesWithoutExpiration() throws Exception {
		ClientCache mockClientCache = mock(ClientCache.class);

		this.gemfireConfiguration.setMaxInactiveIntervalInSeconds(300);

		RegionAttributesFactoryBean regionAttributesFactory =
			this.gemfireConfiguration.sessionRegionAttributes(mockClientCache);

		assertThat(regionAttributesFactory).isNotNull();

		regionAttributesFactory.afterPropertiesSet();

		RegionAttributes<Object, ExpiringSession> sessionRegionAttributes = regionAttributesFactory.getObject();

		assertThat(sessionRegionAttributes).isNotNull();
		assertThat(sessionRegionAttributes.getKeyConstraint()).isEqualTo(
			GemFireHttpSessionConfiguration.SPRING_SESSION_GEMFIRE_REGION_KEY_CONSTRAINT);
		assertThat(sessionRegionAttributes.getValueConstraint()).isEqualTo(
			GemFireHttpSessionConfiguration.SPRING_SESSION_GEMFIRE_REGION_VALUE_CONSTRAINT);

		ExpirationAttributes entryIdleTimeoutExpiration = sessionRegionAttributes.getEntryIdleTimeout();

		assertThat(entryIdleTimeoutExpiration).isNotNull();
		assertThat(entryIdleTimeoutExpiration.getAction()).isEqualTo(ExpirationAction.INVALIDATE);
		assertThat(entryIdleTimeoutExpiration.getTimeout()).isEqualTo(0);
	}

	@Test
	public void expirationIsAllowed() {
		Cache mockCache = mock(Cache.class);

		ClientCache mockClientCache = mock(ClientCache.class);

		this.gemfireConfiguration.setClientRegionShortcut(ClientRegionShortcut.PROXY);
		this.gemfireConfiguration.setServerRegionShortcut(RegionShortcut.REPLICATE);

		assertThat(this.gemfireConfiguration.isExpirationAllowed(mockCache)).isTrue();

		this.gemfireConfiguration.setServerRegionShortcut(RegionShortcut.PARTITION_REDUNDANT_PERSISTENT_OVERFLOW);

		assertThat(this.gemfireConfiguration.isExpirationAllowed(mockCache)).isTrue();

		this.gemfireConfiguration.setClientRegionShortcut(ClientRegionShortcut.CACHING_PROXY);
		this.gemfireConfiguration.setServerRegionShortcut(RegionShortcut.PARTITION_PROXY);

		assertThat(this.gemfireConfiguration.isExpirationAllowed(mockClientCache)).isTrue();

		this.gemfireConfiguration.setClientRegionShortcut(ClientRegionShortcut.LOCAL_PERSISTENT_OVERFLOW);
		this.gemfireConfiguration.setServerRegionShortcut(RegionShortcut.REPLICATE_PROXY);

		assertThat(this.gemfireConfiguration.isExpirationAllowed(mockClientCache)).isTrue();
	}

	@Test
	public void expirationIsNotAllowed() {
		Cache mockCache = mock(Cache.class);
		ClientCache mockClientCache = mock(ClientCache.class);

		this.gemfireConfiguration.setClientRegionShortcut(ClientRegionShortcut.PROXY);
		this.gemfireConfiguration.setServerRegionShortcut(RegionShortcut.PARTITION);

		assertThat(this.gemfireConfiguration.isExpirationAllowed(mockClientCache)).isFalse();

		this.gemfireConfiguration.setClientRegionShortcut(ClientRegionShortcut.LOCAL);
		this.gemfireConfiguration.setServerRegionShortcut(RegionShortcut.PARTITION_PROXY);

		assertThat(this.gemfireConfiguration.isExpirationAllowed(mockCache)).isFalse();
	}
}
