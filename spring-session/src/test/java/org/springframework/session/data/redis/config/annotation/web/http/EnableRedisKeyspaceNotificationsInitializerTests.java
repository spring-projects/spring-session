/*
 * Copyright 2002-2015 the original author or authors.
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
package org.springframework.session.data.redis.config.annotation.web.http;

import static org.fest.assertions.Assertions.assertThat;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.session.data.redis.config.annotation.web.http.RedisHttpSessionConfiguration.*;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.session.data.redis.config.ConfigureNotifyKeyspaceEventsAction;

import java.util.Arrays;

@RunWith(MockitoJUnitRunner.class)
public class EnableRedisKeyspaceNotificationsInitializerTests {
	static final String CONFIG_NOTIFY_KEYSPACE_EVENTS = "notify-keyspace-events";

	@Mock
	RedisConnectionFactory connectionFactory;
	@Mock
	RedisConnection connection;
	@Captor
	ArgumentCaptor<String> options;

	EnableRedisKeyspaceNotificationsInitializer initializer;

	@Before
	public void setup() {
		when(connectionFactory.getConnection()).thenReturn(connection);

		initializer = new EnableRedisKeyspaceNotificationsInitializer(connectionFactory, new ConfigureNotifyKeyspaceEventsAction());
	}

	@Test
	public void afterPropertiesSetUnset() throws Exception {
		setConfigNotification("");

		initializer.afterPropertiesSet();

		assertOptionsContains("E","g","x");
	}

	@Test
	public void afterPropertiesSetA() throws Exception {
		setConfigNotification("A");

		initializer.afterPropertiesSet();

		assertOptionsContains("A", "E");
	}

	@Test
	public void afterPropertiesSetE() throws Exception {
		setConfigNotification("E");

		initializer.afterPropertiesSet();

		assertOptionsContains("E", "g", "x");
	}

	@Test
	public void afterPropertiesSetK() throws Exception {
		setConfigNotification("K");

		initializer.afterPropertiesSet();

		assertOptionsContains("K", "E", "g", "x");
	}

	@Test
	public void afterPropertiesSetAE() throws Exception {
		setConfigNotification("AE");

		initializer.afterPropertiesSet();

		verify(connection, never()).setConfig(anyString(), anyString());
	}

	@Test
	public void afterPropertiesSetAK() throws Exception {
		setConfigNotification("AK");

		initializer.afterPropertiesSet();

		assertOptionsContains("A", "K", "E");
	}

	@Test
	public void afterPropertiesSetEK() throws Exception {
		setConfigNotification("EK");

		initializer.afterPropertiesSet();

		assertOptionsContains("E", "K", "g", "x");
	}

	@Test
	public void afterPropertiesSetEg() throws Exception {
		setConfigNotification("Eg");

		initializer.afterPropertiesSet();

		assertOptionsContains("E", "g", "x");
	}

	@Test
	public void afterPropertiesSetE$() throws Exception {
		setConfigNotification("E$");

		initializer.afterPropertiesSet();

		assertOptionsContains("E", "$", "g", "x");
	}

	@Test
	public void afterPropertiesSetKg() throws Exception {
		setConfigNotification("Kg");

		initializer.afterPropertiesSet();

		assertOptionsContains("K", "g", "E", "x");
	}

	@Test
	public void afterPropertiesSetAEK() throws Exception {
		setConfigNotification("AEK");

		initializer.afterPropertiesSet();

		verify(connection, never()).setConfig(anyString(), anyString());
	}

	private void assertOptionsContains(String... expectedValues) {
		verify(connection).setConfig(eq(CONFIG_NOTIFY_KEYSPACE_EVENTS), options.capture());
		for(String expectedValue : expectedValues) {
			assertThat(options.getValue()).contains(expectedValue);
		}
		assertThat(options.getValue().length()).isEqualTo(expectedValues.length);
	}

	private void setConfigNotification(String value) {
		when(connection.getConfig(CONFIG_NOTIFY_KEYSPACE_EVENTS)).thenReturn(Arrays.asList(CONFIG_NOTIFY_KEYSPACE_EVENTS, value));
	}
}