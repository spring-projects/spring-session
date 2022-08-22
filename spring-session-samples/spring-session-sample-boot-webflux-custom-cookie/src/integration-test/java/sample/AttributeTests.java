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

package sample;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.htmlunit.HtmlUnitDriver;
import org.testcontainers.containers.GenericContainer;
import sample.pages.HomePage;
import sample.pages.HomePage.Attribute;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Eleftheria Stein
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class AttributeTests {

	private static final String DOCKER_IMAGE = "redis:7.0.4-alpine";

	@LocalServerPort
	private int port;

	private WebDriver driver;

	@BeforeEach
	void setup() {
		this.driver = new HtmlUnitDriver();
	}

	@AfterEach
	void tearDown() {
		this.driver.quit();
	}

	@Test
	void home() {
		HomePage home = HomePage.go(this.driver, this.port);
		home.assertAt();
	}

	@Test
	void noAttributes() {
		HomePage home = HomePage.go(this.driver, this.port);
		assertThat(home.attributes()).isEmpty();
	}

	@Test
	void createAttribute() {
		HomePage home = HomePage.go(this.driver, this.port);
		// @formatter:off
		home = home.form()
				.attributeName("a")
				.attributeValue("b")
				.submit(HomePage.class);
		// @formatter:on

		List<Attribute> attributes = home.attributes();
		assertThat(attributes).hasSize(1);
		Attribute row = attributes.get(0);
		assertThat(row.getAttributeName()).isEqualTo("a");
		assertThat(row.getAttributeValue()).isEqualTo("b");
	}

	@TestConfiguration
	static class Config {

		@Bean
		GenericContainer redisContainer() {
			GenericContainer redisContainer = new GenericContainer(DOCKER_IMAGE).withExposedPorts(6379);
			redisContainer.start();
			return redisContainer;
		}

		@Bean
		LettuceConnectionFactory redisConnectionFactory() {
			return new LettuceConnectionFactory(redisContainer().getContainerIpAddress(),
					redisContainer().getFirstMappedPort());
		}

	}

}
