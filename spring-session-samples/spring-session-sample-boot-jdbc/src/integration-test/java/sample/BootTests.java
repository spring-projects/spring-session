/*
 * Copyright 2014-present the original author or authors.
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
import sample.pages.HomePage;
import sample.pages.LoginPage;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.htmlunit.webdriver.MockMvcHtmlUnitDriverBuilder;

/**
 * @author Eddú Meléndez
 * @author Vedran Pavic
 */
@ExtendWith(SpringExtension.class)
@AutoConfigureMockMvc
@SpringBootTest(webEnvironment = WebEnvironment.MOCK)
class BootTests {

	@Autowired
	private MockMvc mockMvc;

	private WebDriver driver;

	@BeforeEach
	void setup() {
		this.driver = MockMvcHtmlUnitDriverBuilder.mockMvcSetup(this.mockMvc).build();
	}

	@AfterEach
	void tearDown() {
		if (this.driver != null) {
			this.driver.quit();
		}
	}

	@Test
	void home() {
		LoginPage login = HomePage.go(this.driver);
		login.assertAt();
		HomePage home = login.form().login(HomePage.class);
		home.assertAt();
	}

	@Test
	void login() {
		LoginPage login = HomePage.go(this.driver);
		HomePage home = login.form().login(HomePage.class);
		home.containCookie("SESSION");
		home.doesNotContainCookie("JSESSIONID");
	}

	@Test
	void logout() {
		LoginPage login = HomePage.go(this.driver);
		HomePage home = login.form().login(HomePage.class);
		login = home.logout();
		login.assertAt();
	}

}
