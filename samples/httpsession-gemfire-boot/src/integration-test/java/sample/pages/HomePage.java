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

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.PageFactory;

/**
 * @author Eddú Meléndez
 */
public class HomePage {

	private WebDriver driver;

	private List<Row> rows;

	public HomePage(WebDriver driver) {
		this.driver = driver;
		this.rows = new ArrayList<Row>();
	}

	private static void get(WebDriver driver, String get) {
		String baseUrl = "http://localhost";
		driver.get(baseUrl + get);
	}

	public static HomePage go(WebDriver driver) {
		get(driver, "/");
		return PageFactory.initElements(driver, HomePage.class);
	}

	public void addAttribute(String name, String value) {
		WebElement form = this.driver.findElement(By.tagName("form"));
		WebElement attributeName = form.findElement(By.name("attributeName"));
		WebElement attributeValue = form.findElement(By.name("attributeValue"));

		attributeName.sendKeys(name);
		attributeValue.sendKeys(value);

		form.findElement(By.cssSelector("input[type=\"submit\"]")).click();
	}

	public List<Row> attributes() {
		WebElement table = this.driver.findElement(By.tagName("table"));
		WebElement tbody = table.findElement(By.tagName("tbody"));
		List<WebElement> trs = tbody.findElements(By.tagName("tr"));

		List<Row> rows = new ArrayList<Row>();
		for (WebElement tr : trs) {
			List<WebElement> tds = tr.findElements(By.cssSelector("td"));
			Row row = Row.builder()
					.driver(this.driver)
					.attributeName(tds.get(0).getText())
					.attributeValue(tds.get(1).getText())
					.build();
			rows.add(row);
		}
		this.rows.addAll(rows);
		return this.rows;
	}

	public Row row(int index) {
		return this.rows.get(index);
	}

	@Data
	@Builder
	public static class Row {

		final String attributeName;

		final String attributeValue;

		@Getter(AccessLevel.PRIVATE)
		final WebDriver driver;

	}

}
