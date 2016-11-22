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

package samples.pages;

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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Eddú Meléndez
 */
public class LoginPage extends BasePage {

	public LoginPage(WebDriver driver) {
		super(driver);
	}

	public void assertAt() {
		assertThat(getDriver().getTitle()).isEqualTo("Spring Session Sample - Login");
	}

	public static LoginPage go(WebDriver driver) {
		get(driver, "/login");
		return PageFactory.initElements(driver, LoginPage.class);
	}

	public HomePage login() {
		WebElement username = getDriver().findElement(By.name("username"));
		WebElement password = getDriver().findElement(By.name("password"));
		WebElement button = getDriver().findElement(By.cssSelector("button[type=\"submit\"]"));

		username.sendKeys("user");
		password.sendKeys("password");
		button.click();
		return PageFactory.initElements(getDriver(), HomePage.class);
	}

	public void addAttribute(String name, String value) {
		WebElement form = getDriver().findElement(By.name("f"));
		WebElement attributeName = form.findElement(By.name("key"));
		WebElement attributeValue = form.findElement(By.name("value"));

		attributeName.sendKeys(name);
		attributeValue.sendKeys(value);

		form.findElement(By.cssSelector("button[type=\"submit\"]")).click();
	}

	public List<Row> attributes() {
		WebElement table = getDriver().findElement(By.tagName("table"));
		List<WebElement> trs = table.findElements(By.tagName("tr"));

		List<Row> rows = new ArrayList<Row>();
		for (WebElement tr : trs) {
			List<WebElement> tds = tr.findElements(By.cssSelector("td"));
			if (!tds.isEmpty()) {
				Row row = Row.builder()
						.driver(getDriver())
						.key(tds.get(0).getText())
						.value(tds.get(1).getText())
						.build();
				rows.add(row);
			}
		}
		return rows;
	}

	@Data
	@Builder
	public static class Row {

		final String key;

		final String value;

		@Getter(AccessLevel.PRIVATE)
		final WebDriver driver;

	}

}
