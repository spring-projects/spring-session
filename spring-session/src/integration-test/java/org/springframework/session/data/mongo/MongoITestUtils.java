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
package org.springframework.session.data.mongo;

import java.io.IOException;

import de.flapdoodle.embed.mongo.MongodExecutable;
import de.flapdoodle.embed.mongo.MongodStarter;
import de.flapdoodle.embed.mongo.config.IMongodConfig;
import de.flapdoodle.embed.mongo.config.MongodConfigBuilder;
import de.flapdoodle.embed.mongo.config.Net;
import de.flapdoodle.embed.mongo.distribution.Version;
import de.flapdoodle.embed.process.runtime.Network;

/**
 * Utility class for Mongo integration tests.
 *
 * @author Vedran Pavic
 */
final class MongoITestUtils {

	private MongoITestUtils() {
	}

	/**
	 * Creates {@link MongodExecutable} for use in integration tests.
	 * @param port the port for embedded Mongo to bind to
	 * @return the {@link MongodExecutable} instance
	 * @throws IOException in case of I/O errors
	 */
	static MongodExecutable embeddedMongoServer(int port) throws IOException {
		IMongodConfig mongodConfig = new MongodConfigBuilder()
				.version(Version.Main.PRODUCTION)
				.net(new Net(port, Network.localhostIsIPv6()))
				.build();
		MongodStarter mongodStarter = MongodStarter.getDefaultInstance();
		return mongodStarter.prepare(mongodConfig);
	}

}
