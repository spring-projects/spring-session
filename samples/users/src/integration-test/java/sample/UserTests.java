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
import org.openqa.selenium.WebElement;
import org.openqa.selenium.htmlunit.HtmlUnitDriver;
import sample.pages.HomePage;
import sample.pages.LinkPage;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Pool Dolorier
 */
public class UserTests {

	private static final String ROB = "rob";
	private static final String USERNAME = "username";
	private static final String LUKE = "luke";
	private static final String NAV_LINK = "navLink";
	private static final String HREF = "href";
	private static final String ADD_ACCOUNT = "addAccount";
	private static final String UN = "un";
	private static final String LOGOUT = "logout";
	private static final String ERROR = "error";

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
	public void firstVisitNotAuthenticated() {
		HomePage homePage = HomePage.go(this.driver);
		homePage.assertAt();
		homePage.assertUserNameEmpty();
	}

	@Test
	public void invalidLogin() {
		HomePage homePage = HomePage.go(this.driver);
		String user = ROB;
		homePage.login(user, user + "invalid");
		WebElement errorMessage = homePage.getElementById(ERROR);
		homePage.assertAt();
		homePage.assertErrorInvalidAuthentication(errorMessage);
	}

	@Test
	public void emptyUsername() {
		HomePage homePage = HomePage.go(this.driver);
		homePage.login("", "");
		WebElement errorMessage = homePage.getElementById(ERROR);
		homePage.assertAt();
		homePage.assertErrorInvalidAuthentication(errorMessage);
	}

	@Test
	public void loginSingleUser() {
		loginRob();
	}

	@Test
	public void addAccount() {
		backHomeForAddLukeAccount();
	}

	@Test
	public void logInSecondUser() {
		logInLukeAccount();
	}

	@Test
	public void followingLinksKeepsNewSession() {
		followingLukeLinkSession();

	}

	@Test
	public void switchAccountRob() {
		switchAccountRobHomePage();
	}

	@Test
	public void followingLinksKeepsOriginalSession() {
		followingRobLinkSession();
	}

	@Test
	public void switchAccountLuke() {
		switchAccountLukeHomePage();
	}

	@Test
	public void logoutLuke() {
		logoutLukeAccount();
	}

	@Test
	public void switchBackRob() {
		switchBackRobHomePage();
	}

	@Test
	public void logoutRob() {
		logoutRobAccount();
	}

	private HomePage loginRob() {
		HomePage home = HomePage.go(this.driver);

		String user = ROB;
		home.login(user, user);
		WebElement username = home.getElementById(UN);
		assertThat(username.getText()).isEqualTo(user);
		return home;
	}

	private HomePage backHomeForAddLukeAccount() {
		HomePage robHome = loginRob();

		String addAccountLink = robHome.getContentAttributeByElementId(ADD_ACCOUNT, HREF);
		HomePage backHome = robHome.home(this.driver, addAccountLink);
		WebElement username = backHome.getElementById(USERNAME);
		assertThat(username.getText()).isEmpty();
		return backHome;
	}

	private HomePage logInLukeAccount() {
		HomePage home = backHomeForAddLukeAccount();

		String secondUser = LUKE;
		home.login(secondUser, secondUser);
		WebElement secondUserName = home.getElementById(UN);
		assertThat(secondUserName.getText()).isEqualTo(secondUser);
		return home;
	}

	private LinkPage followingLukeLinkSession() {
		HomePage lukeHome = logInLukeAccount();

		String navLink = lukeHome.getContentAttributeByElementId(NAV_LINK, HREF);
		LinkPage lukeLinkPage = lukeHome.linkPage(this.driver, navLink);
		lukeLinkPage.assertAt();
		WebElement username = lukeLinkPage.getElementById(UN);
		assertThat(username.getText()).isEqualTo(LUKE);
		return lukeLinkPage;
	}

	private HomePage switchAccountRobHomePage() {
		LinkPage lukeLinkPage = followingLukeLinkSession();

		String robSwitch = lukeLinkPage.getSwitchElementId(ROB);
		String switchLink = lukeLinkPage.getContentAttributeByElementId(robSwitch, HREF);
		HomePage robHome = lukeLinkPage.home(this.driver, switchLink);
		WebElement username = robHome.getElementById(UN);
		assertThat(username.getText()).isEqualTo(ROB);
		return robHome;
	}

	private LinkPage followingRobLinkSession() {
		HomePage robHome = switchAccountRobHomePage();

		String navLink = robHome.getContentAttributeByElementId(NAV_LINK, HREF);
		LinkPage robLinkPage = robHome.linkPage(this.driver, navLink);
		robLinkPage.assertAt();
		WebElement username = robLinkPage.getElementById(UN);
		assertThat(username.getText()).isEqualTo(ROB);
		return robLinkPage;
	}

	private HomePage switchAccountLukeHomePage() {
		LinkPage robLinkPage = followingRobLinkSession();

		String lukeSwitch = robLinkPage.getSwitchElementId(LUKE);
		String lukeSwitchLink = robLinkPage.getContentAttributeByElementId(lukeSwitch, HREF);
		HomePage lukeHome = robLinkPage.home(this.driver, lukeSwitchLink);
		WebElement username = lukeHome.getElementById(UN);
		assertThat(username.getText()).isEqualTo(LUKE);
		return lukeHome;
	}

	private HomePage logoutLukeAccount() {
		HomePage lukeHome = switchAccountLukeHomePage();

		String logoutLink = lukeHome.getContentAttributeByElementId(LOGOUT, HREF);
		HomePage home = lukeHome.home(this.driver, logoutLink);
		home.assertUserNameEmpty();
		return home;
	}

	private HomePage switchBackRobHomePage() {
		HomePage homePage = logoutLukeAccount();

		String robSwitch = homePage.getSwitchElementId(ROB);
		String robSwitchLink = homePage.getContentAttributeByElementId(robSwitch, HREF);
		HomePage robHome = homePage.home(this.driver, robSwitchLink);
		WebElement username = robHome.getElementById(UN);
		assertThat(username.getText()).isEqualTo(ROB);
		return robHome;
	}

	private HomePage logoutRobAccount() {
		HomePage robHome = switchBackRobHomePage();

		String logoutLink = robHome.getContentAttributeByElementId(LOGOUT, HREF);
		HomePage home = robHome.home(this.driver, logoutLink);
		home.assertUserNameEmpty();
		return home;
	}
}
