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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Eddú Meléndez
 */
public class AttributeTests {

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
	public void noAttributes() {
		HomePage home = HomePage.go(this.driver, HomePage.class);
		assertThat(home.attributes().size()).isEqualTo(0);
	}

	@Test
	public void createAttribute() {
		HomePage home = HomePage.go(this.driver, HomePage.class);
		home = home.form()
				.attributeName("a")
				.attributeValue("b")
				.submit(HomePage.class);
		assertThat(home.attributes()).extracting("attributeName").containsOnly("a");
		assertThat(home.attributes()).extracting("attributeValue").containsOnly("b");
	}

}
