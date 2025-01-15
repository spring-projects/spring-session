/*
 * Copyright 2014-2023 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.session.data.redis.config.annotation;

import java.util.Properties;
import java.util.function.Predicate;

import reactor.core.publisher.Mono;
import reactor.util.function.Tuples;

import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.data.redis.connection.ReactiveRedisConnection;
import org.springframework.session.data.redis.config.ConfigureReactiveRedisAction;

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
 * {@link ConfigureReactiveRedisAction#NO_OP} should be exposed.
 * </p>
 *
 * @author Rob Winch
 * @author Mark Paluch
 * @author Marcus da Coregio
 * @since 3.3
 */
public class ConfigureNotifyKeyspaceEventsReactiveAction implements ConfigureReactiveRedisAction {

	static final String CONFIG_NOTIFY_KEYSPACE_EVENTS = "notify-keyspace-events";

	@Override
	public Mono<Void> configure(ReactiveRedisConnection connection) {
		return getNotifyOptions(connection).map((notifyOptions) -> {
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
			return Tuples.of(notifyOptions, customizedNotifyOptions);
		})
			.filter((optionsTuple) -> !optionsTuple.getT1().equals(optionsTuple.getT2()))
			.flatMap((optionsTuple) -> connection.serverCommands()
				.setConfig(CONFIG_NOTIFY_KEYSPACE_EVENTS, optionsTuple.getT2()))
			.filter("OK"::equals)
			.doFinally((unused) -> connection.close())
			.then();
	}

	private Mono<String> getNotifyOptions(ReactiveRedisConnection connection) {
		return connection.serverCommands()
			.getConfig(CONFIG_NOTIFY_KEYSPACE_EVENTS)
			.filter(Predicate.not(Properties::isEmpty))
			.map((config) -> config.getProperty(config.stringPropertyNames().iterator().next()))
			.onErrorMap(InvalidDataAccessApiUsageException.class,
					(ex) -> new IllegalStateException("Unable to configure Reactive Redis to keyspace notifications",
							ex));
	}

}
