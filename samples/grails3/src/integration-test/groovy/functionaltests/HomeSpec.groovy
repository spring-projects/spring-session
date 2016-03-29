/*
 * Copyright 2014-2016 the original author or authors.
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

import grails.test.mixin.integration.Integration
import grails.transaction.Transactional
import org.springframework.boot.test.IntegrationTest

import spock.lang.*
import geb.spock.*

import sample.pages.HomePage
import sample.pages.LoginPage
import sample.pages.IndexPage
import spock.lang.Stepwise
import pages.*

/**
 * Functional tests for grails 3 and spring-session
 *
 * @author Eric Helgeson
 */

@Stepwise
@IntegrationTest("server.port:0")
@Integration(applicationClass=grails3.redis.session.Application)
class HomeSpec extends GebSpec {

    def setup() {
    }

    def cleanup() {
    }

    void 'Anonymous page not redirected to login'() {
        when: 'The index page is visited'
        go '/'

        then: 'Not redirected'
        at IndexPage
    }

    void 'Unauthenticated user sent to log in page'() {
        when: 'The test page is visited'
        go '/test/index'
        if(title != 'Login') {
          println driver.pageSource
        }

        then: 'The password form is correct'
        title == 'Login'
        $('#password')
        $('#username')
    }

    void 'Log in views home page'() {
      when: 'log in successfully'
      to LoginPage
      login()

      then: 'sent to original page'
      at HomePage

      and: 'the username is displayed'
      username == 'user'

      and: 'session id is not blank'
      session != ''

      and: 'Spring Session Management is being used'
      driver.manage().cookies.find { it.name == 'SESSION' }

      and: 'Standard Session is NOT being used'
      !driver.manage().cookies.find { it.name == 'JSESSIONID' }
    }

    def 'Log out success'() {
      when:
      logout()

      then:
      at IndexPage
    }
}
