/*
 * Copyright 2014-2022 the original author or authors.
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

package org.springframework.session.mongodb.examples;

import java.util.HashMap;
import java.util.Map;

import org.testcontainers.containers.MongoDBContainer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.session.data.mongo.config.annotation.web.reactive.EnableMongoWebSession;

/**
 * Pure Spring-based application (using Spring Boot for dependency management), hence no
 * autoconfiguration.
 *
 * @author Rob Winch
 * @author Greg Turnquist
 */
@SpringBootApplication
@EnableMongoWebSession
public class SpringSessionMongoReactiveApplication {

	public static void main(String[] args) {
		SpringApplication application = new SpringApplication(SpringSessionMongoReactiveApplication.class);
		application.addInitializers(new Initializer());
		application.run(args);
	}

	/**
	 * Use Testcontainers to managed MongoDB through Docker.
	 * <p>
	 *
	 * @see <a href=
	 * "https://bsideup.github.io/posts/local_development_with_testcontainers/">Local
	 * Development with Testcontainers</a>
	 */
	static class Initializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

		static MongoDBContainer mongo = new MongoDBContainer("mongo:5.0.11");

		private static Map<String, String> getProperties() {
			mongo.start();

			HashMap<String, String> properties = new HashMap<>();
			properties.put("spring.data.mongodb.host", mongo.getHost());
			properties.put("spring.data.mongodb.port", mongo.getFirstMappedPort() + "");
			return properties;
		}

		@Override
		public void initialize(ConfigurableApplicationContext context) {
			ConfigurableEnvironment env = context.getEnvironment();
			env.getPropertySources().addFirst(new MapPropertySource("testcontainers", (Map) getProperties()));
		}

	}

}
