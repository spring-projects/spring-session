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

import org.openqa.selenium.SearchContext;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import org.openqa.selenium.support.PageFactory;
import org.openqa.selenium.support.pagefactory.DefaultElementLocatorFactory;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Eddú Meléndez
 * @author Rob Winch
 */
public class LoginPage extends BasePage {

	public LoginPage(WebDriver driver) {
		super(driver);
	}

	public void assertAt() {
		assertThat(getDriver().getTitle()).isEqualTo("Spring Session Sample - Log In");
	}

	public Form form() {
		return new Form(getDriver());
	}

	public class Form {

		@FindBy(name = "username")
		private WebElement username;

		@FindBy(name = "password")
		private WebElement password;

		@FindBy(tagName = "button")
		private WebElement button;

		public Form(SearchContext context) {
			PageFactory.initElements(new DefaultElementLocatorFactory(context), this);
		}

		public <T> T login(Class<T> page) {
			this.username.sendKeys("user");
			this.password.sendKeys("password");
			this.button.click();
			return PageFactory.initElements(getDriver(), page);
		}
	}
}
