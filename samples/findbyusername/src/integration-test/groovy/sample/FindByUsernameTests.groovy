/*
 * Copyright 2002-2015 the original author or authors.
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
package sample

import geb.Browser
import geb.spock.*

import org.openqa.selenium.htmlunit.HtmlUnitDriver
import org.springframework.boot.test.IntegrationTest
import org.springframework.boot.test.SpringApplicationContextLoader
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.web.WebAppConfiguration

import pages.*
import sample.pages.HomePage
import sample.pages.LoginPage
import spock.lang.Stepwise

/**
 * Tests findbyusername web application
 *
 * @author Rob Winch
 */
@Stepwise
@ContextConfiguration(classes = FindByUsernameApplication, loader = SpringApplicationContextLoader)
@WebAppConfiguration
@IntegrationTest
class FindByUsernameTests extends MultiBrowserGebSpec {
	def 'Unauthenticated user sent to log in page'() {
		given: 'unauthenticated user request protected page'
		withBrowserSession a, {
			via HomePage
			then: 'sent to the log in page'
			at LoginPage
		}
	}

	def 'Log in views home page'() {
		when: 'log in successfully'
		a.login()
		then: 'sent to original page'
		a.at HomePage
		and: 'the username is displayed'
		a.username == 'user'
		and: 'Spring Session Management is being used'
		a.driver.manage().cookies.find { it.name == 'SESSION' }
		and: 'Standard Session is NOT being used'
		!a.driver.manage().cookies.find { it.name == 'JSESSIONID' }
		and: 'Session id exists and terminate disabled'
		a.sessionId
		a.terminate(a.sessionId).@disabled
	}

	def 'Other Browser: Unauthenticated user sent to log in page'() {
		given:
		withBrowserSession b, {
			via HomePage
			then: 'sent to the log in page'
			at LoginPage
		}
	}

	def 'Other Browser: Log in views home page'() {
		when: 'log in successfully'
		b.login()
		then: 'sent to original page'
		b.at HomePage
		and: 'the username is displayed'
		b.username == 'user'
		and: 'Spring Session Management is being used'
		b.driver.manage().cookies.find { it.name == 'SESSION' }
		and: 'Standard Session is NOT being used'
		!b.driver.manage().cookies.find { it.name == 'JSESSIONID' }
		and: 'Session id exists and terminate disabled'
		b.sessionId
		b.terminate(b.sessionId).@disabled
	}

	def 'Other Browser: Terminate Original'() {
		setup: 'get the session id from browser a'
		def sessionId = a.sessionId
		when: 'we terminate browser a session'
		b.terminate(sessionId).click(HomePage)
		then: 'session is not listed'
		!b.terminate(sessionId)
		when: 'browser a visits home page after session was terminated'
		a.via HomePage
		then: 'browser a sent to log in'
		a.at LoginPage
	}
}