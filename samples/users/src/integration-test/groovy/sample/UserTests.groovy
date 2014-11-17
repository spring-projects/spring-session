package sample

import geb.spock.*
import sample.pages.HomePage;
import sample.pages.LinkPage;
import spock.lang.Stepwise
import pages.*

/**
 * Tests the demo that supports multiple sessions
 *
 * @author Rob Winch
 */
@Stepwise
class UserTests extends GebReportingSpec {
    def 'first visit not authenticated'() {
        when:
        to HomePage
        then:
        form
        !username
    }

    def 'invalid login'() {
        setup:
        def user = 'rob'
        when:
        login(user, user+'invalid')
        then:
        !username
        error == 'Invalid username / password. Please ensure the username is the same as the password.'
    }

    def 'empty username'() {
        setup:
        def user = ''
        when:
        login(user, user)
        then:
        !username
        error == 'Invalid username / password. Please ensure the username is the same as the password.'
    }

    def 'login single user'() {
        setup:
        def user = 'rob'
        when:
        login(user, user)
        then:
        username == user
    }

    def 'add account'() {
        when:
        addAccount.click(HomePage)
        then:
        form
        !username
    }

    def 'log in second user'() {
        setup:
        def user = 'luke'
        when:
        login(user, user)
        then:
        username == user
    }

    def 'following links keeps new session'() {
        when:
        navLink.click(LinkPage)
        then:
        username == 'luke'
    }

    def 'switch account rob'() {
        setup:
        def user = 'rob'
        when:
        switchAccount(user)
        then:
        username == user
    }

    def 'following links keeps original session'() {
        when:
        navLink.click(LinkPage)
        then:
        username == 'rob'
    }

    def 'switch account luke'() {
        setup:
        def user = 'luke'
        when:
        switchAccount(user)
        then:
        username == user
    }

    def 'logout luke'() {
        when:
        logout.click(HomePage)
        then:
        !username
    }

    def 'switch back rob'() {
        setup:
        def user = 'rob'
        when:
        switchAccount(user)
        then:
        username == user
    }

    def 'logout rob'() {
        when:
        logout.click(HomePage)
        then:
        !username
    }
}