package org.example.persistentsession;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.WebDriver;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.htmlunit.webdriver.MockMvcHtmlUnitDriverBuilder;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class SpringSessionSamplePersistentSessionApplicationTests {

	@Autowired
	MockMvc mockMvc;

	WebDriver driver;

	@BeforeEach
	void setup() {
		this.driver = MockMvcHtmlUnitDriverBuilder.mockMvcSetup(this.mockMvc).build();
	}

	@AfterEach
	void tearDown() {
		this.driver.quit();
	}

	@Test
	void loginWhenSuccessThenCookie() {
		HomePage homePage = login();
		homePage.assertAt();
		homePage.containCookie("SESSION");
	}

	@Test
	void loginAndLogoutThenRemoveCookie() {
		HomePage homePage = login();
		homePage.assertAt();
		homePage.containCookie("SESSION");
		LoginPage loginPage = homePage.logout();
		loginPage.assertAt();
		loginPage.doesNotContainCookie("SESSION");
	}

	private HomePage login() {
		LoginPage loginPage = HomePage.go(this.driver);
		return loginPage.form().login(HomePage.class);
	}

}
