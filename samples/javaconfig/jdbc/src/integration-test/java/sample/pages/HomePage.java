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

import java.util.ArrayList;
import java.util.List;

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
public class HomePage {

	private WebDriver driver;

	@FindBy(css = "form")
	WebElement form;

	@FindBy(css = "table tbody tr")
	List<WebElement> trs;

	List<Attribute> attributes;

	public HomePage(WebDriver driver) {
		this.driver = driver;
		this.attributes = new ArrayList<>();
	}

	private static void get(WebDriver driver, String get) {
		String baseUrl = "http://localhost:" + System.getProperty("app.port", "8080");
		driver.get(baseUrl + get);
	}

	public static HomePage go(WebDriver driver) {
		get(driver, "/");
		return PageFactory.initElements(driver, HomePage.class);
	}

	public void assertAt() {
		assertThat(this.driver.getTitle()).isEqualTo("Session Attributes");
	}

	public List<Attribute> attributes() {
		List<Attribute> rows = new ArrayList<>();
		for (WebElement tr : this.trs) {
			rows.add(new Attribute(tr));
		}
		this.attributes.addAll(rows);
		return this.attributes;
	}

	public Form form() {
		return new Form(this.form);
	}

	public class Form {
		@FindBy(name = "attributeName")
		WebElement attributeName;

		@FindBy(name = "attributeValue")
		WebElement attributeValue;

		@FindBy(css = "input[type=\"submit\"]")
		WebElement submit;

		public Form(SearchContext context) {
			PageFactory.initElements(new DefaultElementLocatorFactory(context), this);
		}

		public Form attributeName(String text) {
			this.attributeName.sendKeys(text);
			return this;
		}

		public Form attributeValue(String text) {
			this.attributeValue.sendKeys(text);
			return this;
		}

		public <T> T submit(Class<T> page) {
			this.submit.click();
			return PageFactory.initElements(HomePage.this.driver, page);
		}
	}

	public static class Attribute {
		@FindBy(xpath = ".//td[1]")
		WebElement attributeName;

		@FindBy(xpath = ".//td[2]")
		WebElement attributeValue;

		public Attribute(SearchContext context) {
			PageFactory.initElements(new DefaultElementLocatorFactory(context), this);
		}

		/**
		 * @return the attributeName
		 */
		public String getAttributeName() {
			return this.attributeName.getText();
		}

		/**
		 * @return the attributeValue
		 */
		public String getAttributeValue() {
			return this.attributeValue.getText();
		}
	}

}
