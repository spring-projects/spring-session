package org.springframework.session.data.couchbase.specification

import org.springframework.boot.test.SpringApplicationContextLoader
import org.springframework.session.data.couchbase.BasicSpec
import org.springframework.session.data.couchbase.application.PrincipalSessionsExpirationTestApplication
import org.springframework.test.context.ContextConfiguration

import static org.springframework.session.data.couchbase.assertions.Assertions.assertThat

@ContextConfiguration(loader = SpringApplicationContextLoader, classes = PrincipalSessionsExpirationTestApplication)
class PrincipalSessionsExpirationSpec extends BasicSpec {

    def "Should not get principal HTTP session when HTTP session have expired"() {
        given:
        setPrincipalSessionAttribute()

        when:
        sleep(2000)

        then:
        assertThat(getPrincipalSessions())
                .hasNoSessionIds()
    }
}