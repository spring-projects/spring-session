package sample

import geb.spock.*
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.IntegrationTest
import org.springframework.boot.test.SpringApplicationConfiguration
import org.springframework.boot.test.SpringApplicationContextLoader
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.web.WebAppConfiguration
import sample.pages.HomePage
import sample.pages.LoginPage
import spock.lang.Stepwise
import pages.*

/**
 * Tests the demo that supports multiple sessions
 *
 * @author Rob Winch
 */
@Stepwise
@ContextConfiguration(classes = Application, loader = SpringApplicationContextLoader)
@WebAppConfiguration
@IntegrationTest
class BootTests extends GebReportingSpec {

    def 'Unauthenticated user sent to log in page'() {
        when: 'unauthenticated user request protected page'
        via HomePage
        then: 'sent to the log in page'
        at LoginPage
    }

    def 'Log in views home page'() {
        when: 'log in successfully'
        login()
        then: 'sent to original page'
        at HomePage
        and: 'the username is displayed'
        username == 'user'
        and: 'Spring Session Management is being used'
        driver.manage().cookies.find { it.name == 'SESSION' }
        and: 'Standard Session is NOT being used'
        !driver.manage().cookies.find { it.name == 'JSESSIONID' }
    }

    def 'Log out success'() {
        when:
        logout()
        then:
        at LoginPage
    }

    def 'Logged out user sent to log in page'() {
        when: 'logged out user request protected page'
        via HomePage
        then: 'sent to the log in page'
        at LoginPage
    }
}