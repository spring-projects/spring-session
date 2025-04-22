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

import com.redis.testcontainers.RedisContainer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.openqa.selenium.WebDriver;
import sample.pages.HomePage;
import sample.pages.HomePage.Attribute;
import sample.pages.LoginPage;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.htmlunit.webdriver.MockMvcHtmlUnitDriverBuilder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Eddú Meléndez
 * @author Vedran Pavic
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = WebEnvironment.MOCK)
@AutoConfigureMockMvc
class HttpRedisJsonTest {

	private static final String DOCKER_IMAGE = "redis:7.0.4-alpine";

	@Autowired
	private MockMvc mockMvc;

	private WebDriver driver;

	@BeforeEach
	void setup() {
		this.driver = MockMvcHtmlUnitDriverBuilder.mockMvcSetup(this.mockMvc).build();
	}

	@AfterEach
	void tearDown() {
		this.driver.quit();
	}

	@Test
	void goLoginRedirectToLogin() {
		LoginPage login = HomePage.go(this.driver, LoginPage.class);
		login.assertAt();
	}

	@Test
	void goHomeRedirectLoginPage() {
		LoginPage login = HomePage.go(this.driver, LoginPage.class);
		login.assertAt();
	}

	@Test
	void login() {
		LoginPage login = HomePage.go(this.driver, LoginPage.class);
		HomePage home = login.form().login(HomePage.class);
		home.containCookie("SESSION");
		home.doesNotContainCookie("JSESSIONID");
	}

	@Test
	void createAttribute() {
		LoginPage login = HomePage.go(this.driver, LoginPage.class);
		HomePage home = login.form().login(HomePage.class);
		// @formatter:off
		home = home.form()
				.attributeName("Demo Key")
				.attributeValue("Demo Value")
				.submit(HomePage.class);
		// @formatter:on

		List<Attribute> attributes = home.attributes();
		assertThat(attributes).extracting("attributeName").contains("Demo Key");
		assertThat(attributes).extracting("attributeValue").contains("Demo Value");
	}

	@TestConfiguration
	static class Config {

		@Bean
		@ServiceConnection
		RedisContainer redisContainer() {
			return new RedisContainer(DOCKER_IMAGE);
		}

	}

}
