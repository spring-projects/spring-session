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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.openqa.selenium.WebDriver;
import org.testcontainers.containers.GenericContainer;
import sample.pages.HomePage;
import sample.pages.LoginPage;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.htmlunit.webdriver.MockMvcHtmlUnitDriverBuilder;

/**
 * @author Eddú Meléndez
 * @author Rob Winch
 * @author Vedran Pavic
 */
@ExtendWith(SpringExtension.class)
@AutoConfigureMockMvc
@SpringBootTest(webEnvironment = WebEnvironment.MOCK)
class FindByUsernameTests {

	private static final String DOCKER_IMAGE = "redis:5.0.14";

	@Autowired
	private MockMvc mockMvc;

	private WebDriver driver;

	private WebDriver driver2;

	@BeforeEach
	void setup() {
		this.driver = MockMvcHtmlUnitDriverBuilder.mockMvcSetup(this.mockMvc).build();
	}

	@AfterEach
	void tearDown() {
		this.driver.quit();
		if (this.driver2 != null) {
			this.driver2.quit();
		}
	}

	@Test
	void home() {
		LoginPage login = HomePage.go(this.driver);
		login.assertAt();
	}

	@Test
	void login() {
		LoginPage login = HomePage.go(this.driver);
		HomePage home = login.form().login(HomePage.class);
		home.assertAt();
		home.containCookie("SESSION");
		home.doesNotContainCookie("JSESSIONID");
		home.terminateButtonDisabled();
	}

	@Test
	void terminateOtherSession() throws Exception {
		HomePage forgotToLogout = home(this.driver);

		this.driver2 = MockMvcHtmlUnitDriverBuilder.mockMvcSetup(this.mockMvc).build();
		HomePage terminateFogotSession = home(this.driver2);
		terminateFogotSession.terminateSession(forgotToLogout.getSessionId()).assertAt();

		LoginPage login = HomePage.go(this.driver);
		login.assertAt();
	}

	private static HomePage home(WebDriver driver) {
		LoginPage login = HomePage.go(driver);
		HomePage home = login.form().login(HomePage.class);
		home.assertAt();
		return home;
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
			return new LettuceConnectionFactory(redisContainer().getHost(), redisContainer().getFirstMappedPort());
		}

	}

}
