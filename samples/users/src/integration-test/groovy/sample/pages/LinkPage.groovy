/*
 * Copyright 2002-2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
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
		userMenu() {
			if(!$('#user-menu').displayed) {
				$('#toggle').jquery.click()
			}
			waitFor {
				$('#user-menu').displayed
			}
		}
		switchAccount{ un ->
			userMenu()
			$("#switchAccount${un}").click(HomePage)
		}
	}
}
