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
package sample;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
class EmbeddedMongoPortLogger implements ApplicationRunner, EnvironmentAware {

	private static final Logger logger = LoggerFactory.getLogger(EmbeddedMongoPortLogger.class);

	private Environment environment;

	public void run(ApplicationArguments args) throws Exception {
		String port = this.environment.getProperty("local.mongo.port");
		logger.info("Embedded Mongo started on port " + port +
				", use 'mongo --port " + port + "' command to connect");
	}

	public void setEnvironment(Environment environment) {
		this.environment = environment;
	}

}
