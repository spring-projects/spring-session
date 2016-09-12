package samples.pages

import geb.Page

/**
 * @author jitendra on 15/3/16.
 */
class LoginPage extends Page {
	static url="/login"

	static at=
	{
        assert title == "Spring Session Sample - Login"
        return true
    }

	static content=
	{
        form { $('form') }
        submit { $('button[type=submit]') }
        login(required: false) { user = 'user', pass = 'password' ->
            form.username = user
            form.password = pass
            submit.click(HomePage)
        }
    }
}
