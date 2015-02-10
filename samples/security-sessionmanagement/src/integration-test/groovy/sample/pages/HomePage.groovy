package sample.pages

import geb.Page

/**
 * The home page
 *
 * @author Rob Winch
 */
class HomePage extends Page {
    static url = ''
    static at = { assert driver.title == 'Secured Content'; true}
    static content = {
        username { $('#un').text() }
        logout(to:LoginPage) { $('input[type=submit]').click() }
    }
}
