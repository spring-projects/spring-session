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

package sample.pages;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import org.openqa.selenium.support.PageFactory;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Pool Dolorier
 */
public class LoginPage extends BasePage {

	@FindBy(name = "username")
	private WebElement username;

	@FindBy(name = "password")
	private WebElement password;

	@FindBy(tagName = "button")
	private WebElement button;

	public LoginPage(WebDriver driver) {
		super(driver);
	}

	public static LoginPage go(WebDriver driver) {
		get(driver, "/");
		return PageFactory.initElements(driver, LoginPage.class);
	}

	public void assertAt() {
		assertThat(getDriver().getTitle()).isEqualTo("Please sign in");
	}

	public HomePage login(String user, String password) {
		this.username.sendKeys(user);
		this.password.sendKeys(password);
		this.button.click();
		return  HomePage.go(getDriver());
	}
}
