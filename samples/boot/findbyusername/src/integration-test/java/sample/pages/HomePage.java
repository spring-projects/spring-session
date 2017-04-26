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

import java.util.Base64;
import java.util.Set;

import org.openqa.selenium.By;
import org.openqa.selenium.Cookie;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import org.openqa.selenium.support.PageFactory;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Eddú Meléndez
 * @author Rob Winch
 */
public class HomePage extends BasePage {
	@FindBy(css = "input[type=\"submit\"]")
	WebElement logout;

	public HomePage(WebDriver driver) {
		super(driver);
	}

	public void assertAt() {
		assertThat(getDriver().getTitle())
				.isEqualTo("Spring Session Sample - Secured Content");
	}

	public void containCookie(String cookieName) {
		Set<Cookie> cookies = getDriver().manage().getCookies();
		assertThat(cookies).extracting("name").contains(cookieName);
	}

	public void doesNotContainCookie(String cookieName) {
		Set<Cookie> cookies = getDriver().manage().getCookies();
		assertThat(cookies).extracting("name").doesNotContain(cookieName);
	}

	public void terminateButtonDisabled() {
		Set<Cookie> cookies = getDriver().manage().getCookies();
		String cookieValue = null;
		for (Cookie cookie : cookies) {
			if ("SESSION".equals(cookie.getName())) {
				cookieValue = new String(Base64.getDecoder().decode(cookie.getValue()));
			}
		}
		WebElement element = getDriver().findElement(By.id("terminate-" + cookieValue));
		assertThat(element.isEnabled()).isFalse();
	}

	public HomePage logout() {
		this.logout.click();
		return PageFactory.initElements(getDriver(), HomePage.class);
	}

	public static LoginPage go(WebDriver driver) {
		get(driver, "/");
		return PageFactory.initElements(driver, LoginPage.class);
	}
}
