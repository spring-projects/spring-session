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

package sample.pages;

import java.util.Set;

import org.openqa.selenium.By;
import org.openqa.selenium.Cookie;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.PageFactory;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Eddú Meléndez
 */
public class HomePage extends BasePage {

	public HomePage(WebDriver driver) {
		super(driver);
	}

	public static LoginPage go(WebDriver driver) {
		get(driver, "/");
		return PageFactory.initElements(driver, LoginPage.class);
	}

	public void assertAt() {
		assertThat(getDriver().getTitle()).isEqualTo("Spring Session Sample - Secured Content");
	}

	public void containCookie(String cookieName) {
		Set<Cookie> cookies = getDriver().manage().getCookies();
		assertThat(cookies).extracting("name").contains(cookieName);
	}

	public void doesNotContainCookie(String cookieName) {
		Set<Cookie> cookies = getDriver().manage().getCookies();
		assertThat(cookies).extracting("name").doesNotContain(cookieName);
	}

	public HomePage logout() {
		WebElement logout = getDriver().findElement(By.cssSelector("input[type=\"submit\"]"));
		logout.click();
		return PageFactory.initElements(getDriver(), HomePage.class);
	}

}
