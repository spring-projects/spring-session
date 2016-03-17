package samples.pages

import geb.Page

/**
 * Created by jitendra on 15/3/16.
 */
class HomePage extends Page {
    static url = "/"

    static at = {
        driver.title == "Spring Session Sample - Home"
    }

    static content = {
        form { $('form') }
        submit { $('button[type=submit]') }
        setAttribute(required: false) { key = 'project', value = 'SessionRedisJson' ->
            form.key = key
            form.value = value
            submit.click(SetAttributePage)
        }
    }
}
