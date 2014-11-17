package sample.pages

import geb.*

/**
 * The home page
 *
 * @author Rob Winch
 */
class HomePage extends Page {
    static url = ''
    static at = { assert driver.title == 'Demonstrates Multi User Log In'; true}
    static content = {
        navLink { $('#navLink') }
        error { $('#error').text() }
        form { $('form') }
        username(required:false) { $('#un').text() }
        logout(required:false) { $('#logout') }
        addAccount(required:false) { $('#addAccount') }
        submit { $('input[type=submit]') }
        login(required:false) { user, pass ->
            form.username = user
            form.password = pass
            submit.click(HomePage)
        }
        switchAccount{ un ->
            $("#switchAccount${un}").click(HomePage)
        }
        attributes { moduleList AttributeRow, $("table tr").tail() }
    }
}
