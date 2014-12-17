package sample.pages

import geb.*

/**
 * The home page
 *
 * @author Rob Winch
 */
class HomePage extends Page {
    static url = ''
    static at = { assert driver.title == 'Session Attributes'; true}
    static content = {
        form { $('form') }
        submit { $('input[type=submit]') }
        createAttribute(required:false) { name, value ->
            form.attributeName = name
            form.attributeValue = value
            submit.click(HomePage)
        }
        attributes { moduleList AttributeRow, $("table tr").tail() }
    }
}
class AttributeRow extends Module {
    static content = {
        cell { $("td", it) }
        name { cell(0).text() }
        value { cell(1).text() }
    }
}
