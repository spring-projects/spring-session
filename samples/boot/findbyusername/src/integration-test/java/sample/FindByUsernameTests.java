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

package sample;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
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
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.htmlunit.webdriver.MockMvcHtmlUnitDriverBuilder;

/**
 * @author Eddú Meléndez
 * @author Rob Winch
 * @author Vedran Pavic
 */
@RunWith(SpringRunner.class)
@AutoConfigureMockMvc
@SpringBootTest(webEnvironment = WebEnvironment.MOCK)
public class FindByUsernameTests {

	private static final String DOCKER_IMAGE = "redis:4.0.11";

	@Autowired
	private MockMvc mockMvc;

	private WebDriver driver;

	@Before
	public void setup() {
		this.driver = MockMvcHtmlUnitDriverBuilder.mockMvcSetup(this.mockMvc).build();
	}

	@After
	public void tearDown() {
		this.driver.quit();
	}

	@Test
	public void home() {
		LoginPage login = HomePage.go(this.driver);
		login.assertAt();
	}

	@Test
	public void login() {
		LoginPage login = HomePage.go(this.driver);
		HomePage home = login.form().login(HomePage.class);
		home.assertAt();
		home.containCookie("SESSION");
		home.doesNotContainCookie("JSESSIONID");
		home.terminateButtonDisabled();
	}

	@TestConfiguration
	static class Config {

		@Bean
		public GenericContainer redisContainer() {
			GenericContainer redisContainer = new GenericContainer(DOCKER_IMAGE)
					.withExposedPorts(6379);
			redisContainer.start();
			return redisContainer;
		}

		@Bean
		public LettuceConnectionFactory redisConnectionFactory() {
			return new LettuceConnectionFactory(redisContainer().getContainerIpAddress(),
					redisContainer().getFirstMappedPort());
		}

	}

}
