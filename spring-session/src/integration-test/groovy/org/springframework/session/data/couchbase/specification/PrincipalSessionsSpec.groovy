package org.springframework.session.data.couchbase.specification

import org.springframework.boot.test.SpringApplicationContextLoader
import org.springframework.session.data.couchbase.BasicSpec
import org.springframework.session.data.couchbase.application.PrincipalSessionsTestApplication
import org.springframework.test.context.ContextConfiguration

import static org.springframework.session.data.couchbase.assertions.Assertions.assertThat

@ContextConfiguration(loader = SpringApplicationContextLoader, classes = PrincipalSessionsTestApplication)
class PrincipalSessionsSpec extends BasicSpec {

    def "Should get principal HTTP sessions when they exist"() {
        given:
        def firstSessionId = setPrincipalSessionAttribute()
        clearSessionCookie()
        startApplication PrincipalSessionsTestApplication
        def secondSessionId = setPrincipalSessionAttributeToExtraInstance()

        when:
        def response = getPrincipalSessions()

        then:
        assertThat(response)
                .hasSessionIds(firstSessionId, secondSessionId)
    }

    def "Should not get principal HTTP session when it was invalidated"() {
        given:
        setPrincipalSessionAttribute()

        when:
        invalidateSession()

        then:
        assertThat(getPrincipalSessions())
                .hasNoSessionIds()
    }
}