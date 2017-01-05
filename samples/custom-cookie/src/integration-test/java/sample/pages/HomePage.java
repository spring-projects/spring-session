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
 */
public class HomePage {

	private WebDriver driver;

	@FindBy(name = "attributeName")
	WebElement attributeName;

	@FindBy(name = "attributeValue")
	WebElement attributeValue;

	@FindBy(css = "input[type=\"submit\"]")
	WebElement submit;

	@FindBy(css = "table tbody tr")
	List<WebElement> trs;

	private List<Row> rows;

	public HomePage(WebDriver driver) {
		this.driver = driver;
		this.rows = new ArrayList<Row>();
	}

	private static void get(WebDriver driver, String get) {
		String baseUrl = "http://localhost:" + System.getProperty("tomcat.port", "8080");
		driver.get(baseUrl + get);
	}

	public static HomePage go(WebDriver driver) {
		get(driver, "/");
		return PageFactory.initElements(driver, HomePage.class);
	}

	public void assertAt() {
		assertThat(this.driver.getTitle()).isEqualTo("Session Attributes");
	}

	public void addAttribute(String name, String value) {
		this.attributeName.sendKeys(name);
		this.attributeValue.sendKeys(value);

		this.submit.click();
	}

	public List<Row> attributes() {
		List<Row> rows = new ArrayList<Row>();
		for (WebElement tr : this.trs) {
			rows.add(new Row(tr));
		}
		this.rows.addAll(rows);
		return this.rows;
	}

	public Row row(int index) {
		return this.rows.get(index);
	}

	public static class Row {
		@FindBy(xpath = "//td[1]")
		WebElement attributeName;

		@FindBy(xpath = "//td[2]")
		WebElement attributeValue;

		public Row(SearchContext context) {
			super();
			DefaultElementLocatorFactory factory = new DefaultElementLocatorFactory(
					context);
			PageFactory.initElements(factory, this);
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
