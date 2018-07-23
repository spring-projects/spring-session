/*
 * Copyright 2014-2018 the original author or authors.
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

package org.springframework.session.data.redis.config;

import java.util.Properties;

import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.data.redis.connection.RedisConnection;

/**
 * <p>
 * Ensures that Redis Keyspace events for Generic commands and Expired events are enabled.
 * For example, it might set the following:
 * </p>
 *
 * <pre>
 * config set notify-keyspace-events Egx
 * </pre>
 *
 * <p>
 * This strategy will not work if the Redis instance has been properly secured. Instead,
 * the Redis instance should be configured externally and a Bean of type
 * {@link ConfigureRedisAction#NO_OP} should be exposed.
 * </p>
 *
 * @author Rob Winch
 * @author Mark Paluch
 * @since 1.0.1
 */
public class ConfigureNotifyKeyspaceEventsAction implements ConfigureRedisAction {

	static final String CONFIG_NOTIFY_KEYSPACE_EVENTS = "notify-keyspace-events";

	/*
	 * (non-Javadoc)
	 *
	 * @see
	 * org.springframework.session.data.redis.config.ConfigureRedisAction#configure(org.
	 * springframework.data.redis.connection.RedisConnection)
	 */
	@Override
	public void configure(RedisConnection connection) {
		String notifyOptions = getNotifyOptions(connection);
		String customizedNotifyOptions = notifyOptions;
		if (!customizedNotifyOptions.contains("E")) {
			customizedNotifyOptions += "E";
		}
		boolean A = customizedNotifyOptions.contains("A");
		if (!(A || customizedNotifyOptions.contains("g"))) {
			customizedNotifyOptions += "g";
		}
		if (!(A || customizedNotifyOptions.contains("x"))) {
			customizedNotifyOptions += "x";
		}
		if (!notifyOptions.equals(customizedNotifyOptions)) {
			connection.setConfig(CONFIG_NOTIFY_KEYSPACE_EVENTS, customizedNotifyOptions);
		}
	}

	private String getNotifyOptions(RedisConnection connection) {
		try {
			Properties config = connection.getConfig(CONFIG_NOTIFY_KEYSPACE_EVENTS);
			if (config.isEmpty()) {
				return "";
			}
			return config.getProperty(config.stringPropertyNames().iterator().next());
		}
		catch (InvalidDataAccessApiUsageException ex) {
			throw new IllegalStateException(
					"Unable to configure Redis to keyspace notifications. See http://docs.spring.io/spring-session/docs/current/reference/html5/#api-redisoperationssessionrepository-sessiondestroyedevent",
					ex);
		}
	}

}
