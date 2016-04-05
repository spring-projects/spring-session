package org.springframework.session.data.couchbase.specification

import org.springframework.boot.test.SpringApplicationContextLoader
import org.springframework.session.data.couchbase.BasicSpec
import org.springframework.session.data.couchbase.application.DefaultTestApplication
import org.springframework.session.data.couchbase.application.DifferentNamespaceTestApplication
import org.springframework.session.data.couchbase.application.content.Message
import org.springframework.test.context.ContextConfiguration

import static org.springframework.session.data.couchbase.assertions.Assertions.assertThat

@ContextConfiguration(loader = SpringApplicationContextLoader, classes = DefaultTestApplication)
class NamespacesSpec extends BasicSpec {

    def "Should set and get global HTTP session attribute using the same namespace"() {
        given:
        def message = new Message(text: 'i robot 1', number: 1)
        setGlobalSessionAttribute message
        startApplication()

        when:
        def response = getGlobalSessionAttributeFromExtraInstance()

        then:
        assertThat(response)
                .hasBody(message)
    }

    def "Should set and get HTTP session attribute using the same namespace"() {
        given:
        def message = new Message(text: 'i robot 2', number: 2)
        setSessionAttribute message
        startApplication()

        when:
        def response = getSessionAttributeFromExtraInstance()

        then:
        assertThat(response)
                .hasBody(message)
    }

    def "Should set and get global HTTP session attribute using different namespace"() {
        given:
        def message = new Message(text: 'i robot 3', number: 3)
        setGlobalSessionAttribute message
        startApplication(DifferentNamespaceTestApplication)

        when:
        def response = getGlobalSessionAttributeFromExtraInstance()

        then:
        assertThat(response)
                .hasBody(message)
    }

    def "Should not get HTTP session attribute using different namespace"() {
        given:
        def message = new Message(text: 'i robot 4', number: 4)
        setSessionAttribute message
        startApplication(DifferentNamespaceTestApplication)

        when:
        def response = getSessionAttributeFromExtraInstance()

        then:
        assertThat(response)
                .hasNoBody()
    }

    def "Should set and remove global HTTP session attribute using different namespace"() {
        given:
        def message = new Message(text: 'delete me', number: 71830)
        setGlobalSessionAttribute message
        startApplication(DifferentNamespaceTestApplication)

        when:
        deleteGlobalSessionAttribute()

        then:
        assertThat(getGlobalSessionAttributeFromExtraInstance())
                .hasNoBody()
    }
}