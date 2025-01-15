/*
 * Copyright 2014-2024 the original author or authors.
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

package com.example;

import java.util.ArrayList;
import java.util.List;

import org.openqa.selenium.SearchContext;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import org.openqa.selenium.support.PageFactory;
import org.openqa.selenium.support.pagefactory.DefaultElementLocatorFactory;

import static org.assertj.core.api.Assertions.assertThat;

public class HomePage {

	private WebDriver driver;

	@FindBy(css = "table tbody tr")
	List<WebElement> trs;

	List<Attribute> attributes;

	public HomePage(WebDriver driver) {
		this.driver = driver;
		this.attributes = new ArrayList<>();
	}

	private static void get(WebDriver driver, int port, String get) {
		String baseUrl = "http://localhost:" + port;
		driver.get(baseUrl + get);
	}

	public static LoginPage go(WebDriver driver, int port) {
		get(driver, port, "/");
		return PageFactory.initElements(driver, LoginPage.class);
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
