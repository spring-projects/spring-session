package sample.pages

import geb.*

/**
 * The Links Page
 *
 * @author Rob Winch
 */
class LinkPage extends Page {
    static url = ''
    static at = { assert driver.title == 'Linked Page'; true}
    static content = {
        form { $('#navLinks') }
        username(required:false) { $('#un').text() }
        switchAccount{ un ->
            $("#switchAccount${un}").click(HomePage)
        }
    }
}
