/*
 * Copyright 2014-2017 the original author or authors.
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

package samples;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.openqa.selenium.WebDriver;
import sample.Application;
import samples.pages.HomePage;
import samples.pages.LoginPage;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootContextLoader;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.htmlunit.webdriver.MockMvcHtmlUnitDriverBuilder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Eddú Meléndez
 */
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ContextConfiguration(classes = Application.class, loader = SpringBootContextLoader.class)
public class HttpRedisJsonTest {

	@Autowired
	private MockMvc mockMvc;

	private WebDriver driver;

	@Before
	public void setup() {
		this.driver = MockMvcHtmlUnitDriverBuilder
				.mockMvcSetup(this.mockMvc)
				.build();
	}

	@After
	public void tearDown() {
		this.driver.quit();
	}

	@Test
	public void goLoginRedirectToLogin() {
		LoginPage login = LoginPage.go(this.driver);
		login.assertAt();
	}

	@Test
	public void goHomeRedirectLoginPage() {
		LoginPage login = HomePage.go(this.driver);
		login.assertAt();
	}

	@Test
	public void login() {
		LoginPage login = LoginPage.go(this.driver);
		HomePage home = login.login();
		home.containCookie("SESSION");
		home.doesNotContainCookie("JSESSIONID");
	}

	@Test
	public void createAttribute() {
		LoginPage login = LoginPage.go(this.driver);
		login.login();
		login.addAttribute("Demo Key", "Demo Value");
		assertThat(login.attributes()).extracting("key").contains("Demo Key");
		assertThat(login.attributes()).extracting("value").contains("Demo Value");
	}

}
