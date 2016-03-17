package samples

import geb.spock.GebSpec
import org.springframework.boot.test.IntegrationTest
import org.springframework.boot.test.SpringApplicationContextLoader
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.web.WebAppConfiguration
import sample.Application
import samples.pages.HomePage
import samples.pages.LoginPage
import samples.pages.SetAttributePage
import spock.lang.Stepwise

/**
 * @author jitendra on 15/3/16.
 */
@Stepwise
@ContextConfiguration(classes = Application, loader = SpringApplicationContextLoader)
@WebAppConfiguration
@IntegrationTest
class HttpRedisJsonTest extends GebSpec {

    def 'login page test'() {
        when:
        to LoginPage
        then:
        at LoginPage
    }

    def "Unauthenticated user sent to login page"() {
        when:
        via HomePage
        then:
        at LoginPage
    }

    def "Successful Login test"() {
        when:
        login()
        then:
        at HomePage
        driver.manage().cookies.find {it.name == "SESSION"}
        !driver.manage().cookies.find {it.name == "JSESSIONID"}
    }

    def "Set and get attributes in session"() {
        when:
        setAttribute("Demo Key", "Demo Value")

        then:
        at SetAttributePage
        tdKey()*.text().contains("Demo Key")
        tdKey()*.text().contains("Demo Value")
    }
}
