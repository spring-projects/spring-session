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

package sample;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.htmlunit.HtmlUnitDriver;
import sample.pages.HomePage;
import sample.pages.LoginPage;

/**
 * @author Eddú Meléndez
 * @author Rob Winch
 */
public class HazelcastSpringTests {

	private WebDriver driver;

	@Before
	public void setup() {
		this.driver = new HtmlUnitDriver();
	}

	@After
	public void tearDown() {
		this.driver.quit();
	}

	@Test
	public void goHomeRedirectLoginPage() {
		LoginPage login = HomePage.go(this.driver);
		login.assertAt();
	}

	@Test
	public void login() {
		LoginPage login = HomePage.go(this.driver);
		login.assertAt();
		HomePage home = login.form().login(HomePage.class);
		home.containCookie("SESSION");
		home.doesNotContainCookie("JSESSIONID");
	}

}
