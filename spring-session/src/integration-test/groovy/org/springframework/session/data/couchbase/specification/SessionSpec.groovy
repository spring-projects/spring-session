package org.springframework.session.data.couchbase.specification

import org.springframework.boot.test.SpringApplicationContextLoader
import org.springframework.session.data.couchbase.BasicSpec
import org.springframework.session.data.couchbase.application.DefaultTestApplication
import org.springframework.session.data.couchbase.application.DifferentNamespaceTestApplication
import org.springframework.session.data.couchbase.application.content.Message
import org.springframework.test.context.ContextConfiguration
import org.springframework.web.client.HttpServerErrorException

import static org.springframework.session.data.couchbase.assertions.Assertions.assertThat

@ContextConfiguration(loader = SpringApplicationContextLoader, classes = DefaultTestApplication)
class SessionSpec extends BasicSpec {

    def "Should set and get HTTP session attribute"() {
        given:
        def message = new Message(text: 'flying sausage', number: 666)
        setSessionAttribute message

        when:
        def response = getSessionAttribute()

        then:
        assertThat(response)
                .hasBody(message)
    }

    def "Should set and remove HTTP session attribute"() {
        given:
        def message = new Message(text: 'twisted mind', number: 100)
        setSessionAttribute message

        when:
        deleteSessionAttribute()

        then:
        assertThat(getSessionAttribute())
                .hasNoBody()
    }

    def "Should not get HTTP session attribute when session was invalidated"() {
        given:
        def message = new Message(text: 'godzilla', number: 13)
        setSessionAttribute message

        when:
        invalidateSession()

        then:
        assertThat(getSessionAttribute())
                .hasNoBody()
    }

    def "Should set and get HTTP session scoped bean"() {
        given:
        def message = new Message(text: 'i am batman', number: 69)
        setSessionBean message

        when:
        def response = getSessionBean()

        then:
        assertThat(response)
                .hasBody(message)
    }

    def "Should not get HTTP session scoped bean when session was invalidated"() {
        given:
        def message = new Message(text: 'mariusz kopylec', number: 1)
        setSessionBean message
        invalidateSession()

        when:
        def response = getSessionBean()

        then:
        assertThat(response)
                .hasBody(new Message(text: null, number: null))
    }

    def "Should copy HTTP session attributes when session ID was changed"() {
        given:
        def message = new Message(text: 'i cannot disappear!', number: 13)
        setSessionAttribute message
        def globalMessage = new Message(text: 'i cannot disappear too!', number: 12222)
        setGlobalSessionAttribute globalMessage
        startApplication(DifferentNamespaceTestApplication)
        def extraMessage = new Message(text: 'and me too!', number: 14100)
        setSessionAttributeToExtraInstance extraMessage

        when:
        changeSessionId()

        then:
        assertThat(getSessionAttribute())
                .hasBody(message)
        assertThat(getGlobalSessionAttributeFromExtraInstance())
                .hasBody(globalMessage)
        assertThat(getSessionAttributeFromExtraInstance())
                .hasBody(extraMessage)
    }

    def "Should fail to get principal HTTP session when principal HTTP sessions are disabled"() {
        given:
        setPrincipalSessionAttribute()

        when:
        getPrincipalSessions()

        then:
        thrown HttpServerErrorException
    }

    def "Should get global and application namespace HTTP session attribute names"() {
        given:
        def message = new Message(text: 'how do you do', number: 10)
        setGlobalSessionAttribute message
        setSessionAttribute message

        when:
        def response = getSessionAttributeNames()

        then:
        assertThat(response)
                .hasSessionAttributeNames(
                'attribute',
                'org.springframework.session.data.couchbase.CouchbaseSession.global.attribute',
                'org.springframework.session.data.couchbase.CouchbaseSession.global.$lastAccessedTime',
                'org.springframework.session.data.couchbase.CouchbaseSession.global.$creationTime',
                'org.springframework.session.data.couchbase.CouchbaseSession.global.$maxInactiveInterval'
        )
    }
}