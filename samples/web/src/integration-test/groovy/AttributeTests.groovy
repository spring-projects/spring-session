
import geb.spock.*
import spock.lang.Stepwise
import pages.*

/**
 * Tests the CAS sample application using service tickets.
 *
 * @author Rob Winch
 */
@Stepwise
class AttributeTests extends GebReportingSpec {
    def 'first visit no attributes'() {
        when:
        to HomePage
        then:
        attributes.empty
    }

    def 'create attribute'() {
        when:
        createAttribute('a','b')
        then:
        attributes.size() == 1
        attributes[0].name == 'a'
        attributes[0].value == 'b'
    }
}