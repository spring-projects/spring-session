package sample.pages

import geb.*

/**
 * The Links Page
 *
 * @author Rob Winch
 */
class LoginPage extends Page {
    static url = '/login'
    static at = { assert driver.title == 'Login Page'; true}
    static content = {
        form { $('form') }
        submit { $('input[type=submit]') }
        login(required:false) { user='user', pass='password' ->
            form.username = user
            form.password = pass
            submit.click(HomePage)
        }
    }
}
