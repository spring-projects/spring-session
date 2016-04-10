package org.springframework.session.data.couchbase.specification

import org.springframework.boot.test.SpringApplicationContextLoader
import org.springframework.session.data.couchbase.BasicSpec
import org.springframework.session.data.couchbase.application.ExpirationTestApplication
import org.springframework.session.data.couchbase.application.content.Message
import org.springframework.test.context.ContextConfiguration

import static org.springframework.session.data.couchbase.application.ExpirationTestApplication.SESSION_TIMEOUT
import static org.springframework.session.data.couchbase.assertions.Assertions.assertThat

@ContextConfiguration(loader = SpringApplicationContextLoader, classes = ExpirationTestApplication)
class SessionExpirationSpec extends BasicSpec {

    def "Should not get HTTP session attribute when session has expired"() {
        given:
        def message = new Message(text: 'power rangers', number: 123)
        setSessionAttribute message
        sleep(SESSION_TIMEOUT * 1000 + 100)

        when:
        def response = getSessionAttribute()

        then:
        assertThat(response)
                .hasNoBody()
    }

    def "Should not get Couchbase session document when HTTP session has expired"() {
        given:
        def message = new Message(text: 'power rangers 2', number: 10001)
        setSessionAttribute message

        when:
        sleep(SESSION_TIMEOUT * 1000 + 1000)

        then:
        !currentSessionExists()
    }
}