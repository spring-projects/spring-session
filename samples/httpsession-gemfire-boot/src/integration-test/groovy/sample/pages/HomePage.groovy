/*
 * Copyright 2002-2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package sample.pages

import geb.*

/**
 * The home page
 *
 * @author Rob Winch
 * @author John Blum
 */
class HomePage extends Page {
	static url = ''
	static at = { assert driver.title == 'Session Attributes'; true }
	static content = {
		form { $('form') }
		submit { $('input[type=submit]') }
		createAttribute(required:false) { name, value ->
			form.attributeName = name
			form.attributeValue = value
			submit.click(HomePage)
		}
		attributes { moduleList AttributeRow, $("table tr").tail() }
		// attributes { $("table tr").tail().moduleList(AttributeRow) }
	}
}
class AttributeRow extends Module {
	static content = {
		cell { $("td", it) }
		name { cell(0).text() }
		value { cell(1).text() }
	}
}
